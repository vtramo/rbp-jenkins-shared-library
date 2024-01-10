class SlackUtilities {
    static String CI_CHANNEL = "ci"

    static String GREEN = "green"
    static String RED = "red"
    static String YELLOW = "yellow"

    static String SUCCESS_MESSAGE = "Build was successful!"
    static String FAILURE_MESSAGE = "Build failed!"
    static String UNSTABLE_MESSAGE = "Unstable build!"

    static List<String> getSlackColorAndMessageNotificationByBuildResult(BuildResult buildResult) {
        switch(buildResult) {
            case BuildResult.SUCCESS:
                return [GREEN, SUCCESS_MESSAGE]
            case BuildResult.FAILURE:
                return [RED, FAILURE_MESSAGE]
            case BuildResult.UNSTABLE:
                return [YELLOW, UNSTABLE_MESSAGE]
            default:
                throw new RuntimeException("Unexpected build result: " + buildResult)
        }
    }
}
