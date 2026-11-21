// DeployOnK8s: put the new image tag in the deployment file and apply it to the cluster.
// Usage: DeployOnK8s(image: 'user/demoapp:5', manifest: 'lab-22/deployment.yaml',
//                    deployment: 'demoapp', namespace: 'ivolve', kubeconfigId: 'kubeconfig')
def call(Map config = [:]) {
    def image        = config.image
    def manifest     = config.manifest ?: 'deployment.yaml'
    def deployment   = config.deployment ?: 'demoapp'
    def namespace    = config.namespace ?: 'ivolve'
    def kubeconfigId = config.kubeconfigId ?: 'kubeconfig'

    if (!image) {
        error 'DeployOnK8s: "image" is required'
    }

    stage('Edit Deployment File') {
        sh "sed -i 's|image: .*|image: ${image}|' ${manifest}"
        sh "grep 'image:' ${manifest}"
    }

    stage('Deploy to Cluster') {
        withCredentials([file(credentialsId: kubeconfigId, variable: 'KUBECONFIG')]) {
            sh "kubectl apply -f ${manifest}"
            sh "kubectl rollout status deployment/${deployment} -n ${namespace} --timeout=180s"
            sh "kubectl get pods -n ${namespace} -o wide"
        }
    }
}
