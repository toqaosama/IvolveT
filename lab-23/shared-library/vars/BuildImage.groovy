// BuildImage: build the Docker image, scan it with Trivy, push it to Docker Hub,
// then delete the local copy.
// Usage: BuildImage(image: 'user/demoapp:5', appDir: 'lab-22/DemoApp', credentialsId: 'docker')
//        credentialsId: '' (or null) -> push without docker login (local registry, e.g. localhost:5000)
def call(Map config = [:]) {
    def image         = config.image
    def appDir        = config.appDir ?: '.'
    def credentialsId = config.containsKey('credentialsId') ? config.credentialsId : 'docker'

    if (!image) {
        error 'BuildImage: "image" is required'
    }

    stage('Build Image') {
        dir(appDir) {
            sh "docker build -t ${image} ."
        }
    }

    stage('Scan Image') {
        // --exit-code 0: report HIGH/CRITICAL findings without failing the build
        sh "trivy image --severity HIGH,CRITICAL --exit-code 0 --no-progress ${image}"
    }

    stage('Push Image') {
        if (credentialsId) {
            withCredentials([usernamePassword(credentialsId: credentialsId,
                                              usernameVariable: 'DOCKER_USER',
                                              passwordVariable: 'DOCKER_PASS')]) {
                sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
                sh "docker push ${image}"
                sh 'docker logout'
            }
        } else {
            // local registry: no login needed
            sh "docker push ${image}"
        }
    }

    stage('Delete Local Image') {
        sh "docker rmi ${image} || true"
    }
}
