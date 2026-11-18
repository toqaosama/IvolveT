# Lab 15: Node.js Application Deployment with ClusterIP Service
 
## Objective
 
Deploy the Node.js application (built and pushed to Docker Hub in Lab 8) to Kubernetes:
 
1. Create the Deployment `ivolveapp` in the `ivolve` namespace with **2 replicas**.
2. Use the custom Docker image from Docker Hub.
3. Consume environment variables from the existing ConfigMap and Secret.
4. Schedule the pods on the node labeled `project=ivolve` and tolerate the `workload=ivolveApp:NoSchedule` taint.
5. Mount the PersistentVolumeClaim from Lab 13 for the application logs.
6. Add an **init container** (MySQL client image) that creates the `ivolve` database and grants privileges to the application user.
7. Expose the application with a **ClusterIP Service**.
8. Verify the database, the privileges, the application logs, and the application endpoints.
## Concepts
 
| Concept | Purpose |
|---------|---------|
| **Deployment** | Manages stateless application pods and keeps the desired number of replicas running. |
| **Init container** | Runs to completion before the main container starts. Used here to prepare the database. |
| **ClusterIP Service** | Gives the pods a stable internal IP and DNS name, and load-balances traffic across them. |
 
> Kubernetes object names must be lowercase, so the Service from the lab sheet (`ivolvaAppSvc`) is created as **`ivolvaappsvc`**.
 
## Environment
 
| Item | Value |
|------|-------|
| Namespace | `ivolve` |
| Application node | `ip-172-31-24-135` (label `project=ivolve`, taint `workload=ivolveApp:NoSchedule`) |
| Application image | `marceleno17/lab8-app:latest` (from Lab 8) |
| Application port | `3000` |
| Database | `ivolvedb-0` (StatefulSet from Lab 14) behind the headless Service `ivolvedbsvc` |
| ConfigMap / Secret | `ivolvecm` / `ivolvesecret` (Lab 12) |
| PVC | `ivolvepvc` (Lab 13) |
 
---
 
## Steps
 
### Step 1: Create the ClusterIP Service
 
`service.yaml`:
 
```yaml
apiVersion: v1
kind: Service
metadata:
  name: ivolvaappsvc
  namespace: ivolve
  labels:
    app: ivolveapp
spec:
  type: ClusterIP
  ports:
    - port: 3000
      targetPort: 3000
      name: http
  selector:
    app: ivolveapp
```
 
```bash
kubectl apply -f service.yaml
```
 
```text
service/ivolvaappsvc created
```
 
### Step 2: Create the Deployment
 
`deployment.yaml`:
 
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ivolveapp
  namespace: ivolve
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ivolveapp
  template:
    metadata:
      labels:
        app: ivolveapp
    spec:
      nodeSelector:
        project: ivolve
      tolerations:
        - key: workload
          operator: Equal
          value: ivolveApp
          effect: NoSchedule
      initContainers:
        - name: init-db
          image: mysql:latest
          env:
            - name: DB_HOST
              valueFrom:
                configMapKeyRef:
                  name: ivolvecm
                  key: DB_HOST
            - name: MYSQL_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: ivolvesecret
                  key: MYSQL_ROOT_PASSWORD
            - name: DB_USER
              valueFrom:
                configMapKeyRef:
                  name: ivolvecm
                  key: DB_USER
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: ivolvesecret
                  key: DB_PASSWORD
          command:
            - "sh"
            - "-c"
            - |
              until mysql -h "$DB_HOST" -u root -p"$MYSQL_ROOT_PASSWORD" -e "SELECT 1;" > /dev/null 2>&1; do
                echo "Waiting for MySQL at $DB_HOST to be ready..."
                sleep 3
              done
              mysql -h "$DB_HOST" -u root -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS ivolve; CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASSWORD'; GRANT ALL PRIVILEGES ON ivolve.* TO '$DB_USER'@'%'; FLUSH PRIVILEGES;"
              echo "Database ivolve initialized and privileges granted to $DB_USER."
      containers:
        - name: app
          image: marceleno17/lab8-app:latest
          ports:
            - containerPort: 3000
              name: http
          env:
            - name: DB_HOST
              valueFrom:
                configMapKeyRef:
                  name: ivolvecm
                  key: DB_HOST
            - name: DB_USER
              valueFrom:
                configMapKeyRef:
                  name: ivolvecm
                  key: DB_USER
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: ivolvesecret
                  key: DB_PASSWORD
          volumeMounts:
            - name: log-volume
              mountPath: /app/logs
      volumes:
        - name: log-volume
          persistentVolumeClaim:
            claimName: ivolvepvc
```
 
What the manifest does:
 
- **Init container `init-db`:** waits until MySQL answers, then creates the `ivolve` database, creates the application user, and grants it all privileges on `ivolve.*`. Its connection parameters come from the ConfigMap and the Secret.
- **Scheduling:** `nodeSelector` (`project: ivolve`) and a toleration for `workload=ivolveApp:NoSchedule`.
- **Logs:** the PVC `ivolvepvc` is mounted at `/app/logs`.
Check that the PVC from Lab 13 is bound before deploying:
 
```bash
kubectl get pvc ivolvepvc -n ivolve
```
 
```text
NAME        STATUS   VOLUME           CAPACITY   ACCESS MODES   STORAGECLASS   VOLUMEATTRIBUTESCLASS   AGE
ivolvepvc   Bound    ivolvepv-lab13   1Gi        RWX                           <unset>                 14m
```
 
Apply the Deployment:
 
```bash
kubectl apply -f deployment.yaml
```
 
```text
deployment.apps/ivolveapp created
```
 
---
 
## Verification
 
### 1. Deployment and pods
 
```bash
kubectl get deployments -n ivolve
kubectl get pods -n ivolve -o wide
```
 
```text
NAME        READY   UP-TO-DATE   AVAILABLE   AGE
ivolveapp   1/2     1            1           8s
NAME                         READY   STATUS    RESTARTS       AGE     IP             NODE               NOMINATED NODE   READINESS GATES
ivolveapp-68f6fc9b76-qpqmc   1/1     Running   0              8s      192.168.74.8   ip-172-31-24-135   <none>           <none>
ivolvedb-0                   1/1     Running   1 (104m ago)   2d21h   192.168.74.6   ip-172-31-24-135   <none>           <none>
network                      1/1     Running   1 (104m ago)   45h     192.168.74.7   ip-172-31-24-135   <none>           <none>
```
 
The first application pod is `Running` on the dedicated node `ip-172-31-24-135`. The init container finished successfully and the app started. **Only 1 of the 2 replicas is running**, see the Troubleshooting section below.
 
```bash
kubectl rollout status deployment/ivolveapp -n ivolve
```
 
```text
Waiting for deployment "ivolveapp" rollout to finish: 1 out of 2 new replicas have been updated...
error: deployment "ivolveapp" exceeded its progress deadline
```
 
```bash
kubectl get pods -l app=ivolveapp -n ivolve -o wide
```
 
```text
NAME                         READY   STATUS    RESTARTS   AGE   IP             NODE               NOMINATED NODE   READINESS GATES
ivolveapp-68f6fc9b76-qpqmc   1/1     Running   0          10m   192.168.74.8   ip-172-31-24-135   <none>           <none>
```
 
### 2. Service and endpoints
 
```bash
kubectl get svc ivolvaappsvc -n ivolve
```
 
```text
NAME           TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)    AGE
ivolvaappsvc   ClusterIP   10.108.104.143   <none>        3000/TCP   14m
```
 
```bash
kubectl get endpoints ivolvaappsvc -n ivolve
```
 
```text
Warning: v1 Endpoints is deprecated in v1.33+; use discovery.k8s.io/v1 EndpointSlice
NAME           ENDPOINTS           AGE
ivolvaappsvc   192.168.74.8:3000   15m
```
 
The Service has an endpoint for every ready pod (currently one).
 
### 3. Verify the database and the user privileges manually
 
```bash
kubectl exec -it ivolvedb-0 -n ivolve -- mysql -u root -p123 -e "SHOW DATABASES; SHOW GRANTS FOR 'admin'@'%';"
```
 
```text
mysql: [Warning] Using a password on the command line interface can be insecure.
+--------------------+
| Database           |
+--------------------+
| information_schema |
| ivolve             |
| mysql              |
| performance_schema |
| sys                |
+--------------------+
+---------------------------------------------------+
| Grants for admin@%                                |
+---------------------------------------------------+
| GRANT USAGE ON *.* TO `admin`@`%`                 |
| GRANT ALL PRIVILEGES ON `ivolve`.* TO `admin`@`%` |
+---------------------------------------------------+
```
 
The `ivolve` database exists and `admin` has `ALL PRIVILEGES` on `ivolve.*`.
 
### 4. Application logs
 
```bash
kubectl logs -l app=ivolveapp -c app -n ivolve --tail=10
```
 
```text
🔄 Attempting to connect to MySQL...
✅ Connected to MySQL and 'ivolve' DB found.
🚀 Server started on http://0.0.0.0:3000
```
 
The application connected to MySQL and found the `ivolve` database.
 
### 5. Test the application through the Service
 
From the `network` diagnostic pod (Lab 14):
 
```bash
kubectl exec -it network -n ivolve -- curl -s http://ivolvaappsvc:3000/health
```
 
```text
🚀 iVolve web app is working! Keep calm and code on! 🎉
```
 
```bash
kubectl exec -it network -n ivolve -- curl -s http://ivolvaappsvc:3000/ready
```
 
```text
👍 iVolve web app is ready to rock and roll! 🤘
```
 
```bash
kubectl exec -it network -n ivolve -- curl -s http://ivolvaappsvc:3000 | head -n 10
```
 
```text
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>iVolve | Global Cloud Company</title>
  <style>
    body {
      font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
      background-color: #f2f4f8;
```
 
The Service name `ivolvaappsvc` resolves inside the cluster and routes traffic to the application.
 
---
 
## Troubleshooting: only 1 of 2 replicas is running
 
`kubectl rollout status` reports `exceeded its progress deadline` and the Deployment stays at `1/2`.
 
**Likely cause:** the ResourceQuota from Lab 11 (`ivolvequota`) limits the `ivolve` namespace to **3 pods**, and exactly 3 pods already exist:
 
| Pod | Source |
|-----|--------|
| `ivolvedb-0` | MySQL StatefulSet (Lab 14) |
| `network` | diagnostic pod (Lab 14) |
| `ivolveapp-68f6fc9b76-qpqmc` | first application replica |
 
The second application replica cannot be created because the quota is full.
 
**Confirm:**
 
```bash
kubectl describe rs -n ivolve | grep -i -A3 quota
kubectl describe resourcequota ivolvequota -n ivolve
```
 
Look for `exceeded quota: ivolvequota` in the ReplicaSet events and `pods 3 / 3` in the quota.
 
**Fix (choose one):**
 
Delete the diagnostic pod to free a slot:
 
```bash
kubectl delete pod network -n ivolve
```
 
Or increase the pod limit in `quota.yaml` (for example `pods: "5"`) and re-apply it:
 
```bash
kubectl apply -f quota.yaml
```
 
Then check again:
 
```bash
kubectl rollout status deployment/ivolveapp -n ivolve
kubectl get pods -l app=ivolveapp -n ivolve -o wide
```
 
---
 
## Result
 
| Check | Status |
|-------|--------|
| Service `ivolvaappsvc` (ClusterIP) created | Done |
| Deployment `ivolveapp` created with the Docker Hub image | Done |
| Environment variables from ConfigMap and Secret | Done |
| Node selector and toleration applied (pod runs on `ip-172-31-24-135`) | Done |
| PVC `ivolvepvc` mounted at `/app/logs` | Done |
| Init container created the `ivolve` database and granted privileges | Done |
| Database and privileges verified manually | Done |
| Application logs show a successful MySQL connection | Done |
| Application reachable through the Service (`/health`, `/ready`, `/`) | Done |
| 2 replicas running | 1/2, blocked by the pod quota (see Troubleshooting) |
 
