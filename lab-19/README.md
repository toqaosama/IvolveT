# Lab 19: Securing Kubernetes with RBAC and Service Accounts
 
## Objective
 
Give Jenkins a limited identity in the cluster using RBAC:
 
1. Create the `jenkins-sa` **ServiceAccount** in the `ivolve` namespace.
2. Create a **token** for the ServiceAccount.
3. Define a **Role** `ivolveDeployRole` that grants `create` and `update` permissions on Deployments in the `ivolve` namespace.
4. Bind the Role to the ServiceAccount with a **RoleBinding** `ivolveDeployRoleBinding`.
5. Validate the permissions with `kubectl auth can-i`.
## Concepts
 
| Concept | Purpose |
|---------|---------|
| **ServiceAccount** | An identity for processes (such as Jenkins) that talk to the Kubernetes API. |
| **Token** | The credential the ServiceAccount uses to authenticate to the API server. |
| **Role** | A set of permissions (verbs on resources) inside **one namespace**. |
| **RoleBinding** | Grants the permissions of a Role to a user, group, or ServiceAccount inside the same namespace. |
 
> The ServiceAccount is created as `jenkins-sa` (the lab sheet writes `jenkinsSA`), because ServiceAccount names must be lowercase DNS names. The Role and RoleBinding keep the names from the lab sheet.
 
## Task Details
 
| Object | Name | Namespace |
|--------|------|-----------|
| ServiceAccount | `jenkins-sa` | `ivolve` |
| Role | `ivolveDeployRole` | `ivolve` |
| RoleBinding | `ivolveDeployRoleBinding` | `ivolve` |
| Permissions | `create`, `update` on `deployments` (API group `apps`) | `ivolve` |
 
---
 
## Steps
 
### Step 1: Create the ServiceAccount
 
`jenkins-sa.yaml`:
 
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: jenkins-sa
  namespace: ivolve
```
 
```bash
kubectl apply -f jenkins-sa.yaml
```
 
```text
serviceaccount/jenkins-sa created
```
 
```bash
kubectl get sa -n ivolve
```
 
```text
NAME         AGE
default      5d10h
jenkins-sa   7s
```
 
### Step 2: Create a token for the ServiceAccount
 
Create a short-lived token (the default lifetime is 1 hour):
 
```bash
kubectl create token jenkins-sa -n ivolve
```
 
```text
<TOKEN>
```
 
Create a token that lasts 24 hours and show the full `TokenRequest` object:
 
```bash
kubectl create token jenkins-sa -n ivolve --duration=24h -o yaml
```
 
```text
apiVersion: authentication.k8s.io/v1
kind: TokenRequest
metadata:
  creationTimestamp: "2026-11-18T11:40:42Z"
  name: jenkins-sa
  namespace: ivolve
  uid: b4922676-6be9-457a-9dfb-0e016c5d763f
spec:
  audiences:
  - https://kubernetes.default.svc.cluster.local
  boundObjectRef: null
  expirationSeconds: 86400
status:
  expirationTimestamp: "2026-11-19T11:40:42Z"
  token: <TOKEN>
```
 
> The real token values are replaced with `<TOKEN>` on purpose. A token is a credential: anyone who has it can act as `jenkins-sa`. Never commit it to a repository.
 
Create a **long-lived** token stored in a Secret (useful for Jenkins, since `kubectl create token` tokens expire).
 
`secret.yaml`:
 
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: jenkins-sa-token
  namespace: ivolve
  annotations:
    kubernetes.io/service-account.name: jenkins-sa
type: kubernetes.io/service-account-token
```
 
```bash
kubectl apply -f secret.yaml
```
 
```text
secret/jenkins-sa-token created
```
 
Kubernetes fills this Secret with a token for `jenkins-sa`. To read it:
 
```bash
kubectl get secret jenkins-sa-token -n ivolve -o jsonpath="{.data.token}" | base64 --decode
```
 
### Step 3: Create the Role
 
`role.yaml` (create and update on Deployments only):
 
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ivolveDeployRole
  namespace: ivolve
rules:
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["create", "update"]
```
 
```bash
kubectl apply -f role.yaml
```
 
```text
role.rbac.authorization.k8s.io/ivolveDeployRole created
```
 
### Step 4: Bind the Role to the ServiceAccount
 
`rolebinding.yaml`:
 
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ivolveDeployRoleBinding
  namespace: ivolve
subjects:
  - kind: ServiceAccount
    name: jenkins-sa
    namespace: ivolve
roleRef:
  kind: Role
  name: ivolveDeployRole
  apiGroup: rbac.authorization.k8s.io
```
 
```bash
kubectl apply -f rolebinding.yaml
```
 
```text
rolebinding.rbac.authorization.k8s.io/ivolveDeployRoleBinding created
```
 
---
 
## Validation
 
`kubectl auth can-i` with `--as=system:serviceaccount:<namespace>:<name>` checks what a ServiceAccount is allowed to do.
 
### Checks that were run
 
```bash
kubectl auth can-i list pods --as=system:serviceaccount:ivolve:jenkins-sa -n ivolve
```
 
```text
yes
```
 
```bash
kubectl auth can-i create pods --as=system:serviceaccount:ivolve:jenkins-sa -n ivolve
```
 
```text
yes
```
 
```bash
kubectl auth can-i list pods --as=system:serviceaccount:ivolve:jenkins-sa -n default
```
 
```text
no
```
 
```bash
kubectl auth can-i delete pods --as=system:serviceaccount:ivolve:jenkins-sa -n ivolve
```
 
```text
no
```
 
What these results show:
 
- The permissions apply **only inside the `ivolve` namespace**: the same check in `default` returns `no`.
- `delete` is not allowed, so the account does not have full access.
### Validation gap: check Deployments, not Pods
 
The lab requires the account to **create/update Deployments only**, but the checks above test **Pods**. Also, `list pods` and `create pods` returned `yes`, which means the Role that was applied grants more than Deployment permissions. Inspect the Role:
 
```bash
kubectl get role ivolveDeployRole -n ivolve -o yaml
```
 
If it contains rules for `pods`, remove them so that only `deployments` with `create` and `update` remain, then re-apply `role.yaml`. After that, run the checks that match the lab requirement:
 
```bash
kubectl auth can-i create deployments --as=system:serviceaccount:ivolve:jenkins-sa -n ivolve
kubectl auth can-i update deployments --as=system:serviceaccount:ivolve:jenkins-sa -n ivolve
kubectl auth can-i delete deployments --as=system:serviceaccount:ivolve:jenkins-sa -n ivolve
kubectl auth can-i create deployments --as=system:serviceaccount:ivolve:jenkins-sa -n default
kubectl auth can-i list pods --as=system:serviceaccount:ivolve:jenkins-sa -n ivolve
```
 
Expected results:
 
| Command | Expected |
|---------|----------|
| create deployments in `ivolve` | `yes` |
| update deployments in `ivolve` | `yes` |
| delete deployments in `ivolve` | `no` |
| create deployments in `default` | `no` |
| list pods in `ivolve` | `no` |
 
---
 
## Result
 
| Check | Status |
|-------|--------|
| ServiceAccount `jenkins-sa` created in `ivolve` | Done |
| Token created for the ServiceAccount (`kubectl create token` and a long-lived Secret) | Done |
| Role `ivolveDeployRole` created | Done |
| RoleBinding `ivolveDeployRoleBinding` created | Done |
| Permissions limited to the `ivolve` namespace | Done (`no` in `default`) |
| Role allows only create/update on Deployments | Needs the Deployment checks above (the Pod checks showed extra permissions) |
 
## Notes
 
- This ServiceAccount and token are used by Jenkins in the CI/CD labs. If the pipeline deploys with `kubectl apply`, it usually also needs the `get` and `patch` verbs on Deployments, because `apply` reads the object and patches it when it already exists.
 
