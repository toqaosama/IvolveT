# Lab 22: CI/CD Pipeline with Jenkins Agents and Shared Libraries

## Objective

1. Take the Dockerfile/app from https://github.com/Ibrahim-Adel15/DemoApp.git
2. Create three Jenkins shared libraries:
   - `BuildApp` – unit test, build app
   - `BuildImage` – build image, scan image, push image, delete image locally
   - `DeployOnK8s` – edit deployment file, deploy to cluster
3. Update the pipeline to use them.
4. Configure a Jenkins slave (agent) to run the pipeline.

## Structure

```text
lab-22/
├── DemoApp/                     # Spring Boot app cloned from Ibrahim-Adel15/DemoApp
│   ├── Dockerfile
│   ├── pom.xml                  # + spring-boot-starter-test for the unit test
│   └── src/
│       ├── main/java/com/example/demo/DemoApplication.java
│       └── test/java/com/example/demo/DemoApplicationTests.java
├── shared-library/
│   └── vars/
│       ├── BuildApp.groovy
│       ├── BuildImage.groovy
│       └── DeployOnK8s.groovy
├── agent/start-agent.sh         # starts the inbound Jenkins agent (WSL)
├── deployment.yaml              # Namespace + Deployment + NodePort Service
└── Jenkinsfile
```

## Architecture

```text
GitHub repo ──► Jenkins master (Docker, :8080) ──► agent "slave" (WSL Ubuntu)
                                                    ├─ mvn test / package
                                                    ├─ docker build → trivy → push → rmi
                                                    └─ kubectl apply → Minikube
```

The master only schedules; every stage runs on the agent with label `slave`,
because the agent has Maven, Docker, Trivy and kubectl installed.

## Shared libraries

| Library | Stages | Parameters |
|---|---|---|
| `BuildApp` | Unit Test (`mvn test`), Build App (`mvn package`) | `appDir` |
| `BuildImage` | Build Image, Scan Image (Trivy), Push Image, Delete Local Image | `image`, `appDir`, `credentialsId` |
| `DeployOnK8s` | Edit Deployment File (`sed` new tag), Deploy to Cluster (`kubectl apply` + rollout) | `image`, `manifest`, `deployment`, `namespace`, `kubeconfigId` |

Pipeline usage:

```groovy
@Library('ivolve-shared-lib') _
...
BuildApp(appDir: 'lab-22/DemoApp')
BuildImage(image: env.IMAGE, appDir: 'lab-22/DemoApp', credentialsId: 'docker')
DeployOnK8s(image: env.IMAGE, manifest: 'lab-22/deployment.yaml', deployment: 'demoapp', namespace: 'ivolve')
```

## Steps

### 1. Prepare the agent machine (WSL Ubuntu)

Tools required on the agent: `java 17`, `maven`, `docker`, `trivy`, `kubectl`, `git`.

```bash
java -version && mvn -v && docker version && trivy --version && kubectl version --client
minikube start --driver=docker
```

### 2. Create the agent node in Jenkins

**Manage Jenkins → Nodes → New Node**

| Field | Value |
|---|---|
| Name | `slave` |
| Type | Permanent Agent |
| Remote root directory | `/home/<user>/jenkins-agent` |
| Labels | `slave` |
| Launch method | Launch agent by connecting it to the controller |

Then set **Built-In Node → Number of executors = 0** so builds only run on the agent.

### 3. Start the agent

Copy the secret from the node page and run on WSL:

```bash
export JENKINS_URL=http://localhost:8080
export AGENT_NAME=slave
export AGENT_SECRET=<secret>
bash lab-22/agent/start-agent.sh
```

The node should show as **connected** in Jenkins.

### 4. Register the shared library

**Manage Jenkins → System → Global Trusted Pipeline Libraries → Add**

| Field | Value |
|---|---|
| Name | `ivolve-shared-lib` |
| Default version | `main` |
| Retrieval method | Modern SCM → Git |
| Project repository | `https://github.com/toqaosama/IvolveT.git` |
| Library Path | `lab-22/shared-library` |

### 5. Credentials

| ID | Type | Purpose |
|---|---|---|
| `docker` | Username with password | Docker Hub user + access token |
| `kubeconfig` | Secret file | kubeconfig of the Minikube cluster (from the agent: `kubectl config view --raw --flatten`) |

### 6. Create the pipeline job

**New Item → Pipeline → Pipeline script from SCM**

- Repository: `https://github.com/toqaosama/IvolveT.git`, branch `main`
- Script Path: `lab-22/Jenkinsfile`

Click **Build Now**.

### 7. Verify

```bash
kubectl get deploy,pods,svc -n ivolve
minikube service demoapp-svc -n ivolve --url
curl <url>        # Hello from Dockerized Spring Boot!
```

## Screenshots

- Agent connected: `screenshots/agent-connected.png`
  (screenshots/agent-connected.png)
- Shared library configuration: `screenshots/shared-lib.png`
- Pipeline stages: `screenshots/pipeline-stages.png`
- Docker Hub image: `screenshots/dockerhub.png`
- Pods running: `screenshots/pods.png`
