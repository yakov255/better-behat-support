package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.updateSettings.impl.UpdateSettingsProvider

class CustomRepoProvider : UpdateSettingsProvider {
    override fun getPluginRepositories(): List<String> =
        listOf("https://yakov255.github.io/better-behat-support/updatePlugins.xml")
}
