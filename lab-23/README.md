# Lab 23: GitOps Workflow with Jenkins and Argo CD

## Objective

1. Install Argo CD in the Kubernetes cluster and log in to the UI.
2. Connect Argo CD to the GitHub repository.
3. Create an Argo CD Application named `ivovle` that watches the manifest directory.
4. Enable automatic sync.
5. Change the `DeployOnK8s` shared library so it **pushes** the edited deployment file to GitHub instead of running `kubectl apply`.
6. Run the Jenkins pipeline and confirm Argo CD syncs the new image.

## GitOps flow

```text
 git push ──► Jenkins (agent "slave")
              ├─ BuildApp     mvn test + package
              ├─ BuildImage   docker build → trivy → push toqaosama/demoapp:<build> → rmi
              └─ DeployOnK8s  sed new tag into lab-23/k8s/deployment.yaml
                              git commit "[skip ci]" + git push ──► GitHub
                                                                      │
                                Argo CD (polls every ~3 min) ◄────────┘
                                └─ auto-sync ──► Minikube namespace "ivolve"
```

Jenkins never touches the cluster any more; Git is the single source of truth.

## Structure

```text
lab-23/
├── argocd/application.yaml        # Argo CD Application "ivovle"
├── k8s/                           # directory watched by Argo CD
│   ├── deployment.yaml
│   └── service.yaml
├── shared-library/vars/
│   ├── BuildApp.groovy            # same as lab 22
│   ├── BuildImage.groovy          # same as lab 22
│   └── DeployOnK8s.groovy         # changed: edit file + git push
└── Jenkinsfile
```

The app source is reused from `lab-22/DemoApp`.

## Steps

### 1. Install Argo CD

```bash
minikube start --driver=docker
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl get pods -n argocd -w        # wait until all are Running
```

### 2. Open the UI and log in

```bash
kubectl port-forward svc/argocd-server -n argocd 8443:443
# initial admin password
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
```

Open https://localhost:8443 → user `admin` + the password above.

### 3. Connect the GitHub repository

**Settings → Repositories → Connect Repo**

- Connection method: HTTPS
- Repository URL: `https://github.com/toqa68/IvolveT.git`
- (Public repo: no credentials needed. Private repo: GitHub username + personal access token.)

Status must show **Successful**.

### 4. Create the application `ivovle` with auto-sync

```bash
# remove the lab 22 deployment first so Argo CD owns the resources
kubectl delete -f lab-22/deployment.yaml --ignore-not-found

kubectl apply -f lab-23/argocd/application.yaml
kubectl get applications -n argocd
```

`application.yaml` sets:

| Field | Value |
|---|---|
| repoURL | `https://github.com/toqa68/IvolveT.git` |
| path | `lab-23/k8s` |
| destination namespace | `ivolve` |
| syncPolicy.automated | `prune: true`, `selfHeal: true` |
| syncOptions | `CreateNamespace=true` |

### 5. Configure Jenkins

Credentials:

| ID | Type | Purpose |
|---|---|---|
| `docker` | Username with password | Docker Hub |
| `github` | Username with password | GitHub username + Personal Access Token (scope `repo`) for the push |

Global Trusted Pipeline Library:

| Field | Value |
|---|---|
| Name | `ivolve-shared-lib-gitops` |
| Default version | `main` |
| Repository | `https://github.com/toqa68/IvolveT.git` |
| Library Path | `lab-23/shared-library` |

Pipeline job → Pipeline script from SCM → Script Path `lab-23/Jenkinsfile`.

### 6. Trigger the pipeline

Click **Build Now**. The last stage pushes a commit like:

```text
Update image to toqaosama/demoapp:7 [skip ci]
```

### 7. Confirm Argo CD synced

```bash
kubectl get application ivovle -n argocd     # SYNC STATUS: Synced, HEALTH: Healthy
kubectl get deploy demoapp -n ivolve -o jsonpath='{.spec.template.spec.containers[0].image}'
```

The image tag must match the Jenkins build number. In the UI the app shows the new commit hash.
To sync immediately instead of waiting for the poll: **Refresh** in the UI, or
`argocd app sync ivovle`.

## Screenshots

- Argo CD login: `screenshots/argocd-login.png`
- Repo connected: `screenshots/argocd-repo.png`
- Jenkins pipeline: `screenshots/pipeline.png`
- Commit pushed by Jenkins: `screenshots/github-commit.png`
- Application synced/healthy: `screenshots/argocd-synced.png`
