pipeline {
               
    agent any
    tools {
        maven 'Maven3'
    }
    stages {
        stage('Checkout') {
            steps { checkout scm
            }
        }
        stage('Build Image') {
            steps {
                sh "docker build -t team-skeleton:${BUILD_NUMBER} ."
            }
        }
        stage('Test') {
            steps {
                sh 'mvn -B test -D  skipTests' // TODO: remove -DskipTests once real tests exist
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }   
    }
}
