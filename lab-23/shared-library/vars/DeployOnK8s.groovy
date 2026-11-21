// DeployOnK8s (GitOps version): edit the deployment file with the new image,
// then commit and push it to GitHub. Argo CD sees the change in Git and syncs the cluster,
// so Jenkins no longer runs kubectl apply.
//
// Usage: DeployOnK8s(image: 'user/demoapp:7', manifest: 'lab-23/k8s/deployment.yaml',
//                    branch: 'main', gitCredentialsId: 'github')
def call(Map config = [:]) {
    def image            = config.image
    def manifest         = config.manifest ?: 'k8s/deployment.yaml'
    def branch           = config.branch ?: 'main'
    def gitCredentialsId = config.gitCredentialsId ?: 'github'
    def gitUser          = config.gitUser ?: 'jenkins'
    def gitEmail         = config.gitEmail ?: 'jenkins@ivolve.local'

    if (!image) {
        error 'DeployOnK8s: "image" is required'
    }

    stage('Edit Deployment File') {
        sh "sed -i 's|image: .*|image: ${image}|' ${manifest}"
        sh "grep 'image:' ${manifest}"
    }

    stage('Push Manifest to GitHub') {
        withCredentials([usernamePassword(credentialsId: gitCredentialsId,
                                          usernameVariable: 'GIT_USER',
                                          passwordVariable: 'GIT_TOKEN')]) {
            withEnv(["MANIFEST=${manifest}", "BRANCH=${branch}", "IMAGE=${image}",
                     "COMMIT_NAME=${gitUser}", "COMMIT_EMAIL=${gitEmail}"]) {
                sh '''
                    git config user.name  "$COMMIT_NAME"
                    git config user.email "$COMMIT_EMAIL"

                    git add "$MANIFEST"
                    if git diff --cached --quiet; then
                        echo "Manifest already up to date - nothing to push"
                        exit 0
                    fi

                    # [skip ci] stops this commit from triggering the pipeline again
                    git commit -m "Update image to $IMAGE [skip ci]"

                    REPO_URL=$(git config --get remote.origin.url | sed 's|https://||')
                    git push "https://$GIT_USER:$GIT_TOKEN@$REPO_URL" HEAD:"$BRANCH"
                '''
            }
        }
    }
}
