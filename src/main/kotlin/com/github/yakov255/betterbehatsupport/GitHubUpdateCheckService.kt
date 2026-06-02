package com.github.yakov255.betterbehatsupport

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service(Service.Level.APP)
class GitHubUpdateCheckService {

    private val log = Logger.getInstance(GitHubUpdateCheckService::class.java)
    private val gson = Gson()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    sealed class Result {
        object Throttled : Result()
        object OptedOut : Result()
        object UpToDate : Result()
        data class UpdateAvailable(
            val currentVersion: String,
            val latestVersion: String,
            val releaseUrl: String,
        ) : Result()
        data class Failed(val reason: String) : Result()
    }

    private data class GitHubReleaseDto(
        @SerializedName("tag_name") val tagName: String = "",
        @SerializedName("html_url") val htmlUrl: String = "",
    )

    fun checkForUpdate(
        throttleMs: Long = TimeUnit.HOURS.toMillis(24),
        nowMs: Long = System.currentTimeMillis(),
    ): Result {
        val settings = GitHubUpdateCheckSettings.getInstance()
        if (settings.dontCheckAgain) return Result.OptedOut
        if (nowMs - settings.lastCheckedAtMs < throttleMs) return Result.Throttled

        val currentVersion = currentPluginVersion() ?: return Result.Failed("plugin descriptor not found")

        return try {
            val release = fetchLatestRelease()
            if (release == null) return Result.Failed("no release returned")
            val latestVersion = normalizeTag(release.tagName)
            if (latestVersion.isEmpty()) return Result.Failed("empty tag_name")

            settings.lastCheckedAtMs = nowMs

            val cmp = SemVer.compare(latestVersion, currentVersion)
            if (cmp <= 0) {
                Result.UpToDate
            } else {
                Result.UpdateAvailable(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    releaseUrl = release.htmlUrl.ifBlank {
                        "https://github.com/$REPO/releases/tag/${release.tagName}"
                    },
                )
            }
        } catch (e: Exception) {
            log.warn("GitHub update check failed", e)
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fetchLatestRelease(): GitHubReleaseDto? {
        val url = "https://api.github.com/repos/$REPO/releases/latest"
        log.debug("GitHub update check: GET $url")
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .timeout(Duration.ofSeconds(10))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            log.info("GitHub update check: HTTP ${response.statusCode()} from $url")
            return null
        }
        return gson.fromJson(response.body(), GitHubReleaseDto::class.java)
    }

    private fun currentPluginVersion(): String? {
        val descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?: return null
        return descriptor.version
    }

    private fun normalizeTag(tag: String): String =
        tag.trim().removePrefix("v").removePrefix("V")

    companion object {
        const val PLUGIN_ID = "com.github.yakov255.betterbehatsupport"
        const val REPO = "yakov255/better-behat-support"

        fun getInstance(): GitHubUpdateCheckService = service()
    }
}

internal object SemVer {

    fun compare(a: String, b: String): Int {
        val pa = parse(a) ?: return a.compareTo(b)
        val pb = parse(b) ?: return a.compareTo(b)

        for (i in 0 until 3) {
            val cmp = pa.numbers[i].compareTo(pb.numbers[i])
            if (cmp != 0) return cmp
        }

        if (pa.preRelease.isEmpty() && pb.preRelease.isNotEmpty()) return 1
        if (pa.preRelease.isNotEmpty() && pb.preRelease.isEmpty()) return -1
        if (pa.preRelease.isEmpty() && pb.preRelease.isEmpty()) return 0

        val ai = pa.preRelease
        val bi = pb.preRelease
        val n = minOf(ai.size, bi.size)
        for (i in 0 until n) {
            val x = ai[i]
            val y = bi[i]
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val cmp = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null && yn == null -> -1
                xn == null && yn != null -> 1
                else -> x.compareTo(y)
            }
            if (cmp != 0) return cmp
        }
        return ai.size.compareTo(bi.size)
    }

    private data class Parsed(val numbers: IntArray, val preRelease: List<String>)

    private fun parse(version: String): Parsed? {
        val trimmed = version.trim().substringBefore('+')
        if (trimmed.isEmpty()) return null
        val dashAt = trimmed.indexOf('-')
        val mainPart = if (dashAt >= 0) trimmed.substring(0, dashAt) else trimmed
        val preRelease = if (dashAt >= 0) trimmed.substring(dashAt + 1).split('.') else emptyList()

        val parts = mainPart.split('.')
        if (parts.isEmpty() || parts.size > 3) return null
        val nums = IntArray(3)
        for (i in 0 until 3) {
            val seg = parts.getOrNull(i) ?: "0"
            nums[i] = seg.toIntOrNull() ?: return null
        }
        return Parsed(nums, preRelease)
    }
}
