class Service {
    static List<Service> services = [
        new Service('auth'),
        new Service('booking'),
        new Service('message'),
        new Service('branding'),
        new Service('report'),
        new Service('room')
    ]

    String name

    Service(String name) {
        this.name = name
    }

    BuildStatus getBuildStatus() {
        String buildStatus = System.getenv(getBuildStatusEnvVariableName())
        println(getBuildStatusEnvVariableName())
        println(buildStatus)

        if (buildStatus != null) {
            try {
                return BuildStatus.valueOf(buildStatus.toUpperCase())
            } catch(IllegalArgumentException ignored) {
                return BuildStatus.UNKNOWN
            }
        } else {
            return BuildStatus.UNKNOWN
        }
    }

    String getBuildStatusEnvVariableName() {
        return BuildUtilities.BUILD_STATUS_ENV_VAR_NAME_PREFIX + "_" + name.toUpperCase()
    }
}