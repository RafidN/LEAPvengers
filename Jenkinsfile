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

        stage('Database Setup') {
            steps {
                sh '''
                    # Start PostgreSQL container
                    docker run -d --name leapdb \
                      -e POSTGRES_PASSWORD=n3u3d4! \
                      -p 5432:5432 \
                      postgres:15
                    
                    # Wait for it to be ready
                    sleep 5
                    
                    # Create and seed database
                    docker exec leapdb psql -U postgres -c "CREATE DATABASE leapvengersdb;"
                    docker cp database/enterprise-schema.sql leapdb:/schema.sql
                    docker exec leapdb psql -U postgres -d leapvengersdb -f /schema.sql
                    docker cp database/seed.sql leapdb:/seed.sql
                    docker exec leapdb psql -U postgres -d leapvengersdb -f /seed.sql
                '''
            }

            post {
                always {
                    sh 'docker rm -f leapdb || true'
                }
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

        stage('Build Image') {
            steps {
                unstash 'backend-jar'
                sh 'docker build -t team-skeleton:${BUILD_NUMBER} .'
            }
        }
    }
}
