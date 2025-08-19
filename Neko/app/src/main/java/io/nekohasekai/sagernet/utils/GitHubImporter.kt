package io.nekohasekai.sagernet.utils

import android.content.Context
import android.widget.Toast
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.fmt.parseUniversal
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * GitHub-based random config importer
 * Imports proxy configurations from various GitHub repositories
 */
object GitHubImporter {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    // List of known GitHub repositories with proxy configurations
    private val configSources = listOf(
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/mix",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge.txt",
        "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub",
        "https://raw.githubusercontent.com/aiboboxx/v2rayfree/main/v2",
        "https://raw.githubusercontent.com/ripaojiedian/freenode/main/sub",
        "https://raw.githubusercontent.com/Jsnzkpg/Jsnzkpg/Jsnzkpg/Jsnzkpg",
        "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
        "https://raw.githubusercontent.com/tbbatbb/Proxy/master/dist/v2ray.config.txt",
        "https://raw.githubusercontent.com/anaer/Sub/main/clash.yaml"
    )
    
    /**
     * Import random configurations from GitHub sources
     */
    fun importRandomConfigs(context: Context, maxAttempts: Int = 3) {
        runOnDefaultDispatcher {
            var attempt = 0
            var successfulImports = 0
            
            while (attempt < maxAttempts && successfulImports == 0) {
                attempt++
                
                runOnMainDispatcher {
                    Toast.makeText(
                        context,
                        context.getString(R.string.github_import_attempting, attempt, maxAttempts),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                try {
                    // Select random source
                    val randomSource = configSources[Random.nextInt(configSources.size)]
                    Logs.d("Attempting to import from: $randomSource")
                    
                    val result = fetchConfigFromUrl(randomSource)
                    result.fold(
                        onSuccess = { content ->
                            val importResult = parseAndImportConfigs(context, content, randomSource)
                            if (importResult > 0) {
                                successfulImports = importResult
                            } else if (attempt < maxAttempts) {
                                // Try a different source
                                delay(2000)
                                continue
                            }
                        },
                        onFailure = { error ->
                            Logs.w("Failed to fetch from $randomSource", error)
                            if (attempt < maxAttempts) {
                                delay(2000) // Wait before next attempt
                            }
                        }
                    )
                } catch (e: Exception) {
                    Logs.w("GitHub import attempt $attempt failed", e)
                    if (attempt < maxAttempts) {
                        delay(2000)
                    }
                }
            }
            
            // Show final result
            runOnMainDispatcher {
                val message = if (successfulImports > 0) {
                    context.getString(R.string.github_import_success, successfulImports)
                } else {
                    context.getString(R.string.github_import_failed_all_attempts, maxAttempts)
                }
                
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                
                if (successfulImports > 0) {
                    GroupManager.postReload(DataStore.currentGroupId())
                }
            }
        }
    }
    
    /**
     * Fetch configuration content from URL
     */
    private suspend fun fetchConfigFromUrl(url: String): Result<String> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:40.0) Gecko/40.0 Firefox/40.0")
                .build()
            
            val response: Response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
            }
            
            val content = response.body?.string() ?: ""
            if (content.isBlank()) {
                return Result.failure(IOException("Empty response"))
            }
            
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parse and import configurations
     */
    private suspend fun parseAndImportConfigs(context: Context, content: String, source: String): Int {
        return try {
            val parseResult = ErrorHandler.safeExecute("parse GitHub configs") {
                parseUniversal(content)
            }
            
            parseResult.fold(
                onSuccess = { proxies ->
                    if (proxies.isEmpty()) {
                        return 0
                    }
                    
                    // Limit the number of imported configs to avoid spam
                    val maxImportCount = 50
                    val proxyList = if (proxies.size > maxImportCount) {
                        proxies.shuffled().take(maxImportCount)
                    } else {
                        proxies
                    }
                    
                    val currentGroup = DataStore.currentGroup()
                    var importedCount = 0
                    
                    for (proxy in proxyList) {
                        try {
                            proxy.groupId = currentGroup.id
                            proxy.userOrder = System.currentTimeMillis() + importedCount
                            proxy.name = "[GitHub] ${proxy.name}"
                            ProfileManager.createProfile(proxy)
                            importedCount++
                        } catch (e: Exception) {
                            Logs.w("Failed to import proxy from GitHub", e)
                        }
                    }
                    
                    Logs.d("Successfully imported $importedCount configs from $source")
                    importedCount
                },
                onFailure = { error ->
                    Logs.w("Failed to parse configs from GitHub", error)
                    0
                }
            )
        } catch (e: Exception) {
            Logs.w("Error parsing GitHub configs", e)
            0
        }
    }
}