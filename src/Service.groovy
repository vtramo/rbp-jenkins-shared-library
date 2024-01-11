class Service {
    static Map<String, Service> servicesByName = [
        'auth': new Service('auth'),
        'booking': new Service('booking'),
        'message': new Service('message'),
        'branding': new Service('branding'),
        'report': new Service('report'),
        'room': new Service('room')
    ]

    String name
    BuildStatus buildStatus

    Service(String name) {
        this.name = name
        this.buildStatus = BuildStatus.UNKNOWN
    }

    BuildStatus getBuildStatus() {
        return buildStatus
    }

    String getBuildStatusEnvVariableName() {
        return BuildUtilities.BUILD_STATUS_ENV_VAR_NAME_PREFIX + "_" + name.toUpperCase()
    }

    static setServiceBuildStatus(String serviceName, String buildStatus) {
        servicesByName.computeIfPresent(serviceName, (__, service) -> {
            service.setBuildStatus(BuildStatus.parseBuildStatus(buildStatus))
        })
    }
}