package moe.shizuku.manager.module

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class CatalogModule(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long? = null,
    val author: String,
    val description: String,
    val githubRepo: String,
    val downloadUrl: String,
    val tags: List<String> = emptyList(),
    val isOfficial: Boolean = false,
    val iconUrl: String? = null
)

object CatalogModuleManager {

    private const val REMOTE_CATALOG_URL = "https://raw.githubusercontent.com/HmnDev-Tech/Shevery/main/catalog.json"

    val builtInCatalog = listOf(
        CatalogModule(
            id = "ShizuConfigs",
            name = "ShizuConfigs",
            version = "1.0.0",
            versionCode = 100,
            author = "HmnDev-Tech",
            description = "Official module by HmnDev-Tech. All-in-one ADB Command center with Material You WebUI, Manage packages, settings, display, performance, network, and custom commands.",
            githubRepo = "HmnDev-Tech/ShizuConfigs",
            downloadUrl = "https://github.com/HmnDev-Tech/ShizuConfigs/releases/latest/download/ShizuConfigs.zip",
            tags = listOf("OFFICIAL", "ADB", "System", "SetEdit", "Monitoring"),
            isOfficial = true
        )
    )

    suspend fun loadCatalog(): List<CatalogModule> = withContext(Dispatchers.IO) {
        val merged = mutableMapOf<String, CatalogModule>()
        builtInCatalog.forEach { merged[it.id] = it }

        try {
            val url = URL(REMOTE_CATALOG_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonText)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank()) {
                        val tagsList = mutableListOf<String>()
                        val tagsArray = obj.optJSONArray("tags")
                        if (tagsArray != null) {
                            for (t in 0 until tagsArray.length()) {
                                tagsList.add(tagsArray.getString(t))
                            }
                        }
                        val isOfficial = obj.optBoolean("isOfficial", false) || tagsList.any { it.equals("OFFICIAL", ignoreCase = true) }
                        val item = CatalogModule(
                            id = id,
                            name = obj.optString("name", id),
                            version = obj.optString("version", "1.0.0"),
                            versionCode = obj.optLong("versionCode", 1),
                            author = obj.optString("author", "Unknown"),
                            description = obj.optString("description", ""),
                            githubRepo = obj.optString("githubRepo", ""),
                            downloadUrl = obj.optString("downloadUrl", ""),
                            tags = tagsList,
                            isOfficial = isOfficial,
                            iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() }
                        )
                        merged[id] = item
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to builtInCatalog if remote catalog fails
        }

        // Live check GitHub Latest Release for each module in catalog to update version & download URL
        val updatedList = merged.values.map { module ->
            if (module.githubRepo.isNotBlank()) {
                val releaseInfo = fetchLatestGitHubReleaseInfo(module.githubRepo)
                if (releaseInfo != null) {
                    module.copy(
                        version = releaseInfo.first,
                        downloadUrl = releaseInfo.second
                    )
                } else module
            } else module
        }

        updatedList
    }

    private fun fetchLatestGitHubReleaseInfo(repo: String): Pair<String, String>? {
        return try {
            val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "Shevery-App")

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(text)
                val tagName = obj.optString("tag_name").trimStart('v', 'V')
                var zipUrl: String? = null
                val assets = obj.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".zip", ignoreCase = true)) {
                            zipUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                if (zipUrl == null) {
                    zipUrl = obj.optString("zipball_url").takeIf { it.isNotBlank() }
                }
                if (!zipUrl.isNullOrBlank() && tagName.isNotBlank()) {
                    Pair(tagName, zipUrl)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun downloadToDownloads(context: Context, downloadUrl: String, filename: String): Long {
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(filename)
            setDescription("Downloading Shevery ADB Module ZIP...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    suspend fun downloadAndInstall(context: Context, downloadUrl: String, filename: String): AdbModule =
        withContext(Dispatchers.IO) {
            val temp = File(context.cacheDir, "catalog-download-$filename")
            try {
                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true

                if (conn.responseCode in 300..399) {
                    val redirectUrl = conn.getHeaderField("Location")
                    if (!redirectUrl.isNullOrBlank()) {
                        return@withContext downloadAndInstall(context, redirectUrl, filename)
                    }
                }

                require(conn.responseCode == 200) { "Download failed with HTTP ${conn.responseCode}" }

                conn.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val uri = Uri.fromFile(temp)
                AdbModuleManager.install(context, uri)
            } finally {
                temp.delete()
            }
        }

    suspend fun checkModuleUpdate(module: AdbModule): ModuleUpdateInfo? = withContext(Dispatchers.IO) {
        val updateJsonUrl = module.updateJson
        if (!updateJsonUrl.isNullOrBlank()) {
            return@withContext checkCustomUpdateJson(module, updateJsonUrl)
        }

        val githubRepo = extractGitHubRepo(module) ?: return@withContext null
        return@withContext checkGitHubLatestRelease(module, githubRepo)
    }

    private fun extractGitHubRepo(module: AdbModule): String? {
        val candidate = module.url ?: builtInCatalog.firstOrNull { it.id == module.id }?.githubRepo
        if (candidate.isNullOrBlank()) return null

        if (candidate.contains("github.com/")) {
            val parts = candidate.substringAfter("github.com/").trim('/').split('/')
            if (parts.size >= 2) {
                return "${parts[0]}/${parts[1]}"
            }
        }
        if (candidate.contains('/') && !candidate.startsWith("http")) {
            return candidate.trim('/')
        }
        return null
    }

    private fun checkCustomUpdateJson(module: AdbModule, jsonUrl: String): ModuleUpdateInfo? {
        return try {
            val url = URL(jsonUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(text)

            val version = obj.optString("version")
            val versionCode = obj.optLong("versionCode", -1).takeIf { it != -1L }
            val zipUrl = obj.optString("zipUrl")
            val changelog = obj.optString("changelog")

            if (zipUrl.isBlank() || version.isBlank()) return null

            val isNewer = if (versionCode != null && module.versionCode != null) {
                versionCode > module.versionCode
            } else {
                isVersionNewer(version, module.version)
            }

            if (isNewer) {
                ModuleUpdateInfo(
                    newVersion = version,
                    newVersionCode = versionCode,
                    zipUrl = zipUrl,
                    changelog = changelog.takeIf { it.isNotBlank() }
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun checkGitHubLatestRelease(module: AdbModule, repo: String): ModuleUpdateInfo? {
        return try {
            val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "Shevery-App")

            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(text)

            val tagName = obj.optString("tag_name").trimStart('v', 'V')
            val body = obj.optString("body")

            var zipUrl: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".zip", ignoreCase = true)) {
                        zipUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            if (zipUrl == null) {
                zipUrl = obj.optString("zipball_url").takeIf { it.isNotBlank() }
            }
            if (zipUrl.isNullOrBlank() || tagName.isBlank()) return null

            if (isVersionNewer(tagName, module.version)) {
                ModuleUpdateInfo(
                    newVersion = tagName,
                    newVersionCode = null,
                    zipUrl = zipUrl,
                    changelog = body.takeIf { it.isNotBlank() }
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun isVersionNewer(remote: String, local: String?): Boolean {
        if (local.isNullOrBlank()) return true
        val rClean = remote.trim().trimStart('v', 'V')
        val lClean = local.trim().trimStart('v', 'V')
        if (rClean == lClean) return false

        val rParts = rClean.split('.').mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
        val lParts = lClean.split('.').mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }

        val max = maxOf(rParts.size, lParts.size)
        for (i in 0 until max) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
