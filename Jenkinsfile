// LEAPvengers CI/CD.
//
// Every build: static checks, backend and frontend tests, then the three images (backend, frontend, market-data).
// Optionally: push the images to Amazon ECR, and deploy them to AWS (deploy/aws, see deploy/aws/README.md).
//
// Agent requirements: Linux with Docker (the build tools all run in containers, so no JDK, Maven or Node set-up is
// needed in Jenkins), and the AWS CLI v2 for pushing and deploying. The workspace is mounted into the build
// containers, so Docker must run on the agent itself (not a separate Docker host).
//
// AWS access: an IAM role on the agent, or Jenkins AWS credentials whose ID is set in AWS_CREDENTIALS_ID
// (needs the "CloudBees AWS Credentials" plugin).

def withAws(Closure body) {
    if (params.AWS_CREDENTIALS_ID?.trim()) {
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: params.AWS_CREDENTIALS_ID.trim()]]) {
            body()
        }
    } else {
        body()
    }
}

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 60, unit: 'MINUTES')
    }

    parameters {
        booleanParam(name: 'PUSH_IMAGES', defaultValue: false, description: 'Push the images to Amazon ECR')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Push and deploy this build to AWS (main branch only)')
        string(name: 'AWS_REGION', defaultValue: 'eu-west-2', description: 'Region the infrastructure stack is in')
        string(name: 'AWS_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins AWS credentials ID; empty uses the agent\'s IAM role')
    }

    environment {
        PROJECT_NAME = 'leapvengers'
        // Build tools, pinned so every build uses the same versions
        MAVEN_IMAGE = 'maven:3.9-eclipse-temurin-21'
        NODE_IMAGE = 'node:22-alpine'
        PLAYWRIGHT_IMAGE = 'mcr.microsoft.com/playwright:v1.64.1-jammy'
        PYTHON_IMAGE = 'python:3.12-slim'
        SHELLCHECK_IMAGE = 'koalaman/shellcheck:stable'
        // Run build containers as the Jenkins user, so files they write in the workspace can be cleaned up
        DOCKER_USER = sh(script: 'echo "$(id -u):$(id -g)"', returnStdout: true).trim()
    }

    stages {
        stage('Prepare') {
            steps {
                script {
                    // e.g. 3f9c2a1b7d4e-57: the commit, plus the build number so a rebuild never reuses a tag
                    env.IMAGE_TAG = "${sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()}-${env.BUILD_NUMBER}"
                    currentBuild.description = "Images ${env.IMAGE_TAG}"
                }
                sh 'mkdir -p .cache/m2 .cache/npm .cache/pip'
            }
        }

        stage('Checks and tests') {
            parallel {
                stage('Static checks') {
                    steps {
                        // Shell scripts that run in the containers and the deploy scripts
                        sh '''
                            docker run --rm -v "$WORKSPACE":/mnt -w /mnt "$SHELLCHECK_IMAGE" \
                                -x scripts/docker-entrypoint.sh frontend/nginx/40-api-proxy.sh deploy/aws/*.sh
                        '''
                        // Python scripts compile, CloudFormation templates are valid
                        sh '''
                            docker run --rm -u "$DOCKER_USER" -e HOME=/tmp -e PIP_CACHE_DIR=/ws/.cache/pip \
                                -v "$WORKSPACE":/ws -w /ws "$PYTHON_IMAGE" sh -c '
                                    python -m py_compile scripts/*.py &&
                                    pip install --quiet --user cfn-lint &&
                                    /tmp/.local/bin/cfn-lint deploy/aws/infra.yml deploy/aws/services.yml'
                        '''
                    }
                }

                stage('Backend tests') {
                    steps {
                        // Unit and integration tests; the integration tests use an in-memory database
                        sh '''
                            docker run --rm -u "$DOCKER_USER" \
                                -v "$WORKSPACE/backend":/src -v "$WORKSPACE/.cache/m2":/var/maven/.m2 \
                                -e MAVEN_CONFIG=/var/maven/.m2 -w /src "$MAVEN_IMAGE" \
                                mvn -B -ntp -Duser.home=/var/maven clean verify
                        '''
                    }
                    post {
                        always {
                            junit testResults: 'backend/target/surefire-reports/*.xml', allowEmptyResults: true
                        }
                    }
                }

                stage('Frontend tests and build') {
                    steps {
                        sh '''
                            docker run --rm -u "$DOCKER_USER" -e HOME=/tmp -e CI=true -e NG_CLI_ANALYTICS=false \
                                -e npm_config_cache=/ws/.cache/npm -v "$WORKSPACE":/ws -w /ws/frontend "$NODE_IMAGE" sh -c '
                                    npm ci --no-audit --no-fund &&
                                    npx ng test --watch=false &&
                                    npx ng build --configuration production'
                        '''
                        sh '''
                            docker run --rm -u "$DOCKER_USER" -e HOME=/tmp -e CI=true -e NG_CLI_ANALYTICS=false \
                                -e npm_config_cache=/ws/.cache/npm -v "$WORKSPACE":/ws -w /ws/frontend "$PLAYWRIGHT_IMAGE" sh -c '
                                    npm ci --no-audit --no-fund &&
                                    npx playwright test'
                        '''
                    }
                }
            }
        }

        stage('Build images') {
            steps {
                sh '''
                    docker build --pull -t "leap-backend:$IMAGE_TAG" backend
                    docker build --pull -t "leap-frontend:$IMAGE_TAG" frontend
                    docker build --pull -t "leap-market-data:$IMAGE_TAG" -f scripts/Dockerfile .
                '''
            }
        }

        stage('Push to ECR') {
            when {
                expression { params.PUSH_IMAGES || params.DEPLOY }
            }
            steps {
                script {
                    withAws {
                        sh 'AWS_REGION="$AWS_REGION" SKIP_BUILD=true deploy/aws/push-images.sh "$IMAGE_TAG"'
                    }
                }
            }
        }

        stage('Deploy to AWS') {
            when {
                allOf {
                    expression { params.DEPLOY }
                    // Multibranch jobs only deploy main; a single-branch job has no BRANCH_NAME and deploys what it builds
                    expression { env.BRANCH_NAME == null || env.BRANCH_NAME == 'main' }
                }
            }
            steps {
                script {
                    withAws {
                        sh 'AWS_REGION="$AWS_REGION" deploy/aws/deploy.sh "$IMAGE_TAG"'
                    }
                }
            }
        }
    }

    post {
        always {
            // Local copies of the images; the pushed ones are in ECR
            sh '''
                docker image rm "leap-backend:$IMAGE_TAG" "leap-frontend:$IMAGE_TAG" "leap-market-data:$IMAGE_TAG" 2>/dev/null || true
                docker image prune --force --filter "until=72h" > /dev/null || true
            '''
        }
        success {
            echo "Built images ${env.IMAGE_TAG}${params.DEPLOY ? ' and deployed them' : ''}"
        }
    }
}
