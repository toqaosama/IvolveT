# Lab 16: Pod Resource Management with CPU and Memory Requests and Limits
 
## Objective
 
1. Update the existing `ivolveapp` Deployment to include resource **requests** and **limits**.
2. Verify the applied requests and limits with `kubectl describe pod`.
3. Monitor real-time resource usage with `kubectl top pod` (this requires the **Metrics Server** to be running in the cluster).
## Concepts
 
| Concept | Purpose |
|---------|---------|
| **Request** | The amount of CPU/memory the scheduler reserves for the container. A pod is only scheduled on a node that has enough free capacity for its requests. |
| **Limit** | The maximum the container may use. CPU above the limit is throttled; memory above the limit gets the container OOM-killed. |
| **Metrics Server** | Collects CPU/memory usage from the kubelets and serves it through the Metrics API, which `kubectl top` reads. |
 
## Task Details
 
| Resource | Request | Limit |
|----------|---------|-------|
| CPU | `1` (1 vCPU) | `2` (2 vCPUs) |
| Memory | `1Gi` | `2Gi` |
 
## Environment
 
| Item | Value |
|------|-------|
| Control plane node | `ip-172-31-16-131` (taint `node-role.kubernetes.io/control-plane:NoSchedule`) |
| Worker node | `ip-172-31-24-135` (label `project=ivolve`, taint `workload=ivolveApp:NoSchedule`) |
| Metrics Server | Helm chart `metrics-server-3.14.0` (app version `0.9.0`) in `kube-system` |
| Helm | `4.3.2` |
 
---
 
## Part 1: Install the Metrics Server
 
`kubectl top` needs the Metrics API, so the Metrics Server is installed first with Helm.
 
### 1.1 Install Helm
 
```bash
sudo snap install helm --classic
```
 
```text
helm 4.3.2 from Snapcrafters✪ installed
```
 
### 1.2 Add the Metrics Server repository
 
```bash
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/
helm repo update
```
 
```text
"metrics-server" has been added to your repositories
Hang tight while we grab the latest from your chart repositories...
...Successfully got an update from the "metrics-server" chart repository
Update Complete. ⎈Happy Helming!⎈
```
 
### 1.3 Install the chart
 
`--kubelet-insecure-tls` is needed on a kubeadm cluster because the kubelets use self-signed certificates.
 
```bash
helm install metrics-server metrics-server/metrics-server -n kube-system --set "args={--kubelet-insecure-tls,--kubelet-preferred-address-types=InternalIP\,ExternalIP\,Hostname}"
```
 
```text
NAME: metrics-server
LAST DEPLOYED: Sun Nov 15 23:19:06 2026
NAMESPACE: kube-system
STATUS: deployed
REVISION: 1
DESCRIPTION: Install complete
TEST SUITE: None
NOTES:
***********************************************************************
* Metrics Server                                                      *
***********************************************************************
  Chart version: 3.14.0
  App version:   0.9.0
  Image tag:     registry.k8s.io/metrics-server/metrics-server:v0.9.0
***********************************************************************
```
 
### 1.4 Problem: the Metrics Server pod stays `Pending`
 
```bash
kubectl rollout status deployment/metrics-server -n kube-system
```
 
```text
error: deployment "metrics-server" exceeded its progress deadline
```
 
```bash
kubectl get pods -n kube-system | grep metrics
```
 
```text
metrics-server-5bdd765c66-gt77g            0/1     Pending   0              31m
```
 
```bash
kubectl describe pod metrics-server-5bdd765c66-gt77g -n kube-system
```
 
The relevant part of the output is the event at the bottom:
 
```text
Node-Selectors:              <none>
Tolerations:                 node.kubernetes.io/not-ready:NoExecute op=Exists for 300s
                             node.kubernetes.io/unreachable:NoExecute op=Exists for 300s
Events:
  Type     Reason            Age                  From               Message
  ----     ------            ----                 ----               -------
  Warning  FailedScheduling  2m21s (x7 over 32m)  default-scheduler  0/2 nodes are available: 2 node(s) had untolerated taint(s). preemption: 0/2 nodes are available: 2 Preemption is not helpful for scheduling.
```
 
**Cause:** both nodes are tainted and the Metrics Server pod has no matching tolerations.
 
```bash
kubectl describe nodes | grep -i Taints
```
 
```text
Taints:             node-role.kubernetes.io/control-plane:NoSchedule
Taints:             workload=ivolveApp:NoSchedule
```
 
### 1.5 Fix: add tolerations to the Metrics Server
 
```bash
helm upgrade metrics-server metrics-server/metrics-server \
  -n kube-system \
  --reuse-values \
  --set-json 'tolerations=[{"key":"node-role.kubernetes.io/control-plane","operator":"Exists","effect":"NoSchedule"},{"key":"workload","operator":"Equal","value":"ivolveApp","effect":"NoSchedule"}]'
```
 
```text
Release "metrics-server" has been upgraded. Happy Helming!
NAME: metrics-server
LAST DEPLOYED: Sun Nov 15 23:53:32 2026
NAMESPACE: kube-system
STATUS: deployed
REVISION: 2
DESCRIPTION: Upgrade complete
TEST SUITE: None
```
 
Verify the release and the pod:
 
```bash
helm list -A
```
 
```text
NAME            NAMESPACE       REVISION        UPDATED                                 STATUS          CHART                   APP VERSION
metrics-server  kube-system     1               2026-11-15 23:19:06.481757624 +0000 UTC deployed        metrics-server-3.14.0   0.9.0
```
 
(This listing was taken before the upgrade, so it still shows revision 1. After the upgrade the revision is 2.)
 
```bash
kubectl get pods -n kube-system
```
 
```text
NAME                                       READY   STATUS    RESTARTS       AGE
coredns-559f6c778d-cfshg                   1/1     Running   4 (3h3m ago)   8d
coredns-559f6c778d-p6jzf                   1/1     Running   4 (3h3m ago)   8d
etcd-ip-172-31-16-131                      1/1     Running   4 (3h3m ago)   8d
kube-apiserver-ip-172-31-16-131            1/1     Running   4 (3h3m ago)   8d
kube-controller-manager-ip-172-31-16-131   1/1     Running   4 (3h3m ago)   8d
kube-proxy-5rkdl                           1/1     Running   2 (3h3m ago)   3d22h
kube-proxy-bcbhh                           1/1     Running   4 (3h3m ago)   8d
kube-scheduler-ip-172-31-16-131            1/1     Running   4 (3h3m ago)   8d
metrics-server-745c9d4966-9gf74            1/1     Running   0              13m
```
 
The new Metrics Server pod `metrics-server-745c9d4966-9gf74` is `1/1 Running`, and the old `Pending` pod was terminated.
 
---
 
## Part 2: Add resource requests and limits to the Deployment
 
Edit `deployment.yaml` (the Deployment from Lab 15) and add a `resources` block to the `app` container:
 
```bash
nano deployment.yaml
```
 
```yaml
      containers:
        - name: app
          image: marceleno17/lab8-app:latest
          ports:
            - containerPort: 3000
              name: http
          resources:
            requests:
              cpu: "1"
              memory: "1Gi"
            limits:
              cpu: "2"
              memory: "2Gi"
```
 
The rest of the manifest is unchanged from Lab 15.
 
Apply it:
 
```bash
kubectl apply -f deployment.yaml
```
 
```text
deployment.apps/ivolveapp configured
```
 
---
 
## Verification
 
### 1. Monitor real-time usage with `kubectl top pod`
 
```bash
kubectl top pod -n ivolve
```
 
```text
NAME                         CPU(cores)   MEMORY(bytes)
ivolveapp-68f6fc9b76-qpqmc   1m           21Mi
ivolvedb-0                   5m           478Mi
network                      0m           14Mi
```
 
The Metrics API works, so `kubectl top` returns real-time CPU and memory usage for the pods in the `ivolve` namespace.
 
### 2. Verify the requests and limits with `kubectl describe pod`
 
```bash
kubectl describe pod $(kubectl get pods -n ivolve -l app=ivolveapp -o jsonpath="{.items[0].metadata.name}") -n ivolve | grep -A 8 "Limits:"
```
 
Expected output once the new pods are running:
 
```text
    Limits:
      cpu:     2
      memory:  2Gi
    Requests:
      cpu:      1
      memory:   1Gi
```
 
> **Status:** this check is **not completed yet**. In the run above the command returned nothing, and the only application pod
> (`ivolveapp-68f6fc9b76-qpqmc`) is the **old** one created before the update, so it has no resources block.
> The rollout of the new pods did not finish (see Troubleshooting).
 
---
 
## Troubleshooting: the updated Deployment does not roll out
 
```bash
kubectl rollout status deployment/ivolveapp -n ivolve
```
 
```text
Waiting for deployment "ivolveapp" rollout to finish: 0 out of 2 new replicas have been updated...
error: deployment "ivolveapp" exceeded its progress deadline
```
 
The new ReplicaSet could not create or schedule pods that carry the new resources block. There are two likely causes. Check both:
 
**1. The pod quota from Lab 11 (`ivolvequota`, limit of 3 pods).**
The namespace already holds 3 pods (`ivolvedb-0`, `network`, and the old `ivolveapp` pod), so a rolling update cannot create an extra pod.
 
```bash
kubectl get rs -n ivolve
kubectl describe rs -n ivolve | grep -i -A3 quota
kubectl describe resourcequota ivolvequota -n ivolve
```
 
Look for `exceeded quota: ivolvequota` in the events. Fix by deleting the diagnostic pod (`kubectl delete pod network -n ivolve`) or by raising the quota (for example `pods: "5"` in `quota.yaml`, then `kubectl apply -f quota.yaml`).
 
**2. Not enough CPU or memory on the worker node.**
Each replica now **requests 1 CPU and 1Gi of memory**, so 2 replicas request 2 CPUs and 2Gi in total, on top of what the other pods on the node already request. The pods are scheduled only on the node labeled `project=ivolve`.
 
```bash
kubectl get pods -n ivolve
kubectl describe pod <pending-pod-name> -n ivolve
kubectl describe node ip-172-31-24-135 | grep -A 8 -E "Allocatable|Allocated resources"
```
 
If the pod is `Pending` with `Insufficient cpu` or `Insufficient memory` in the events, the node is too small. Use a larger worker instance, or reduce the replica count or the requests for this lab.
 
After fixing, confirm:
 
```bash
kubectl rollout status deployment/ivolveapp -n ivolve
kubectl get pods -l app=ivolveapp -n ivolve -o wide
kubectl top pod -n ivolve
```
 
---
 
## Result
 
| Check | Status |
|-------|--------|
| Metrics Server installed with Helm | Done |
| Metrics Server scheduling fixed with tolerations for both node taints | Done |
| Resource requests and limits added to `deployment.yaml` and applied | Done |
| `kubectl top pod` returns real-time usage | Done |
| New pods running with the requests and limits | Pending (rollout blocked, see Troubleshooting) |
| `kubectl describe pod` shows requests and limits | Pending (depends on the previous row) |
 
