# Lab 21 – Jenkins CI/CD Pipeline for iVolveApp

< p></p>
## Project Overview

This lab demonstrates how to build and automate a CI/CD pipeline using Jenkins to build, scan, publish, and deploy a containerized Node.js application to Kubernetes.

The pipeline automates the application delivery process, from source code checkout to Kubernetes deployment.

**Final Result:** Successfully completed the CI/CD pipeline and deployed iVolveApp to Kubernetes.

## Technologies & Tools

- **Jenkins** – CI/CD automation
- **Git & GitHub** – Source code management
- **Node.js & npm** – Application runtime and dependency management
- **Docker** – Containerization
- **Docker Hub** – Container image registry
- **Trivy** – Container image vulnerability scanning
- **Kubernetes (Minikube)** – Container orchestration
- **kubectl** – Kubernetes command-line tool
- **YAML** – Kubernetes deployment configuration

## Application Structure

```text
lab-21/
└── iVolveApp/
    ├── Jenkinsfile
    ├── Dockerfile
    ├── package.json
    ├── package-lock.json
    ├── server.js
    ├── deployment.yaml
    └── README.md
```

## CI/CD Pipeline Workflow

The Jenkins pipeline consists of the following stages:

| Stage | Description |
|---|---|
| 1. Checkout SCM | Retrieves the source code and Jenkinsfile from GitHub. |
| 2. Build App | Installs Node.js application dependencies using `npm install`. |
| 3. Build Docker Image | Builds a Docker image for the application. |
| 4. Scan Image with Trivy | Scans the Docker image for HIGH and CRITICAL vulnerabilities. |
| 5. Push to Docker Hub | Authenticates using Jenkins credentials and pushes the image to Docker Hub. |
| 6. Delete Local Image | Removes the local Docker image after pushing it. |
| 7. Update Deployment Manifest | Updates the Kubernetes manifest with the current build image tag. |
| 8. Deploy to Kubernetes | Applies the deployment manifest and checks the rollout status. |

## Pipeline Configuration

The Jenkinsfile uses environment variables to manage the Docker image:

```groovy
environment {
    IMAGE_NAME = "toqaosama/ivolveapp"
    IMAGE_TAG = "${BUILD_NUMBER}"
    FULL_IMAGE = "${IMAGE_NAME}:${IMAGE_TAG}"
}
```

Each Jenkins build generates a unique image tag based on the build number.

### Security & Credentials

Sensitive credentials are managed through Jenkins Credentials instead of hardcoding them in the Jenkinsfile.

- `docker` – Docker Hub username and access token.
- `kubeconfig` – Kubernetes configuration file used to authenticate with the cluster.

### Kubernetes Deployment

The application is deployed in the `ivolve` namespace with:

- 2 application replicas.
- Container port `3000`.
- Readiness probe using `/health`.
- Liveness probe using `/health`.
- Kubernetes Service for internal application access.

## Application Endpoints

| Endpoint | Purpose |
|---|---|
| `/` | Returns the application welcome message. |
| `/health` | Returns the application health status. |

Example health response:

```json
{
  "status": "healthy",
  "application": "iVolveApp"
}
```

## Screenshots

Add your screenshots here to document the successful execution of the lab.

### 1. Jenkins Pipeline – Successful Build

![Jenkins Pipeline Success](screenshots/jenkins-pipeline-success.png)

### 2. Jenkins Pipeline Stages

![Jenkins Pipeline Stages](screenshots/jenkins-pipeline-stages.png)

### 3. Docker Hub Image

![Docker Hub Image](screenshots/dockerhub-image.png)

### 4. Kubernetes Deployment

![Kubernetes Deployment](screenshots/kubernetes-deployment.png)

### 5. Running Application

![iVolveApp](screenshots/application-running.png)

## Final Result

The lab was successfully completed!

The Jenkins pipeline automates the complete CI/CD workflow:

**GitHub → Jenkins → Build → Docker → Trivy Scan → Docker Hub → Kubernetes**

The application image is built, scanned, pushed to Docker Hub, and deployed to the Kubernetes cluster automatically.

This lab provided practical experience with CI/CD automation, containerization, image scanning, credential management, and Kubernetes deployment.

---

**Author:** Toqa Osama  
**GitHub Repository:** [iVolveT](https://github.com/toqaosama/IvolveT)  
**Project Directory:** `lab-21/iVolveApp`