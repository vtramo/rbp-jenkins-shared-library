def call(Map params = [:]) {
    def serviceName = params.serviceName
    def skipPerformanceTests = (params.skipPerformanceTests == true || params.skipPerformanceTests == "true")
    def nodeLabel = params.nodeLabel
    def rbpServiceHostname = params.rbpServiceHostname
    def rbpServicePort = params.rbpServicePort

    node("${nodeLabel}") {
        unstash 'rbp'

        def rbpServiceMainDir = "${serviceName}"
        def rbpServiceCiDir = "${serviceName}/ci"

        try {
            stage("[${serviceName}] Build") {
                timeout(time: 2, unit: 'MINUTES') {
                    dir("${rbpServiceMainDir}") {
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
                timeout(time: 2, unit: 'MINUTES') {
                    dir("${rbpServiceMainDir}") {
                        sh """
                            docker build --build-arg BUILD_NUMBER=${BUILD_NUMBER} \
                                --build-arg BUILD_TAG=${BUILD_TAG} \
                                --build-arg GIT_COMMIT=${GIT_COMMIT} \
                                -t ${DOCKER_REGISTRY_URL}/rbp-${serviceName}:${GIT_SHORT_COMMIT} .
                        """
                    }
                }
            }

            stage("[${serviceName}] Performance Tests") {
                if (skipPerformanceTests == true) {
                    echo "[${serviceName}] Skip Performance Tests Stage"
                } else {
                    env.RBP_SERVICE_DOCKER_IMAGE_TAG = "${GIT_SHORT_COMMIT}"
                    env.RBP_SERVICE_HOSTNAME = "${rbpServiceHostname}"
                    env.RBP_SERVICE_PORT = "${rbpServicePort}"

                    timeout(time: 1, unit: 'MINUTES') {
                        dir("${rbpServiceCiDir}") {
                            sh 'docker compose -f docker-compose-test.yaml up -d --build --wait'
                            bzt """-o settings.env.JMETER_HOME=${JMETER_HOME} \
                                -o settings.env.RBP_SERVICE_HOSTNAME=${rbpServiceHostname} \
                                -o settings.env.RBP_SERVICE_PORT=${rbpServicePort} \
                                performance-test.yaml"""
                        }
                    }
                }
            }

            stage("[${serviceName}] Push Image") {
                if (currentBuild.result != null && currentBuild.result != 'SUCCESS') {
                    echo "[${serviceName}] Skip Push Image Stage, deleting image..."
                    sh "docker image rm --force ${DOCKER_REGISTRY_URL}/rbp-${serviceName}:${GIT_SHORT_COMMIT}"
                } else {
                    timeout(time: 1, unit: 'MINUTES') {
                        sh "docker push ${DOCKER_REGISTRY_URL}/rbp-${serviceName}:${GIT_SHORT_COMMIT}"
                    }
                }
            }
        } finally {
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
                    id: "${serviceName}-recordIssues", enabledForFailure: true, aggregatingResults: true,
                    tools: [
                        java(
                            id: "${serviceName}-java",
                            name: "[${serviceName}] Java compiler Warnings"),
                        junitParser(
                            id: "${serviceName}-unit-tests",
                            name: "[${serviceName}] Unit Test Warnings",
                            pattern: 'target/surefire-reports/**/*.xml'),
                        junitParser(
                            id: "${serviceName}-integration-tests",
                            name: "[${serviceName}] Integration Test Warnings",
                            pattern: 'target/failsafe-reports/**/*.xml')
                    ]
                )
            }

            timeout(time: 1, unit: 'MINUTES') {
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
