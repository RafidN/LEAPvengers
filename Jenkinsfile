pipeline {
    agent any

    tools {
        maven 'Maven3'
        nodejs 'NodeJS20'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Database Setup') {
            steps {
                sh 'docker-compose down -v || true'
                sh 'docker-compose up -d leapdb'
                sh 'sleep 10 && docker-compose exec -T leapdb pg_isready -U postgres'
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
                    sh 'npm test'
                }
            }
        }

        stage('Code Analysis') {
            environment {
                scannerHome = tool 'LeapVengersSonar'
                SONAR_TOKEN = credentials('jenkins-sonar')
            }
            steps {
                sh 'docker-compose up -d leapsonar'
                sh '''
                    for i in {1..10}; do
                        if curl -f -s -H "Authorization: Bearer ${SONAR_TOKEN}" http://localhost:9000/api/v2/analysis/version > /dev/null 2>&1; then
                            echo "SonarQube API is ready"
                            exit 0
                        fi
                        echo "Attempt $i: SonarQube not ready, retrying in 5s..."
                        sleep 5
                    done
                    echo "SonarQube failed to become ready"
                    exit 1
                '''
                script {
                    sh '''${scannerHome}/bin/sonar-scanner \
                        -Dsonar.projectKey=leap \
                        -Dsonar.projectName=LEAP \
                        -Dsonar.projectVersion=${BUILD_NUMBER} \
                        -Dsonar.sources=./backend/src,./frontend/src,./scripts \
                        -Dsonar.host.url=http://localhost:9000 \
                        -Dsonar.token=${SONAR_TOKEN}'''
                }
            }
        }

        stage('Build Image') {
            steps {
                unstash 'backend-jar'
                sh 'docker build -t team-skeleton:${BUILD_NUMBER} .'
            }
        }

        stage('Cleanup') {
            steps {
                sh 'docker-compose down -v || true'
            }
        }
    }

    post {
        always {
            sh 'docker rm -f leapdb || true'
            sh 'docker rm -f LeapVengersSonar || true'
        }
    }
}
