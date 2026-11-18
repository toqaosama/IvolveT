# Lab 10: Node Isolation Using Taints and Labels in Kubernetes
 
## Objective
 
Dedicate a worker node to a specific workload by:
 
1. Applying a **taint** so that no pod is scheduled on the node unless it has a matching **toleration**.
2. Applying a **label** so that pods can be targeted to this node using a `nodeSelector`.
3. Verifying that both the taint and the label were applied correctly.
## Concepts
 
| Concept | Purpose |
|---------|---------|
| **Taint** | Repels pods from a node. Only pods with a matching toleration can be scheduled on it. |
| **Toleration** | Set on a pod to allow it to be scheduled on a node with a matching taint. |
| **Label** | Key/value tag attached to a node, used by `nodeSelector` / affinity to attract pods. |
 
> A taint alone only **repels** pods. To make sure pods land on this specific node, you need the
> **taint + toleration** (permission) together with the **label + nodeSelector** (targeting).
 
## Environment
 
| Item | Value |
|------|-------|
| Control plane node | `ip-172-31-16-131` |
| Worker node used in this lab | `ip-172-31-24-135` |
| Kubernetes version | `v1.31.14` |
 
## Task Details
 
| Setting | Value |
|---------|-------|
| Taint key | `workload` |
| Taint value | `ivolveApp` |
| Taint effect | `NoSchedule` |
| Node label | `project=ivolve` |
 
---
 
## Steps
 
### Step 1: Apply the taint to the worker node
 
```bash
kubectl taint nodes ip-172-31-24-135 workload=ivolveApp:NoSchedule
```
 
Output:
 
```text
node/ip-172-31-24-135 tainted
```
 
### Step 2: Label the same worker node
 
```bash
kubectl label nodes ip-172-31-24-135 project=ivolve
```
 
Output:
 
```text
node/ip-172-31-24-135 labeled
```
 
---
 
## Verification
 
### 1. Verify the taint using `kubectl describe node`
 
```bash
kubectl describe node ip-172-31-24-135 | grep -i taints
```
 
Output:
 
```text
Taints:             workload=ivolveApp:NoSchedule
```
 
### 2. Verify the label using `kubectl get nodes --show-labels`
 
```bash
kubectl get nodes --show-labels | grep ip-172-31-24-135
```
 
Output:
 
```text
ip-172-31-24-135   Ready    <none>          23h    v1.31.14   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/os=linux,kubernetes.io/arch=amd64,kubernetes.io/hostname=ip-172-31-24-135,kubernetes.io/os=linux,project=ivolve
```
 
The label `project=ivolve` appears at the end of the labels list.
 
### 3. Select the node by label (extra check)
 
```bash
kubectl get nodes -l project=ivolve
```
 
Output:
 
```text
NAME               STATUS   ROLES    AGE   VERSION
ip-172-31-24-135   Ready    <none>   23h   v1.31.14
```
 
Only the tainted worker node is returned, which confirms the label is applied to the correct node.
 
---
 
## Result
 
| Check | Status |
|-------|--------|
| Taint `workload=ivolveApp:NoSchedule` applied | Done |
| Label `project=ivolve` applied | Done |
| Taint verified with `kubectl describe node` | Done |
| Label verified with `kubectl get nodes --show-labels` | Done |
 
## How this is used in later labs
 
Pods that should run on this node (the StatefulSet and Deployment labs) must include both of the following in their pod spec:
 
```yaml
spec:
  nodeSelector:
    project: ivolve
  tolerations:
    - key: "workload"
      operator: "Equal"
      value: "ivolveApp"
      effect: "NoSchedule"
```
 
## Useful commands
 
Remove the taint (note the trailing `-`):
 
```bash
kubectl taint nodes ip-172-31-24-135 workload=ivolveApp:NoSchedule-
```
 
Remove the label:
 
```bash
kubectl label nodes ip-172-31-24-135 project-
```
