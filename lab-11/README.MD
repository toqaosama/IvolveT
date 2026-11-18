# Lab 11: Namespace Management and Resource Quota Enforcement
 
## Objective
 
1. Create a Kubernetes namespace named `ivolve` to isolate application resources.
2. Create a `ResourceQuota` in the `ivolve` namespace that limits the total number of pods to **3**.
3. Verify that the quota is applied and that Kubernetes blocks the creation of additional pods once the limit is reached.
## Concepts
 
| Concept | Purpose |
|---------|---------|
| **Namespace** | Logical isolation of resources inside a cluster. |
| **ResourceQuota** | Limits the total resource consumption (or object count) inside a namespace. |
 
> Kubernetes object names must be lowercase, so `ivolveQuota` from the lab sheet is created as **`ivolvequota`**.
 
## Task Details
 
| Setting | Value |
|---------|-------|
| Namespace | `ivolve` |
| ResourceQuota name | `ivolvequota` |
| Limit | `pods: 3` |
 
---
 
## Steps
 
### Step 1: Create the namespace
 
```bash
kubectl create namespace ivolve
```
 
### Step 2: Create the ResourceQuota manifest
 
```bash
nano quota.yaml
```
 
`quota.yaml`:
 
```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: ivolvequota
  namespace: ivolve
spec:
  hard:
    pods: "3"
```
 
### Step 3: Apply the manifest
 
```bash
kubectl apply -f quota.yaml
```
 
Output:
 
```text
resourcequota/ivolvequota created
```
 
> Running `kubectl create quota ivolvequota --hard=pods=3 -n ivolve` afterwards returns
> `error: failed to create quota: resourcequotas "ivolvequota" already exists`, which confirms the quota
> already exists and the name is taken.
 
---
 
## Verification
 
### 1. Verify the quota is applied
 
```bash
kubectl describe resourcequota ivolvequota -n ivolve
```
 
Output:
 
```text
Name:       ivolvequota
Namespace:  ivolve
Resource    Used  Hard
--------    ----  ----
pods        0     3
```
 
### 2. Create pods up to the limit
 
Create a test deployment with 3 replicas (equal to the quota):
 
```bash
kubectl create deployment nginx-test --image=nginx --replicas=3 -n ivolve
```
 
Output:
 
```text
deployment.apps/nginx-test created
```
 
```bash
kubectl get pods -n ivolve
```
 
Output:
 
```text
NAME                          READY   STATUS    RESTARTS   AGE
nginx-test-858fc98cf5-62qrm   1/1     Running   0          12s
nginx-test-858fc98cf5-j67jf   1/1     Running   0          12s
nginx-test-858fc98cf5-jpstb   1/1     Running   0          12s
```
 
### 3. Check quota usage (limit reached)
 
```bash
kubectl get resourcequota -n ivolve
```
 
Output:
 
```text
NAME          REQUEST     LIMIT   AGE
ivolvequota   pods: 3/3           4m57s
```
 
```bash
kubectl describe resourcequota ivolvequota -n ivolve
```
 
Output:
 
```text
Name:       ivolvequota
Namespace:  ivolve
Resource    Used  Hard
--------    ----  ----
pods        3     3
```
 
### 4. Try to exceed the limit
 
Scale the deployment to 4 replicas (one more than allowed):
 
```bash
kubectl scale deployment nginx-test --replicas=4 -n ivolve
```
 
Output:
 
```text
deployment.apps/nginx-test scaled
```
 
The scale command succeeds, but the 4th pod is rejected by the quota. Check the ReplicaSet events:
 
```bash
kubectl describe rs -n ivolve | grep -A 5 -i "quota"
```
 
Output:
 
```text
Warning  FailedCreate      1s (x2 over 4s)  replicaset-controller  (combined from similar events): Error creating: pods "nginx-test-858fc98cf5-f8swd" is forbidden: exceeded quota: ivolvequota, requested: pods=1, used: pods=3, limited: pods=3
```
 
Kubernetes refused to create the 4th pod: `exceeded quota: ivolvequota, requested: pods=1, used: pods=3, limited: pods=3`.
 
---
 
## Result
 
| Check | Status |
|-------|--------|
| Namespace `ivolve` created | Done |
| ResourceQuota `ivolvequota` limits pods to 3 | Done |
| Quota verified with `kubectl describe resourcequota` | Done |
| 3 pods created successfully (`3/3` used) | Done |
| 4th pod blocked with `exceeded quota` error | Done |
 
## Cleanup
 
```bash
kubectl delete deployment nginx-test -n ivolve
```
 
