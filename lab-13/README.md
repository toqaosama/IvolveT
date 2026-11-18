# Lab 13: Persistent Storage Setup for Application Logging
 
## Objective
 
1. Define a **PersistentVolume (PV)** backed by a `hostPath` on the node.
2. Define a **PersistentVolumeClaim (PVC)** that binds to the PV.
3. Make sure the PVC matches the PV (size and access mode) and that **no StorageClass** is used.
4. Verify that the PV and PVC are bound.
## Concepts
 
| Concept | Purpose |
|---------|---------|
| **PersistentVolume (PV)** | A piece of storage in the cluster. It is a cluster-level resource (no namespace). |
| **PersistentVolumeClaim (PVC)** | A request for storage by a user or an application. It lives in a namespace. |
| **hostPath** | Uses a directory on the node's file system as the volume. Suitable for labs, not for production. |
| **Reclaim policy: Retain** | When the PVC is deleted, the PV and its data are kept and must be cleaned manually. |
 
## Task Details
 
### PersistentVolume
 
| Setting | Value |
|---------|-------|
| Name | `ivolvepv` |
| Size | `1Gi` |
| Storage type | `hostPath` |
| Path | `/mnt/app-logs` (on the node file system) |
| Access mode | `ReadWriteMany` |
| Reclaim policy | `Retain` |
 
### PersistentVolumeClaim
 
| Setting | Value |
|---------|-------|
| Name | `ivolvepvc` |
| Namespace | `ivolve` |
| Request | `1Gi` |
| Access mode | `ReadWriteMany` |
| StorageClass | none (`storageClassName: ""`) |
 
> Kubernetes object names must be lowercase, so `ivolvePV` and `ivolvePVC` from the lab sheet are created as `ivolvepv` and `ivolvepvc`.
 
---
 
## Steps
 
### Step 1: Create the PersistentVolume
 
```bash
nano pv.yaml
```
 
`pv.yaml`:
 
```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: ivolvepv
spec:
  capacity:
    storage: 1Gi
  accessModes:
    - ReadWriteMany
  persistentVolumeReclaimPolicy: Retain
  hostPath:
    path: /mnt/app-logs
```
 
Apply it:
 
```bash
kubectl apply -f pv.yaml
```
 
Output:
 
```text
persistentvolume/ivolvepv created
```
 
### Step 2: Create the PersistentVolumeClaim
 
```bash
nano pvc.yaml
```
 
`pvc.yaml`:
 
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: ivolvepvc
  namespace: ivolve
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: ""
  resources:
    requests:
      storage: 1Gi
```
 
> `storageClassName: ""` explicitly disables dynamic provisioning, so the PVC binds to an existing PV instead of waiting for a default StorageClass.
 
Apply it:
 
```bash
kubectl apply -f pvc.yaml
```
 
Output:
 
```text
persistentvolumeclaim/ivolvepvc created
```
 
---
 
## Verification
 
### 1. Verify the PV
 
```bash
kubectl get pv ivolvepv
```
 
Output:
 
```text
NAME       CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM              STORAGECLASS   VOLUMEATTRIBUTESCLASS   REASON   AGE
ivolvepv   1Gi        RWX            Retain           Bound    ivolve/ivolvepvc                  <unset>                          107s
```
 
The PV status is `Bound` and its claim is `ivolve/ivolvepvc`. Capacity is `1Gi`, access mode is `RWX`, and the reclaim policy is `Retain`.
 
### 2. Verify the PVC
 
```bash
kubectl get pvc ivolvepvc -n ivolve
```
 
Output:
 
```text
NAME        STATUS   VOLUME     CAPACITY   ACCESS MODES   STORAGECLASS   VOLUMEATTRIBUTESCLASS   AGE
ivolvepvc   Bound    ivolvepv   1Gi        RWX                           <unset>                 33s
```
 
### 3. Describe the PVC
 
```bash
kubectl describe pvc ivolvepvc -n ivolve
```
 
Output:
 
```text
Name:          ivolvepvc
Namespace:     ivolve
StorageClass:  
Status:        Bound
Volume:        ivolvepv
Labels:        <none>
Annotations:   pv.kubernetes.io/bind-completed: yes
               pv.kubernetes.io/bound-by-controller: yes
Finalizers:    [kubernetes.io/pvc-protection]
Capacity:      1Gi
Access Modes:  RWX
VolumeMode:    Filesystem
Used By:       <none>
Conditions:
  Type     Status  LastProbeTime                     LastTransitionTime                Reason           Message
  ----     ------  -----------------                 ------------------                ------           -------
  Unused   True    Mon, 01 Jan 0001 00:00:00 +0000   Fri, 13 Nov 2026 01:07:42 +0000   NoPodsUsingPVC   No pods are currently referencing this PVC
Events:    <none>
```
 
- `StorageClass` is empty, so no StorageClass is used.
- `Status: Bound` and `Volume: ivolvepv` confirm the claim is bound to the PV.
- `Used By: <none>` is expected because no pod uses the PVC yet.
---
 
## Result
 
| Check | Status |
|-------|--------|
| PV `ivolvepv` created (1Gi, hostPath `/mnt/app-logs`, RWX, Retain) | Done |
| PVC `ivolvepvc` created (1Gi, RWX) | Done |
| No StorageClass used | Done |
| PV and PVC are `Bound` | Done |
 
## Notes
 
- `hostPath` stores data on one specific node. Pods that use this PVC must run on the node that holds `/mnt/app-logs`, which is why the application pods are scheduled with the `project=ivolve` node selector in the Deployment lab.
- With `Retain`, deleting the PVC leaves the PV in `Released` state and the data on the node is kept.
 
