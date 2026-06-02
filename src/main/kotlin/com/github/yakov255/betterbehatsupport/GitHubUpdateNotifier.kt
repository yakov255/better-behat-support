package com.github.yakov255.betterbehatsupport

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

object GitHubUpdateNotifier {

    private val log = Logger.getInstance(GitHubUpdateNotifier::class.java)
    private val notifiedThisSession = java.util.concurrent.atomic.AtomicBoolean(false)

    fun notifyIfUpdateAvailable(project: Project, result: GitHubUpdateCheckService.Result) {
        if (result !is GitHubUpdateCheckService.Result.UpdateAvailable) return
        if (!notifiedThisSession.compareAndSet(false, true)) return

        val settings = GitHubUpdateCheckSettings.getInstance()
        if (settings.lastSeenVersion == result.latestVersion) {
            notifiedThisSession.set(false)
            return
        }

        ApplicationManager.getApplication().invokeLater {
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("Better Behat Update Notifications")
                .createNotification(
                    "Behat Go To File update available",
                    "Version ${result.latestVersion} is available on GitHub " +
                        "(installed: ${result.currentVersion}).",
                    NotificationType.INFORMATION,
                )
                .addAction(object : NotificationAction("Open Releases") {
                    override fun actionPerformed(e: AnActionEvent, n: com.intellij.notification.Notification) {
                        BrowserUtil.browse(result.releaseUrl)
                        settings.lastSeenVersion = result.latestVersion
                        n.expire()
                    }
                })
                .addAction(object : NotificationAction("Don't check again") {
                    override fun actionPerformed(e: AnActionEvent, n: com.intellij.notification.Notification) {
                        settings.dontCheckAgain = true
                        log.info("GitHub update check disabled by user")
                        n.expire()
                    }
                })

            notification.notify(project)
        }
    }
}
