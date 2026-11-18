# Lab 17: Control Pod-to-Pod Traffic via Network Policy
 
## Objective
 
Restrict access to the MySQL pod with a **NetworkPolicy** so that only the application pods can reach it, and only on the MySQL port. Then prove it with an allowed test and a blocked test.
 
## Concepts
 
| Concept | Purpose |
|---------|---------|
| **NetworkPolicy** | Controls which pods may send traffic to (ingress) or receive traffic from (egress) selected pods. |
| **Default behavior** | Without any policy, all pods can talk to each other. Once a policy selects a pod for `Ingress`, only the traffic the policy allows is accepted by that pod. |
| **Enforcement** | A NetworkPolicy only works if the cluster CNI plugin supports and enforces it. |
 
## Task Details
 
| Setting | Value |
|---------|-------|
| Name | `ivolvenp` |
| Namespace | `ivolve` |
| Pod selector | `app=ivolvedb` (the MySQL StatefulSet pod from Lab 14) |
| Policy types | `Ingress` only |
| Allowed source | pods with label `app=ivolveapp` (the application pods from Lab 15) |
| Allowed port | `3306/TCP` |
 
> Kubernetes object names must be lowercase, so `ivolveNP` from the lab sheet is created as `ivolvenp`.
 
---
 
## Steps
 
### Step 1: Create the NetworkPolicy manifest
 
```bash
nano networkpolicy.yaml
```
 
`networkpolicy.yaml`:
 
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: ivolvenp
  namespace: ivolve
spec:
  podSelector:
    matchLabels:
      app: ivolvedb
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: ivolveapp
      ports:
        - protocol: TCP
          port: 3306
```
 
What the policy does:
 
- `podSelector: app=ivolvedb` selects the MySQL pod, which is the pod being protected.
- `policyTypes: Ingress` means only incoming traffic to the MySQL pod is controlled. Egress is not affected.
- The ingress rule accepts connections only from pods labeled `app=ivolveapp`, and only on port `3306`.
### Step 2: Apply the policy
 
```bash
kubectl apply -f networkpolicy.yaml
```
 
```text
networkpolicy.networking.k8s.io/ivolvenp created
```
 
---
 
## Verification
 
### 1. Verify the policy exists
 
```bash
kubectl get networkpolicy -n ivolve
```
 
```text
NAME       POD-SELECTOR   AGE
ivolvenp   app=ivolvedb   7s
```
 
### 2. Inspect the policy
 
```bash
kubectl describe networkpolicy ivolvenp -n ivolve
```
 
```text
Name:         ivolvenp
Namespace:    ivolve
Created on:   2026-11-16 00:08:38 +0000 UTC
Labels:       <none>
Annotations:  <none>
Spec:
  PodSelector:     app=ivolvedb
  Allowing ingress traffic:
    To Port: 3306/TCP
    From:
      PodSelector: app=ivolveapp
  Not affecting egress traffic
  Policy Types: Ingress
```
 
The policy selects `app=ivolvedb`, allows ingress only from `app=ivolveapp` on `3306/TCP`, and does not affect egress.
 
### 3. Allowed traffic: application pod to MySQL
 
Test the connection from an application pod (label `app=ivolveapp`) to the MySQL Service on port 3306:
 
```bash
kubectl exec -it -n ivolve $(kubectl get pods -n ivolve -l app=ivolveapp -o jsonpath="{.items[0].metadata.name}") -c app -- nc -zv -w 3 ivolvedbsvc 3306
```
 
```text
ivolvedbsvc (192.168.74.6:3306) open
```
 
The connection is **open**, so the application pod is allowed to reach MySQL.
 
### 4. Blocked traffic: any other pod to MySQL
 
Test the same connection from the `network` diagnostic pod, which does **not** have the label `app=ivolveapp`:
 
```bash
kubectl exec -it -n ivolve network -- nc -zv -w 3 ivolvedbsvc 3306
```
 
```text
nc: connect to ivolvedbsvc (192.168.74.6) port 3306 (tcp) timed out: Operation in progress
command terminated with exit code 1
```
 
The connection **times out**, so the policy blocks traffic to MySQL from pods that are not application pods.
 
---
 
## Result
 
| Check | Status |
|-------|--------|
| NetworkPolicy `ivolvenp` created in `ivolve` | Done |
| Targets the MySQL pod (`app=ivolvedb`), `Ingress` only | Done |
| Allows only `app=ivolveapp` pods on port `3306` | Done |
| Application pod can connect to MySQL (allowed) | Done |
| Other pod (`network`) cannot connect to MySQL (blocked) | Done |
 
## Notes
 
- The timeout in the blocked test also shows that the cluster CNI plugin enforces NetworkPolicies. On a CNI without NetworkPolicy support, both tests would succeed.
- Because the policy applies to the MySQL pod, tools that connect to MySQL from other pods (for example a debug pod) are now blocked as well. Add a matching label or another `from` rule if you need them.
