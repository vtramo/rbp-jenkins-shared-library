import Main

def call(Map params = [:]) {
    def serviceName = params.serviceName
    def serviceMainDir  = params.serviceMainDir
    def serviceCIDir = params.serviceCIDir
    def dockerRegistryURL = params.dockerRegistryURL
    def dockerImageName = params.dockerImageName

    stage("${serviceName} Service") {
        when {
            anyOf {
                changeset "${serviceName}/Dockerfile"
                changeset "${serviceName}/src/main/java/**/*.java"
                changeset "${serviceName}/src/test/java/**/*.java"
            }

            environment {
                RBP_SERVICE_MAIN_DIR = "${serviceName}"
                RBP_SERVICE_CI_DIR = "${serviceName}/ci"
            }

            stages {
                stage("[${serviceName}] Build") {
                    options {
                        timeout(time: 3, unit: 'MINUTES')
                    }

                    steps {
                        dir("${RBP_SERVICE_MAIN_DIR}") {
                            sh 'mvn clean package -DskipTests'
                        }
                    }
                }

                stage("[${serviceName}] Unit Tests") {
                    options {
                        timeout(time: 20, unit: 'SECONDS')
                    }

                    steps {
                        dir("${RBP_SERVICE_MAIN_DIR}") {
                            sh 'mvn test'
                        }
                    }
                }

                stage("[${serviceName}] Integration Tests") {
                    options {
                        timeout(time: 40, unit: 'SECONDS')
                    }

                    steps {
                        dir("${RBP_SERVICE_MAIN_DIR}") {
                            sh 'mvn verify -Dskip.surefire.tests=true'
                        }
                    }
                }

                stage("[${serviceName}] SonarQube Scan") {
                    options {
                        timeout(time: 1, unit: 'MINUTES')
                    }

                    steps {
                        dir("${RBP_SERVICE_MAIN_DIR}") {
                            withSonarQubeEnv(installationName: 'sonarqube') {
                                sh """
                                    mvn sonar:sonar \
                                        -Dsonar.projectKey=restful-booker-platform-${serviceName} \
                                        -Dsonar.projectName=restful-booker-platform-${serviceName} \
                                """
                            }
                        }
                    }
                }

                stage("[${serviceName}] Quality Gates") {
                    steps {
                        timeout(time: 1, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }

                stage("[${serviceName}] Build Image") {
                    options {
                        timeout(time: 30, unit: 'SECONDS')
                    }

                    steps {
                        dir("${RBP_SERVICE_MAIN_DIR}") {
                            sh '''
                                docker build \
                                    --build-arg BUILD_NUMBER=${BUILD_NUMBER} \
                                    --build-arg BUILD_TAG=${BUILD_TAG} \
                                    --build-arg GIT_COMMIT=${GIT_COMMIT} \
                                    -t ${DOCKER_REGISTRY_URL}/rbp-auth:${GIT_SHORT_COMMIT} .
                            '''
                        }
                    }
                }

                stage("[${serviceName}] Performance Tests") {
                    options {
                        timeout(time: 1, unit: 'MINUTES')
                    }

                    environment {
                        RBP_SERVICE_HOSTNAME = 'rbp-auth'
                        RBP_SERVICE_PORT = '3004'
                        RBP_SERVICE_DOCKER_IMAGE_TAG = "${GIT_SHORT_COMMIT}"
                    }

                    steps {
                        dir("${RBP_AUTH_SERVICE_CI_DIR}") {
                            sh 'docker compose -f docker-compose-test.yaml up -d --build --wait'
                            bzt """-o settings.env.JMETER_HOME=${JMETER_HOME} \
                                -o settings.env.RBP_SERVICE_HOSTNAME=${RBP_SERVICE_HOSTNAME} \
                                -o settings.env.RBP_SERVICE_PORT=${RBP_SERVICE_PORT} \
                                performance-test.yaml"""
                        }
                    }
                }

                stage("[${serviceName}] Push Image") {
                    when {
                        expression {
                            currentBuild.result == null || currentBuild.result == 'SUCCESS'
                        }
                    }

                    options {
                        timeout(time: 30, unit: 'SECONDS')
                    }

                    steps {
                        sh 'docker push ${DOCKER_REGISTRY_URL}/rbp-auth:${GIT_SHORT_COMMIT}'
                    }
                }

                post {
                    always {
                        dir("${RBP_SERVICE_MAIN_DIR}") {
                            junit(
                                testResults: 'target/surefire-reports/**/*.xml,target/failsafe-reports/**/*.xml',
                                allowEmptyResults: true
                            )
                            jacoco(
                                execPattern: 'target/**/*.exec',
                                classPattern: 'target/classes/com/rbp',
                                sourcePattern: 'src/main/java/com/rbp'
                            )
                            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                            recordIssues(
                                enabledForFailure: true, aggregatingResults: true,
                                tools: [
                                    java(),
                                    junitParser(name: 'Unit Test Warnings',
                                        pattern: 'target/surefire-reports/**/*.xml'),
                                    junitParser(name: 'Integration Test Warnings',
                                        pattern: 'target/failsafe-reports/**/*.xml')
                                ]
                            )
                        }

                        dir("${RBP_SERVICE_CI_DIR}") {
                            sh '''
                                docker compose -f docker-compose-test.yaml logs && \
                                docker compose -f docker-compose-test.yaml down --volumes
                            '''
                        }

                        rbpSendSlackNotification ${serviceName}
                    }
                }
            }
        }
    }
}