pipeline {
    agent any

    tools {
        maven 'Maven3'
        nodejs 'NodeJS20'
        sonarqube 'LeapVengersSonar'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Database Setup') {
            steps {
                sh '''
                    # Clean up any existing container
                    docker rm -f leapdb || true
                    
                    # Start PostgreSQL container with health check
                    docker run -d --name leapdb \
                      -e POSTGRES_PASSWORD=n3u3d4! \
                      -p 5432:5432 \
                      --health-cmd="pg_isready -U postgres" \
                      --health-interval=2s \
                      --health-retries=10 \
                      postgres:15
                    
                    # Wait for container to be healthy
                    timeout 30 bash -c 'until docker exec leapdb pg_isready -U postgres > /dev/null 2>&1; do sleep 1; done'
                    
                    # Create and seed database
                    docker exec leapdb psql -U postgres -c "CREATE DATABASE leapvengersdb;"
                    cat database/enterprise-schema.sql | docker exec -i leapdb psql -U postgres -d leapvengersdb
                    cat database/seed.sql | docker exec -i leapdb psql -U postgres -d leapvengersdb
                '''
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
            }
            steps {
                script {
                    withSonarQubeEnv('LeapVengersSonar') {
                        sh '''${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=leap \
                            -Dsonar.projectName=LEAP \
                            -Dsonar.projectVersion=${BUILD_NUMBER} \
                            -Dsonar.sources=./backend/src,./frontend/src,./scripts'''
                    }
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
                sh 'docker rm -f leapdb || true'
            }
        }
    }

    post {
        always {
            sh 'docker rm -f leapdb || true'
        }
    }
}
