# Lab 8: Containerized Node.js and MySQL Stack Using Docker Compose

## Overview

This lab demonstrates how to containerize a Node.js application and run it alongside a MySQL database using Docker Compose. It covers service orchestration, environment variables, persistent storage, custom networking, application health checks, and publishing a Docker image to Docker Hub.

## Objectives

- Containerize the Node.js application using Docker.
- Run the application and MySQL database as separate services.
- Configure database connectivity using environment variables.
- Persist application logs and MySQL data using Docker volumes.
- Create a custom Docker network named `ivolve-net`.
- Verify application health and database connectivity.
- Build, tag, and publish the application image to Docker Hub.

## Project Structure

```text
Lab8-Node-MySQL/
├── Dockerfile
├── docker-compose.yml
├── package.json
├── server.js
├── db.js
├── frontend/
│   ├── index.html
│   └── assets/
│       └── ivolve-logo.png
└── README.md
```

## Architecture

The application consists of two services:

- **App:** Node.js application running on port `3000`.
- **Database:** MySQL 8.0 database named `ivolve`.

Both services communicate over the custom Docker network `ivolve-net`.

Persistent volumes are used for database storage and application logs.

## Docker Compose Configuration

The `docker-compose.yml` file configures the following resources:

| Resource | Configuration |
|---|---|
| Application image | `toqaosama/ivolve-app:latest` |
| Application port | `3000:3000` |
| Database image | `mysql:8.0` |
| Database name | `ivolve` |
| Network | `ivolve-net` |
| Database volume | `db_data` mounted at `/var/lib/mysql` |
| Application logs | `app_logs` mounted at `/app/logs` |

Database credentials are supplied through environment variables. For local development, default values may be provided in the Compose file; production deployments should use securely managed credentials.

## Prerequisites

- Docker Engine or Docker Desktop
- Docker Compose v2
- Git
- A Docker Hub account for publishing the image

## Run the Application

### 1. Clone the Repository

```bash
git clone https://github.com/Ibrahim-Adel15/iVolveApp.git
cd iVolveApp
```

Alternatively, run the following commands from your existing `Lab8-Node-MySQL` directory.

### 2. Validate the Compose Configuration

```bash
docker compose config
```

### 3. Build and Start the Services

```bash
docker compose up -d --build
```

### 4. Check Service Status

```bash
docker compose ps
```

### 5. View Application and Database Logs

```bash
docker compose logs --tail=100 app
docker compose logs --tail=100 db
```

## Verify Application Functionality

Test the application health endpoint:

```bash
curl -i http://localhost:3000/health
```

Test the readiness endpoint:

```bash
curl -i http://localhost:3000/ready
```

The health endpoint should return a successful response. The readiness endpoint should confirm that the application is ready to serve requests after its database dependency is available.

You can also open the application in your browser:

http://localhost:3000

## Verify Docker Networking and Storage

Inspect the custom network:

```bash
docker network inspect ivolve-net
```

List Docker volumes:

```bash
docker volume ls
```

Inspect the database volume:

```bash
docker volume inspect lab8-node-mysql_db_data
```

Compose may prefix resource names with the project name. Use `docker volume ls` to confirm the exact names in your environment.

The database volume preserves MySQL data across container recreation. The application log volume stores logs separately from the container's writable layer.

## Build, Tag, and Push the Image

Log in to Docker Hub:

```bash
docker login
```

Build the application image:

```bash
docker build -t toqaosama/ivolve-app:latest .
```

Push the image to Docker Hub:

```bash
docker push toqaosama/ivolve-app:latest
```

Docker Hub repository:

https://hub.docker.com/r/toqaosama/ivolve-app

## Cleanup

Stop and remove the Compose containers and network:

```bash
docker compose down
```

To remove the persistent volumes as well, use the following command **only if you no longer need their data**:

```bash
docker compose down -v
```

## Troubleshooting

- **Application cannot connect to MySQL:** Check the database service logs, credentials, network configuration, and database name.
- **Application is not ready:** Review `docker compose logs app` and wait for MySQL initialization to complete.
- **Port 3000 is already in use:** Stop the process using the port or change the host-side port mapping.
- **Docker Hub push times out:** Retry the push and check your internet connection.
- **Volume name differs:** Run `docker volume ls` to identify the actual Compose-generated volume name.

## Learning Outcomes

By completing this lab, you practice Docker image building, multi-container orchestration, container networking, environment-based configuration, persistent storage, health verification, and Docker Hub image publishing.
