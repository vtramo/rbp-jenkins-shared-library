def call(Map params = [:]) {
    def serviceName = params.serviceName
    def skipPerformanceTests = (params.skipPerformanceTests == true || params.skipPerformanceTests == "true")
    def workspace = params.workspace
    def nodeLabel = params.nodeLabel

    node("${nodeLabel}") {
        Service.setServiceBuildStatus(serviceName, "UNKNOWN")

        unstash 'rbp'

        def rbpServiceMainDir = "${serviceName}"
        def rbpServiceCiDir = "${serviceName}/ci"

        try {
            stage("[${serviceName}] Build") {
                timeout(time: 3, unit: 'MINUTES') {
                    dir("${rbpServiceMainDir}") {
                        sh 'ls'
                        sh 'mvn clean package -DskipTests'
                    }
                }
            }

            stage("[${serviceName}] Unit Tests") {
                timeout(time: 20, unit: 'SECONDS') {
                    dir("${rbpServiceMainDir}") {
                        sh 'mvn test'
                    }
                }
            }

            stage("[${serviceName}] Integration Tests") {
                timeout(time: 40, unit: 'SECONDS') {
                    dir("${rbpServiceMainDir}") {
                        sh 'mvn verify -Dskip.surefire.tests=true'
                    }
                }
            }

            stage("[${serviceName}] SonarQube Scan") {
                timeout(time: 1, unit: 'MINUTES') {
                    dir("${rbpServiceMainDir}") {
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
                timeout(time: 1, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }

            stage("[${serviceName}] Build Image") {
                timeout(time: 30, unit: 'SECONDS') {
                    dir("${rbpServiceMainDir}") {
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
                if (skipPerformanceTests == true) {
                    echo "[${serviceName}] Skip Performance Tests Stage"
                } else {
                    environment {
                        RBP_SERVICE_HOSTNAME = 'rbp-auth'
                        RBP_SERVICE_PORT = '3004'
                        RBP_SERVICE_DOCKER_IMAGE_TAG = "${GIT_SHORT_COMMIT}"
                    }


                    timeout(time: 1, unit: 'MINUTES') {
                        dir("${rbpServiceCiDir}") {
                            sh 'docker compose -f docker-compose-test.yaml up -d --build --wait'
                            bzt """-o settings.env.JMETER_HOME=${JMETER_HOME} \
                        -o settings.env.RBP_SERVICE_HOSTNAME=${RBP_SERVICE_HOSTNAME} \
                        -o settings.env.RBP_SERVICE_PORT=${RBP_SERVICE_PORT} \
                        performance-test.yaml"""
                        }
                    }
                }
            }

            stage("[${serviceName}] Push Image") {
                if (currentBuild.result != null && currentBuild.result != 'SUCCESS') {
                    echo "[${serviceName}] Skip Push Image Stage"
                } else {
                    timeout(time: 30, unit: 'SECONDS') {
                        sh 'docker push ${DOCKER_REGISTRY_URL}/rbp-auth:${GIT_SHORT_COMMIT}'
                    }
                }
            }
        } finally {
            Service.setServiceBuildStatus(serviceName, currentBuild.currentResult)
            echo currentBuild.result
            echo ${currentBuild.currentResult}
            echo currentBuild.currentResult
            echo Service.getBuildStatus(serviceName).toString()

            dir("${rbpServiceMainDir}") {
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
                    enabledForFailure: true, aggregatingResults: false,
                    tools: [
                            java(),
                            junitParser(name: 'Unit Test Warnings',
                                    pattern: 'target/surefire-reports/**/*.xml'),
                            junitParser(name: 'Integration Test Warnings',
                                    pattern: 'target/failsafe-reports/**/*.xml')
                    ]
                )
            }

            timeout(time: 30, unit: 'SECONDS') {
                dir("${rbpServiceCiDir}") {
                    sh '''
                    docker compose -f docker-compose-test.yaml logs && \
                    docker compose -f docker-compose-test.yaml down --volumes || :
                '''
                }
            }
        }

    }
}
