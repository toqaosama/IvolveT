// BuildApp: run unit tests, then build the application JAR with Maven.
// Usage: BuildApp(appDir: 'lab-22/DemoApp')
def call(Map config = [:]) {
    def appDir = config.appDir ?: '.'

    dir(appDir) {
        stage('Unit Test') {
            sh 'mvn -B test'
        }
        stage('Build App') {
            sh 'mvn -B clean package -DskipTests'
            sh 'ls -l target/*.jar'
        }
    }
}
