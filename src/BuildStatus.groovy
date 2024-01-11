enum BuildStatus {
    SUCCESS, FAILURE, UNSTABLE, ABORTED, UNKNOWN

    static BuildStatus parseBuildStatus(String buildStatus) {
        if (buildStatus != null) {
            try {
                return valueOf(buildStatus.toUpperCase())
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN
            }
        } else {
            return UNKNOWN
        }
    }
}