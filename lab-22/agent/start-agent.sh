#!/usr/bin/env bash
# Start a Jenkins inbound (JNLP) agent on this machine (WSL Ubuntu).
# The agent needs: java 17+, maven, docker, trivy, kubectl, git.
#
# Usage:
#   export JENKINS_URL=http://localhost:8080
#   export AGENT_NAME=slave
#   export AGENT_SECRET=<secret from Manage Jenkins > Nodes > slave>
#   ./start-agent.sh
set -euo pipefail

JENKINS_URL="${JENKINS_URL:-http://localhost:8080}"
AGENT_NAME="${AGENT_NAME:-slave}"
WORK_DIR="${WORK_DIR:-$HOME/jenkins-agent}"

: "${AGENT_SECRET:?Set AGENT_SECRET to the secret shown on the node page in Jenkins}"

mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

if [ ! -f agent.jar ]; then
  curl -fsSL -o agent.jar "$JENKINS_URL/jnlpJars/agent.jar"
fi

exec java -jar agent.jar \
  -url "$JENKINS_URL" \
  -secret "$AGENT_SECRET" \
  -name "$AGENT_NAME" \
  -webSocket \
  -workDir "$WORK_DIR"
