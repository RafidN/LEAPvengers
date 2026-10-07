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
                sh '''
                    # Start services using docker-compose
                    docker compose down -v || true
                    docker compose up -d leapdb
                    
                    # Wait for PostgreSQL to be healthy (max 60 seconds)
                    timeout 60 bash -c 'until docker exec leapdb pg_isready -U postgres > /dev/null 2>&1; do sleep 2; done'
                    
                    # Create database if it doesn't exist
                    docker compose exec -T leapdb psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'leapvengersdb'" | grep -q 1 || \
                    docker compose exec -T leapdb psql -U postgres -c "CREATE DATABASE leapvengersdb;"
                    
                    # Apply schema and seed data
                    cat database/enterprise-schema.sql | docker compose exec -T leapdb psql -U postgres -d leapvengersdb
                    cat database/seed.sql | docker compose exec -T leapdb psql -U postgres -d leapvengersdb
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
                sh '''
                    # Start SonarQube if not running
                    docker compose up -d leapsonar
                    
                    # Wait for SonarQube to be ready (max 2 minutes)
                    timeout 120 bash -c 'until curl -s http://localhost:9000/api/system/health | grep -q "UP"; do sleep 5; done'
                '''
                script {
                    withSonarQubeEnv('LeapVengersSonar') {
                        sh '''${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=leap \
                            -Dsonar.projectName=LEAP \
                            -Dsonar.projectVersion=${BUILD_NUMBER} \
                            -Dsonar.sources=./backend/src,./frontend/src,./scripts \
                            -Dsonar.host.url=http://localhost:9000'''
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
                sh 'docker compose down -v || true'
            }
        }
    }

    post {
        always {
            sh 'docker rm -f leapdb || true'
        }
    }
}
