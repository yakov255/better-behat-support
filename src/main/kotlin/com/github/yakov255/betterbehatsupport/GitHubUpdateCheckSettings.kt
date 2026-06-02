package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "BehatGitHubUpdateCheckSettings",
    storages = [Storage("behatGitHubUpdateSettings.xml")]
)
@Service(Service.Level.APP)
class GitHubUpdateCheckSettings : PersistentStateComponent<GitHubUpdateCheckSettings.State> {

    data class State(
        var dontCheckAgain: Boolean = false,
        var lastCheckedAtMs: Long = 0L,
        var lastSeenVersion: String = "",
    )

    private val lock = Any()
    private var state = State()

    override fun getState(): State = synchronized(lock) { state.copy() }

    override fun loadState(state: State) {
        synchronized(lock) {
            XmlSerializerUtil.copyBean(state, this.state)
        }
    }

    var dontCheckAgain: Boolean
        get() = synchronized(lock) { state.dontCheckAgain }
        set(value) = synchronized(lock) { state.dontCheckAgain = value }

    var lastCheckedAtMs: Long
        get() = synchronized(lock) { state.lastCheckedAtMs }
        set(value) = synchronized(lock) { state.lastCheckedAtMs = value }

    var lastSeenVersion: String
        get() = synchronized(lock) { state.lastSeenVersion }
        set(value) = synchronized(lock) { state.lastSeenVersion = value }

    companion object {
        fun getInstance(): GitHubUpdateCheckSettings = service()
    }
}
