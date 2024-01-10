def call(String serviceName) {
    script {
        def (slackColor, slackMessage) =
            SlackUtilities.getSlackColorAndMessageNotificationByBuildResult(BuildResult.valueOf(currentBuild.result))
        def ciChannel = SlackUtilities.CI_CHANNEL
    }

    slackSend(
        channel: "${ciChannel}",
        color: "${slackColor}",
        message: """
            :white_check_mark: [${serviceName}] ${slackMessage}
            *Branch:* ${GIT_BRANCH}
            *Commit ID:* ${GIT_COMMIT}
            *Short commit ID:* ${GIT_SHORT_COMMIT}
            *Commit message:* ${GIT_COMMIT_MSG}
            *Previous successful commit ID:* ${GIT_PREVIOUS_SUCCESSFUL_SHORT_COMMIT} 
            *Committer name:* ${GIT_COMMITTER_NAME}
            *Committer email:* ${GIT_COMMITTER_EMAIL}
            *Build label:* ${BUILD_TAG}
            *Build ID:* ${BUILD_ID}
            *Build URL:* ${BUILD_URL}
        """
    )
}