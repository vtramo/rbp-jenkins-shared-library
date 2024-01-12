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

    static BuildStatus getBuildStatus(String serviceName) {
        if (servicesByName[serviceName]) {
            return servicesByName[serviceName].getBuildStatus()
        } else {
            return BuildStatus.UNKNOWN
        }
    }

    static setServiceBuildStatus(String serviceName, String buildStatus) {
        if (servicesByName[serviceName]) {
            servicesByName[serviceName].setBuildStatus(BuildStatus.parseBuildStatus(buildStatus))
        }
    }
}