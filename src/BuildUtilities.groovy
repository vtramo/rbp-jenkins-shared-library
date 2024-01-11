class BuildUtilities {
    static String BUILD_STATUS_ENV_VAR_NAME_PREFIX = "BUILD_STATUS"

    static List<List<String>> groupServicesByBuildStatus() {
        def passedServiceBuilds = []
        def failedServiceBuilds = []
        def abortedServiceBuilds = []
        def unstableServiceBuilds = []

        Service.servicesByName.each { serviceName, service ->
            def buildStatus = service.getBuildStatus()
            switch (buildStatus) {
                case BuildStatus.SUCCESS:
                    passedServiceBuilds.add(serviceName)
                    break
                case BuildStatus.FAILURE:
                    failedServiceBuilds.add(serviceName)
                    break
                case BuildStatus.UNSTABLE:
                    unstableServiceBuilds.add(serviceName)
                    break
                case BuildStatus.ABORTED:
                    abortedServiceBuilds.add(service)
                    break
                case BuildStatus.UNKNOWN:
                    break
            }
        }

        return [passedServiceBuilds, failedServiceBuilds, abortedServiceBuilds, unstableServiceBuilds]
    }
}
