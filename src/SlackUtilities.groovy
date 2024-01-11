class SlackUtilities {
    static String CI_CHANNEL = "ci"

    static String GREEN = "green"
    static String RED = "red"
    static String YELLOW = "yellow"

    static String SUCCESS_MESSAGE = "Build was successful!"
    static String FAILURE_MESSAGE = "Build failed!"
    static String UNSTABLE_MESSAGE = "Unstable build!"

    static String SUCCESS_EMOTICON = ":white_check_mark:"
    static String FAILURE_EMOTICON = ":x:"
    static String UNSTABLE_EMOTICON = ":warning:"

    static List<String> buildSlackMessageByBuildResult(BuildStatus buildResult) {
        switch(buildResult) {
            case BuildStatus.SUCCESS:
                return [SUCCESS_EMOTICON, GREEN, SUCCESS_MESSAGE]
            case BuildStatus.FAILURE:
                return [FAILURE_EMOTICON, RED, FAILURE_MESSAGE]
            case BuildStatus.UNSTABLE:
                return [UNSTABLE_EMOTICON, YELLOW, UNSTABLE_MESSAGE]
            default:
                throw new RuntimeException("Unexpected build result: " + buildResult)
        }
    }
}
