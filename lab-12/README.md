# Lab 12: Managing Configuration and Sensitive Data with ConfigMaps and Secrets
 
## Objective
 
1. Create a **ConfigMap** named `ivolvecm` in the `ivolve` namespace to store non-sensitive MySQL configuration.
2. Create a **Secret** named `ivolvesecret` in the `ivolve` namespace to store sensitive MySQL credentials.
3. Use **Base64 encoding** for the Secret values when defining the Secret in a YAML manifest.
4. Verify both objects.
## Concepts
 
| Concept | Purpose |
|---------|---------|
| **ConfigMap** | Stores non-sensitive configuration as key/value pairs. |
| **Secret** | Stores sensitive data (passwords, tokens). Values are Base64 encoded. |
 
> Base64 is an **encoding**, not encryption. Anyone with access to the Secret can decode it, so restrict access with RBAC.
> Kubernetes object names must be lowercase, so `ivolveCM` and `ivolveSecret` from the lab sheet are created as `ivolvecm` and `ivolvesecret`.
 
## Task Details
 
| Object | Name | Key | Value |
|--------|------|-----|-------|
| ConfigMap | `ivolvecm` | `DB_HOST` | `ivolvedbsvc` (headless Service of the MySQL StatefulSet) |
| ConfigMap | `ivolvecm` | `DB_USER` | `admin` |
| Secret | `ivolvesecret` | `MYSQL_ROOT_PASSWORD` | `123` |
| Secret | `ivolvesecret` | `DB_PASSWORD` | `admin123` |
 
---
 
## Steps
 
### 1. Create the ConfigMap
 
Create `configmap.yaml` in the `ivolve` namespace to store the non-sensitive MySQL configuration variables:
 
- `DB_HOST`: hostname of the MySQL StatefulSet's headless service (`ivolvedbsvc`).
- `DB_USER`: application database user (`admin`).
`configmap.yaml`:
 
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ivolvecm
  namespace: ivolve
data:
  DB_HOST: "ivolvedbsvc"
  DB_USER: "admin"
```
 
Apply the ConfigMap:
 
```bash
kubectl apply -f configmap.yaml
```
 
Alternatively, create it directly with the CLI:
 
```bash
kubectl create configmap ivolvecm --from-literal=DB_HOST=ivolvedbsvc --from-literal=DB_USER=admin -n ivolve
```
 
### 2. Encode the sensitive credentials with Base64
 
Generate the Base64 values for `MYSQL_ROOT_PASSWORD` (`123`) and `DB_PASSWORD` (`admin123`). The `-n` flag prevents a trailing newline from being encoded.
 
```bash
echo -n '123' | base64
```
 
```text
MTIz
```
 
```bash
echo -n 'admin123' | base64
```
 
```text
YWRtaW4xMjM=
```
 
### 3. Create the Secret
 
Create `secret.yaml` in the `ivolve` namespace using the Base64 encoded values.
 
`secret.yaml`:
 
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ivolvesecret
  namespace: ivolve
type: Opaque
data:
  MYSQL_ROOT_PASSWORD: MTIz
  DB_PASSWORD: YWRtaW4xMjM=
```
 
Apply the Secret:
 
```bash
kubectl apply -f secret.yaml
```
 
Alternatively, create it directly with the CLI (kubectl encodes the values for you):
 
```bash
kubectl create secret generic ivolvesecret --from-literal=MYSQL_ROOT_PASSWORD=123 --from-literal=DB_PASSWORD=admin123 -n ivolve
```
 
---
 
## Verification
 
### 1. Verify the ConfigMap
 
```bash
kubectl get configmap ivolvecm -n ivolve
```
 
```text
NAME       DATA   AGE
ivolvecm   2      1m
```
 
```bash
kubectl describe configmap ivolvecm -n ivolve
```
 
```text
Name:         ivolvecm
Namespace:    ivolve
Labels:       <none>
Annotations:  <none>
 
Data
====
DB_HOST:
----
ivolvedbsvc
DB_USER:
----
admin
```
 
### 2. Verify the Secret
 
```bash
kubectl get secret ivolvesecret -n ivolve
```
 
```text
NAME           TYPE     DATA   AGE
ivolvesecret   Opaque   2      1m
```
 
```bash
kubectl describe secret ivolvesecret -n ivolve
```
 
```text
Name:         ivolvesecret
Namespace:    ivolve
Labels:       <none>
Annotations:  <none>
 
Type:  Opaque
 
Data
====
DB_PASSWORD:          8 bytes
MYSQL_ROOT_PASSWORD:  3 bytes
```
 
`kubectl describe` hides the values and only shows their sizes.
 
### 3. Verify the decoded Secret values
 
```bash
kubectl get secret ivolvesecret -n ivolve -o jsonpath="{.data.DB_PASSWORD}" | base64 --decode && echo ""
```
 
```text
admin123
```
 
```bash
kubectl get secret ivolvesecret -n ivolve -o jsonpath="{.data.MYSQL_ROOT_PASSWORD}" | base64 --decode && echo ""
```
 
```text
123
```
 
The decoded values match the original passwords.
 
---
 
## Result
 
| Check | Status |
|-------|--------|
| ConfigMap `ivolvecm` created with `DB_HOST` and `DB_USER` | Done |
| Secret `ivolvesecret` created with Base64 encoded values | Done |
| ConfigMap keys verified (`DATA = 2`) | Done |
| Secret keys verified (`DATA = 2`) | Done |
| Secret values decoded and verified | Done |
 
## Note for the next labs
 
These objects are consumed by the MySQL StatefulSet and the application Deployment as environment variables:
 
```yaml
envFrom:
  - configMapRef:
      name: ivolvecm
  - secretRef:
      name: ivolvesecret
```
 
