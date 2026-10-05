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

        stage('Backend Verify') {
            steps {
                dir('backend') {
                    sh 'mvn -B -ntp clean verify'
                }
                stash name: 'backend-jar', includes: 'backend/target/*.jar'
            }

            post {
                always {
                    junit testResults: 'backend/target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'backend/target/*.jar'
                }
            }
        }

        stage('Frontend Build') {
            when {
                changeset 'frontend/**'
            }

            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run build'
                }
            }
        }

        stage('Frontend Test') {
            when {
                changeset 'frontend/**'
            }

            steps {
                dir('frontend') {
                   // sh 'npm test'
                    echo 'Frontend tests are not configured yet.'
                }
            }
        }

        stage('Build Image') {
            steps {
                unstash 'backend-jar'
                sh 'docker build -t team-skeleton:${BUILD_NUMBER} .'
            }
        }
    }
}
