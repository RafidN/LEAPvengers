pipeline {
    agent any
    tools {
        maven 'Maven3'
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build Image') {
            steps {
                sh 'mvn -B clean package -DskipTests'
                sh 'docker build -t sprint1-demo-app:latest .'
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm sprint1-demo-app:latest'
            }
        }
    }
}
