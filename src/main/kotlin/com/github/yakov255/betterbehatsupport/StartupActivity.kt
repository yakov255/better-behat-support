package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StartupActivity : ProjectActivity, DumbAware {

    private val log = Logger.getInstance(StartupActivity::class.java)

    override suspend fun execute(project: Project) {
        try {
            val result = withContext(Dispatchers.IO) {
                GitHubUpdateCheckService.getInstance().checkForUpdate()
            }
            GitHubUpdateNotifier.notifyIfUpdateAvailable(project, result)
        } catch (ex: Exception) {
            log.warn("GitHub update check: unexpected error", ex)
        }
    }
}
