# Lab 18: Node-Wide Pod Management with DaemonSet and Monitoring Stack
 
## Objective
 
1. Create a `monitoring` namespace.
2. Deploy **Prometheus**, **Alertmanager**, **Grafana**, and the Prometheus **node-exporter DaemonSet** in the `monitoring` namespace.
3. Validate that a node-exporter pod is scheduled on **every node**.
4. Confirm that metrics are exposed at `<node_IP>:9100/metrics`.
5. Make sure Prometheus collects data from node-exporter.
6. Open the Kubernetes dashboard in Grafana.
## Concepts
 
| Component | Purpose |
|-----------|---------|
| **DaemonSet** | Runs exactly one copy of a pod on every node (or on every node that matches a selector). Used here for node-exporter. |
| **Prometheus** | Scrapes and stores metrics. |
| **Alertmanager** | Handles alerts sent by Prometheus. |
| **Grafana** | Visualizes metrics in dashboards. |
| **node-exporter** | Exposes node-level metrics (CPU, memory, disk, network) on port `9100`. |
| **kube-prometheus-stack** | A Helm chart that installs all of the above and wires them together. |
 
> The `kube-prometheus-stack` chart creates a `ServiceMonitor` for node-exporter, so Prometheus is configured to scrape it automatically.
 
## Environment
 
| Item | Value |
|------|-------|
| Namespace | `monitoring` |
| Nodes | `minikube` (`192.168.49.2`), `minikube-m02` (`192.168.49.3`) |
| Helm release name | `prometheus` |
| Chart | `prometheus-community/kube-prometheus-stack` |
| node-exporter image | `quay.io/prometheus/node-exporter:v1.12.1-distroless` |
 
---
 
## Steps
 
### 1. Create the monitoring namespace
 
Create the `monitoring` namespace to isolate the monitoring resources:
 
```bash
kubectl create namespace monitoring
```
 
### 2. Deploy the monitoring stack with Helm
 
Add the `prometheus-community` Helm repository and update the chart indexes:
 
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts && helm repo update
```
 
Install the `kube-prometheus-stack` chart. It deploys Prometheus, Alertmanager, Grafana, and the Prometheus node-exporter DaemonSet:
 
```bash
helm install prometheus prometheus-community/kube-prometheus-stack -n monitoring
```
 
Verify that all the stack components are running:
 
```bash
kubectl get pods -n monitoring
```
 
```text
NAME                                                         READY   STATUS    RESTARTS   AGE
alertmanager-prometheus-kube-prometheus-alertmanager-0       2/2     Running   0          4h
prometheus-grafana-9f5db4bbb-5ndcp                           3/3     Running   0          4h
prometheus-kube-prometheus-operator-785b55c6b9-mt6jm         1/1     Running   0          4h
prometheus-kube-state-metrics-599975f55f-wsdn4               1/1     Running   1          4h
prometheus-prometheus-kube-prometheus-prometheus-0           2/2     Running   0          4h
prometheus-prometheus-node-exporter-sgslb                    1/1     Running   0          4h
prometheus-prometheus-node-exporter-zngdv                    1/1     Running   0          4h
```
 
---
 
## Verification
 
### 1. Validate the node-exporter DaemonSet
 
Verify that the `prometheus-prometheus-node-exporter` DaemonSet is created and healthy:
 
```bash
kubectl get daemonset prometheus-prometheus-node-exporter -n monitoring -o wide
```
 
```text
NAME                                  DESIRED   CURRENT   READY   UP-TO-DATE   AVAILABLE   NODE SELECTOR            AGE   CONTAINERS      IMAGES                                                SELECTOR
prometheus-prometheus-node-exporter   2         2         2       2            2           kubernetes.io/os=linux   4h    node-exporter   quay.io/prometheus/node-exporter:v1.12.1-distroless   app.kubernetes.io/instance=prometheus,app.kubernetes.io/name=prometheus-node-exporter
```
 
`DESIRED`, `CURRENT`, and `READY` are all `2`, which matches the number of nodes in the cluster.
 
### 2. Verify that a node-exporter pod runs on every node
 
```bash
kubectl get pods -n monitoring -l app.kubernetes.io/name=prometheus-node-exporter -o wide
```
 
```text
NAME                                        READY   STATUS    RESTARTS   AGE   IP             NODE           NOMINATED NODE   READINESS GATES
prometheus-prometheus-node-exporter-sgslb   1/1     Running   0          4h    192.168.49.3   minikube-m02   <none>           <none>
prometheus-prometheus-node-exporter-zngdv   1/1     Running   0          4h    192.168.49.2   minikube       <none>           <none>
```
 
There is one pod on `minikube` and one on `minikube-m02`. node-exporter uses the host network, so each pod IP is the node IP.
 
### 3. Confirm metrics exposure at `<node_IP>:9100/metrics`
 
Query the node-exporter endpoint of the node `192.168.49.2` from the `network` diagnostic pod:
 
```bash
kubectl exec -it -n ivolve network -- curl -s http://192.168.49.2:9100/metrics | head -n 10
```
 
```text
# HELP go_gc_duration_seconds A summary of the wall-time pause (stop-the-world) duration in garbage collection cycles.
# TYPE go_gc_duration_seconds summary
go_gc_duration_seconds{quantile="0"} 4.7913e-05
go_gc_duration_seconds{quantile="0.25"} 7.1724e-05
go_gc_duration_seconds{quantile="0.5"} 9.5395e-05
go_gc_duration_seconds{quantile="0.75"} 0.000140165
go_gc_duration_seconds{quantile="1"} 0.078059419
go_gc_duration_seconds_sum 2.514419641
go_gc_duration_seconds_count 1219
# HELP go_gc_gogc_percent Heap size target percentage configured by the user, otherwise 100. This value is set by the GOGC environment variable, and the runtime/debug.SetGCPercent function. Sourced from /gc/gogc:percent.
# TYPE go_gc_gogc_percent gauge
```
 
The endpoint returns metrics in the Prometheus text format, so node-exporter exposes its metrics correctly.
 
### 4. Verify that Prometheus collects data from node-exporter (optional check)
 
The chart configures Prometheus to scrape node-exporter automatically through a `ServiceMonitor`. To confirm it, port-forward Prometheus:
 
```bash
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090
```
 
Open `http://localhost:9090`, go to **Status > Targets**, and check that the `node-exporter` targets are `UP` (one per node).
 
---
 
## Access the Grafana Dashboard for Kubernetes
 
Retrieve the default admin password for Grafana:
 
```bash
kubectl get secret -n monitoring prometheus-grafana -o jsonpath="{.data.admin-password}" | base64 --decode && echo ""
```
 
Port-forward the Grafana Service to access the UI locally:
 
```bash
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
```
 
Open Grafana at `http://localhost:3000` (user: `admin`), go to **Dashboards > K8S Dashboard**, and observe the cluster, node, and workload metrics.
 
![K8S Dashboard in Grafana](images/grafana-k8s-dashboard.png)
 
---
 
## Result
 
| Check | Status |
|-------|--------|
| `monitoring` namespace created | Done |
| Prometheus, Alertmanager, Grafana deployed | Done |
| node-exporter DaemonSet deployed (`2/2` ready) | Done |
| One node-exporter pod on each node | Done |
| Metrics exposed at `<node_IP>:9100/metrics` | Done |
| Prometheus configured to scrape node-exporter | Configured by the chart (confirm in Status > Targets) |
| Grafana Kubernetes dashboard accessible | Done |
 
