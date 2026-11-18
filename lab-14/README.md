# Lab 14: StatefulSet with Headless Service
 
## Objective
 
1. Reuse the ConfigMap and Secret created in Lab 12.
2. Create a **headless Service** (`ivolvedbsvc`) that gives the MySQL pod a stable network identity.
3. Create the **StatefulSet** `ivolvedb` (1 replica, MySQL) that:
   - consumes its configuration from the ConfigMap and the Secret,
   - is scheduled only on the node labeled `project=ivolve`,
   - tolerates the `workload=ivolveApp:NoSchedule` taint,
   - gets persistent storage through a `volumeClaimTemplate` mounted at `/var/lib/mysql`.
4. Verify that the database is operational with a MySQL client.
5. Verify DNS resolution of the MySQL pod inside the cluster.
## Concepts
 
| Concept | Purpose |
|---------|---------|
| **StatefulSet** | Manages stateful applications. Each pod gets a stable name (`ivolvedb-0`) and its own storage. |
| **Headless Service** (`clusterIP: None`) | Has no virtual IP. DNS returns the pod IPs directly, giving each StatefulSet pod a stable DNS name. |
| **volumeClaimTemplate** | Creates one PVC per pod automatically. |
| **nodeSelector + toleration** | Schedules the pod on the dedicated node (label) and allows it on the tainted node (toleration). |
 
> Kubernetes object names must be lowercase, so `ivolveDB` and `ivolveDBSvc` from the lab sheet are created as `ivolvedb` and `ivolvedbsvc`.
 
## Environment
 
| Item | Value |
|------|-------|
| Namespace | `ivolve` |
| Nodes | `minikube` (control plane), `minikube-m02` (worker) |
| Pod network | `10.244.0.0/16` |
| MySQL image | `mysql:latest` |
 
## Task Details
 
| Setting | Value |
|---------|-------|
| StatefulSet | `ivolvedb` (1 replica) |
| Headless Service | `ivolvedbsvc` (port `3306`) |
| Node selector | `project: ivolve` |
| Toleration | `workload=ivolveApp:NoSchedule` |
| Volume claim template | `mysql-data`, `1Gi`, mounted at `/var/lib/mysql` |
| Database created at startup | `ivolve` |
 
---
 
## Steps
 
### 1. Copy the ConfigMap and Secret from Lab 12
 
Copy the configuration and secret manifests:
 
```bash
cp ../lab12/secret.yaml ../lab12/configmap.yaml .
```
 
Apply both manifests in the `ivolve` namespace:
 
```bash
kubectl apply -f configmap.yaml
```
 
```bash
kubectl apply -f secret.yaml
```
 
### 2. Create the headless Service (`service.yaml`)
 
Create `service.yaml` defining a headless Service (`clusterIP: None`) named `ivolvedbsvc` in the `ivolve` namespace. It provides a stable network identity and direct DNS resolution for the StatefulSet pod.
 
`service.yaml`:
 
```yaml
apiVersion: v1
kind: Service
metadata:
  name: ivolvedbsvc
  namespace: ivolve
  labels:
    app: ivolvedb
spec:
  clusterIP: None
  ports:
    - port: 3306
      name: mysql
  selector:
    app: ivolvedb
```
 
Apply the headless Service:
 
```bash
kubectl apply -f service.yaml
```
 
### 3. Create the StatefulSet (`statefulset.yaml`)
 
Create `statefulset.yaml` defining the `ivolvedb` StatefulSet in the `ivolve` namespace:
 
- 1 replica running `mysql:latest`.
- Sensitive credentials (`MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`) sourced from `ivolvesecret`.
- Non-sensitive user (`MYSQL_USER`) sourced from `ivolvecm`.
- Target scheduling via `nodeSelector` (`project: ivolve`).
- Matching toleration for the worker node taint (`workload=ivolveApp:NoSchedule`).
- `volumeClaimTemplate` mounting `/var/lib/mysql`.
`statefulset.yaml`:
 
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: ivolvedb
  namespace: ivolve
spec:
  serviceName: ivolvedbsvc
  replicas: 1
  selector:
    matchLabels:
      app: ivolvedb
  template:
    metadata:
      labels:
        app: ivolvedb
    spec:
      nodeSelector:
        project: ivolve
      tolerations:
        - key: workload
          operator: Equal
          value: ivolveApp
          effect: NoSchedule
      containers:
        - name: mysql
          image: mysql:latest
          ports:
            - containerPort: 3306
              name: mysql
          env:
            - name: MYSQL_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: ivolvesecret
                  key: MYSQL_ROOT_PASSWORD
            - name: MYSQL_USER
              valueFrom:
                configMapKeyRef:
                  name: ivolvecm
                  key: DB_USER
            - name: MYSQL_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: ivolvesecret
                  key: DB_PASSWORD
            - name: MYSQL_DATABASE
              value: ivolve
          volumeMounts:
            - name: mysql-data
              mountPath: /var/lib/mysql
  volumeClaimTemplates:
    - metadata:
        name: mysql-data
      spec:
        accessModes:
          - ReadWriteOnce
        resources:
          requests:
            storage: 1Gi
```
 
Apply the StatefulSet:
 
```bash
kubectl apply -f statefulset.yaml
```
 
---
 
## Verification
 
### 1. Verify the StatefulSet and the pod
 
```bash
kubectl get statefulset ivolvedb -n ivolve
```
 
```text
NAME       READY   AGE
ivolvedb   1/1     30m
```
 
```bash
kubectl get pods -n ivolve -o wide
```
 
```text
NAME         READY   STATUS    RESTARTS   AGE   IP           NODE           NOMINATED NODE   READINESS GATES
ivolvedb-0   1/1     Running   0          30m   10.244.1.7   minikube-m02   <none>           <none>
```
 
The pod `ivolvedb-0` is `Running` on the worker node `minikube-m02`.
 
### 2. Verify the dynamically provisioned PVC
 
```bash
kubectl get pvc -n ivolve
```
 
```text
NAME                      STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
mysql-data-ivolvedb-0     Bound    pvc-87612f0a-012a-43d1-bf34-73349e5d7a81   1Gi        RWO            standard       30m
```
 
The PVC `mysql-data-ivolvedb-0` was created automatically from the `volumeClaimTemplate` and is `Bound`.
 
### 3. Verify the database with a MySQL client
 
Run a MySQL query inside the `ivolvedb-0` pod using the credentials from `ivolvesecret`:
 
```bash
kubectl exec -it ivolvedb-0 -n ivolve -- mysql -uadmin -padmin123 -e "SHOW DATABASES;"
```
 
```text
+--------------------+
| Database           |
+--------------------+
| information_schema |
| ivolve             |
| performance_schema |
+--------------------+
```
 
The `ivolve` database exists and the `admin` user can connect, so the database is operational.
 
### 4. Verify DNS resolution of the MySQL pod inside the cluster
 
Run a diagnostic container (`nicolaka/netshoot`) in the `ivolve` namespace:
 
```bash
kubectl run -n ivolve network --image nicolaka/netshoot -it -- bash
```
 
Inside the container, look up the StatefulSet pod through the headless Service:
 
```bash
nslookup ivolvedb-0.ivolvedbsvc
```
 
```text
Server:         10.96.0.10
Address:        10.96.0.10#53
 
Name:   ivolvedb-0.ivolvedbsvc.ivolve.svc.cluster.local
Address: 10.244.1.7
```
 
The DNS name `ivolvedb-0.ivolvedbsvc.ivolve.svc.cluster.local` resolves to the pod IP `10.244.1.7`, which matches the IP of `ivolvedb-0`.
 
Exit the diagnostic pod:
 
```bash
exit
```
 
Verify the pods status:
 
```bash
kubectl get pods -n ivolve -o wide
```
 
```text
NAME         READY   STATUS    RESTARTS     AGE     IP           NODE           NOMINATED NODE   READINESS GATES
ivolvedb-0   1/1     Running   0            30m     10.244.1.7   minikube-m02   <none>           <none>
network      1/1     Running   1 (9s ago)   2m28s   10.244.0.6   minikube       <none>           <none>
```
 
> The `network` pod shows one restart because it was started with `bash`, which ends when you run `exit`, and the pod restarts it. It also runs on `minikube` since it has no nodeSelector.
 
---
 
## Result
 
| Check | Status |
|-------|--------|
| ConfigMap and Secret applied in `ivolve` | Done |
| Headless Service `ivolvedbsvc` created | Done |
| StatefulSet `ivolvedb` running (`1/1`) | Done |
| Pod scheduled on the node labeled `project=ivolve` | Done |
| PVC created from `volumeClaimTemplate` and `Bound` | Done |
| MySQL reachable with a client, database `ivolve` exists | Done |
| Pod DNS name resolves through the headless Service | Done |
 
## Cleanup
 
```bash
kubectl delete pod network -n ivolve
```
