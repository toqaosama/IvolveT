# Lab 9 --- Deploy a Kubernetes Cluster Using kubeadm

This lab documents the setup of a Kubernetes cluster with **one
control-plane node and one worker node** on AWS EC2. The cluster is
built with `kubeadm`, `containerd`, and Calico.

> **Accuracy note:** This README separates verified results from steps
> that still need to be completed. At the last verified checkpoint, the
> control-plane node was `Ready`, but the worker had **not yet been
> successfully joined**. Do not report a two-node cluster as complete
> until the final verification section shows both nodes.

## 1. Lab architecture

  --------------------------------------------------------------------------
  Role              EC2 public IP     Private IP        Hostname seen in the
                                                        cluster
  ----------------- ----------------- ----------------- --------------------
  Control plane     `3.85.12.27`      `172.31.21.85`    `ip-172-31-21-85`
  (Node A)                                              

  Worker (Node B)   `3.87.123.145`    `172.31.17.163`   `ip-172-31-17-163`
  --------------------------------------------------------------------------

-   **Operating system actually used:** Ubuntu 26.04 / 26.04.1 LTS
-   **Kubernetes:** `v1.37.1`
-   **containerd actually installed:** `2.2.2`
-   **Calico manifest applied:** `v3.33.0`
-   **Pod network CIDR passed to kubeadm:** `192.168.0.0/16`
-   **SSH key path in WSL:** `~/.ssh/key1.pem`
-   **Local project folder in WSL:** `/mnt/f/nti/ivolve/Ivolve/lap9`

### Important differences from the original lab specification

The original lab target mentioned Ubuntu Server 24.04.5 and containerd
2.2.1. The EC2 instances used for this run reported Ubuntu 26.04/26.04.1
and installed containerd 2.2.2 from the configured Ubuntu package
repository. Record these actual versions in the submission rather than
claiming the original versions were used. For a strict assessment,
confirm the instructor accepts these OS/runtime versions.

## 2. Prerequisites

-   Two reachable Ubuntu EC2 instances in the same VPC.
-   SSH access to each instance from the administrator's machine.
-   `kubelet`, `kubeadm`, and `kubectl` installed at the same Kubernetes
    minor/patch version on both nodes.
-   `containerd` running on both nodes, configured to use the systemd
    cgroup driver.
-   Required traffic permitted between the nodes in the EC2 security
    group.

### Security group guidance

Keep cluster-internal ports limited to the cluster nodes or their shared
security group; do **not** expose internal Kubernetes ports to
`0.0.0.0/0`.

At minimum, review: - TCP `22`: SSH, restricted to your current public
IP. - TCP `6443`: Kubernetes API server, reachable from the
worker/control-plane administration path. - TCP `10250`: kubelet API,
restricted to cluster nodes. - TCP `2379-2380`: etcd peer/client
traffic, restricted to the control plane / cluster nodes as
appropriate. - Calico networking: allow the traffic required by the
selected Calico mode. If using Calico BGP mode, TCP `179` may be needed
between nodes. Follow the official Calico documentation and your
manifest/configuration.

The exact rules depend on the chosen CNI mode and VPC design. Verify
connectivity before running `kubeadm join`.

## 3. Connect to the EC2 instances from WSL

Make sure the private key has restrictive permissions:

``` bash
chmod 600 ~/.ssh/key1.pem
```

Connect to the control plane:

``` bash
ssh -i ~/.ssh/key1.pem ubuntu@3.85.12.27
```

Connect to the worker from a separate WSL terminal (or after `exit`):

``` bash
ssh -i ~/.ssh/key1.pem ubuntu@3.87.123.145
```

Check the node's private IP and operating system:

``` bash
hostname
hostname -I
cat /etc/os-release
```

> The public IPs may change if an instance is stopped and restarted
> unless Elastic IPs are assigned. Check the EC2 console before
> connecting if SSH stops working.

## 4. Prepare both nodes

Run the following preparation steps on **both Node A and Node B**.

### 4.1 Disable swap

``` bash
sudo swapoff -a
swapon --show
```

`swapon --show` should return no active swap devices for this lab. If
swap is configured persistently in `/etc/fstab`, review and disable the
relevant swap entry so it does not return after reboot.

### 4.2 Load required kernel modules

``` bash
cat <<'EOF' | sudo tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

sudo modprobe overlay
sudo modprobe br_netfilter
lsmod | grep -E 'overlay|br_netfilter'
```

### 4.3 Apply networking sysctl settings

``` bash
cat <<'EOF' | sudo tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward = 1
EOF

sudo sysctl --system
sysctl net.bridge.bridge-nf-call-iptables
sysctl net.bridge.bridge-nf-call-ip6tables
sysctl net.ipv4.ip_forward
```

The three queried values should be `1`.

## 5. Install Kubernetes packages on both nodes

The Kubernetes v1.37 apt repository was configured on both instances.

``` bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gpg
sudo mkdir -p /etc/apt/keyrings
```

If the repository has not yet been configured on a node, add the
official v1.37 repository:

``` bash
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.37/deb/Release.key \
  | sudo gpg --dearmor --yes -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.37/deb/ /' \
  | sudo tee /etc/apt/sources.list.d/kubernetes.list

sudo apt-get update
sudo apt-get install -y kubelet kubeadm kubectl
sudo apt-mark hold kubelet kubeadm kubectl
```

Verify on both nodes:

``` bash
kubelet --version
kubeadm version
kubectl version --client
```

**Observed version:** Kubernetes `v1.37.1` on both nodes.

## 6. Install and configure containerd on both nodes

The Ubuntu package repository provided `containerd` version `2.2.2` for
this run.

``` bash
sudo apt-get update
sudo apt-get install -y containerd
```

Generate the default configuration, enable the systemd cgroup driver,
and start the service:

``` bash
sudo mkdir -p /etc/containerd
sudo containerd config default | sudo tee /etc/containerd/config.toml > /dev/null
sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

sudo systemctl enable --now containerd
sudo systemctl restart containerd
```

Verify:

``` bash
containerd --version
sudo systemctl is-active containerd
grep -n 'SystemdCgroup' /etc/containerd/config.toml
sudo ctr plugins ls | grep cri
```

Expected checks: - `systemctl is-active containerd` returns `active`. -
The config contains `SystemdCgroup = true`. - The containerd CRI plugins
should show `ok`.

> **Compatibility note:** This run used containerd 2.2.2 on Ubuntu
> 26.04, not the exact runtime version in the original lab
> specification. If kubeadm or kubelet reports runtime/CRI compatibility
> issues, check the official Kubernetes/containerd compatibility
> guidance and use a supported runtime version rather than masking the
> error.

## 7. Initialize the control plane (Node A only)

SSH to Node A:

``` bash
ssh -i ~/.ssh/key1.pem ubuntu@3.85.12.27
```

Enable kubelet (it may restart/retry until kubeadm initialization is
complete):

``` bash
sudo systemctl enable --now kubelet
```

Initialize the cluster using Node A's **private** IP:

``` bash
sudo kubeadm init \
  --apiserver-advertise-address=172.31.21.85 \
  --pod-network-cidr=192.168.0.0/16
```

Wait for the command to finish successfully. If initialization fails,
diagnose the error before retrying; do not repeatedly run `kubeadm init`
on an already initialized control plane.

### Configure kubectl on Node A

Run these commands as the same non-root user that will use `kubectl`:

``` bash
mkdir -p "$HOME/.kube"
sudo cp -i /etc/kubernetes/admin.conf "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
```

Verify the control plane:

``` bash
kubectl get nodes -o wide
kubectl get pods -A
```

Before installing a CNI, the control-plane node can show `NotReady` and
CoreDNS pods can show `Pending`. This is expected until the pod network
is configured.

## 8. Install Calico networking (Node A)

Apply the Calico v3.33.0 manifest:

``` bash
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.33.0/manifests/calico.yaml
```

Check progress:

``` bash
kubectl get pods -n kube-system
kubectl get pods -A
kubectl get nodes -o wide
```

Allow time for the Calico images and pods to initialize. The target
state is for Calico pods and CoreDNS to become healthy and for the
control-plane node to become `Ready`.

If a pod remains `Pending`, `Init`, or `CrashLoopBackOff`, inspect it
before proceeding:

``` bash
kubectl describe pod -n kube-system <pod-name>
kubectl logs -n kube-system <pod-name> --all-containers=true
```

Review the `Events` section in `describe` output. Do not proceed to
worker validation until the control plane is healthy.

### Verified result at the last checkpoint

The following command on Node A returned the control-plane node as
`Ready`:

``` text
NAME              STATUS   ROLES           AGE   VERSION   INTERNAL-IP    EXTERNAL-IP   OS-IMAGE
ip-172-31-21-85   Ready    control-plane   10m   v1.37.1   172.31.21.85   <none>        Ubuntu 26.04.1 LTS
```

The runtime shown in the full output was `containerd://2.2.2`.

## 9. Join the worker (Node B) --- still to be completed/verified

**Do not run `kubectl get nodes` on the worker as a cluster check**
unless you deliberately configure a kubeconfig there. The worker does
not automatically have the administrator kubeconfig; `kubectl` may try
`localhost:8080` and fail. Manage the cluster from Node A.

### 9.1 Generate a fresh join command on Node A

On the control plane:

``` bash
sudo kubeadm token create --print-join-command
```

Copy the complete command that is printed. It should resemble:

``` bash
kubeadm join 172.31.21.85:6443 --token <fresh-token> --discovery-token-ca-cert-hash sha256:<full-hash>
```

The values above are placeholders. **Use the actual generated values**,
not the angle-bracket text and not literal `...`. Since a token was
exposed in terminal output/chat during troubleshooting, generate a fresh
one and do not reuse the exposed token.

### 9.2 Run the actual command on Node B

From WSL, connect to the worker:

``` bash
ssh -i ~/.ssh/key1.pem ubuntu@3.87.123.145
```

Paste the full generated command, prefixed with `sudo`, for example:

``` bash
sudo kubeadm join 172.31.21.85:6443 --token <fresh-token> --discovery-token-ca-cert-hash sha256:<full-hash>
```

Again, replace the placeholders with the real values from Node A. Do not
run this command on Node A.

A successful run prints a message similar to:

``` text
This node has joined the cluster
```

If it times out connecting to `172.31.21.85:6443`, verify the control
plane is running and the EC2 security group permits TCP 6443 from the
worker to the control plane. Also check private-network connectivity.

### 9.3 Verify both nodes on Node A

Return to Node A and run:

``` bash
kubectl get nodes -o wide
kubectl get pods -A
```

The final target is two nodes, both eventually `Ready`:

``` text
NAME              STATUS   ROLES           VERSION   INTERNAL-IP
ip-172-31-21-85   Ready    control-plane   v1.37.1   172.31.21.85
ip-172-31-17-163  Ready    <none>          v1.37.1   172.31.17.163
```

The exact table formatting, age, kernel version, and pod names can
differ. If the worker is initially `NotReady`, wait briefly and inspect
the Calico node pod on the worker:

``` bash
kubectl get pods -n kube-system -o wide
kubectl describe node ip-172-31-17-163
```

Do not claim success until both nodes show `Ready`.

## 10. Troubleshooting notes

### `kubectl` says `localhost:8080` connection refused on Node B

This generally means `kubectl` on the worker has no kubeconfig pointing
to the cluster. Run cluster-level `kubectl` commands on Node A, or
configure a deliberately scoped kubeconfig if required. This error alone
does not prove whether the worker joined.

### `bootstrap token is invalid`

Do not paste the example token or literal `...`. Generate a fresh full
join command on Node A:

``` bash
sudo kubeadm token create --print-join-command
```

Run the complete printed command on Node B.

### Control plane is `NotReady`

Check CNI pods and CoreDNS:

``` bash
kubectl get pods -A
kubectl get pods -n kube-system -o wide
kubectl describe pod -n kube-system <pod-name>
```

### Worker cannot reach the API server

From Node B, test the private API server endpoint:

``` bash
nc -vz 172.31.21.85 6443
```

If `nc` is unavailable, install `netcat-openbsd` or use the approved
connectivity checks for your environment. Review the security group and
network ACLs; do not open all ports to the public internet.

## 11. Final validation checklist

-   [x] Swap disabled on both nodes.
-   [x] `overlay` and `br_netfilter` loaded on both nodes.
-   [x] Kubernetes sysctl settings applied on both nodes.
-   [x] Kubernetes `v1.37.1` installed on both nodes.
-   [x] containerd installed, active, and configured with
    `SystemdCgroup = true` on both nodes.
-   [x] Control plane initialized on Node A.
-   [x] Calico v3.33.0 manifest applied.
-   [x] Control-plane node verified as `Ready`.
-   [ ] Worker successfully joined using a fresh token.
-   [ ] Both nodes verified as `Ready` from Node A.
-   [ ] All cluster system pods verified healthy.

## 12. References

-   Kubernetes kubeadm:
    https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/
-   Kubernetes container runtimes:
    https://kubernetes.io/docs/setup/production-environment/container-runtimes/
-   Kubernetes apt repository: https://pkgs.k8s.io/
-   Calico quickstart:
    https://docs.tigera.io/calico/latest/getting-started/kubernetes/quickstart
-   Calico v3.33.0 manifest:
    https://raw.githubusercontent.com/projectcalico/calico/v3.33.0/manifests/calico.yaml

## 13. Add this README to the Git repository

From WSL, go to the lab folder:

``` bash
cd /mnt/f/nti/ivolve/Ivolve/lap9
```

Save this file as `README.md` in that folder. Then review and push it:

``` bash
git status
git add README.md
git commit -m "docs: add Kubernetes kubeadm lab README"
git push
```

If `git push` reports that the current branch has no upstream, check the
current branch and remote before pushing:

``` bash
git branch --show-current
git remote -v
```

Do not force-push. If this folder is not already a Git repository, move
the README into the correct repository folder first.

### Before pushing

Never commit: - `~/.ssh/key1.pem` or any other private key -
`/etc/kubernetes/admin.conf` or any kubeconfig containing admin
credentials - live kubeadm join tokens - AWS credentials, access keys,
or secret values

This README intentionally does not contain the live join token or
private key.
