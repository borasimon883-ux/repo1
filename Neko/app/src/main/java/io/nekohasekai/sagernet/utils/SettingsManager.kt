package io.nekohasekai.sagernet.utils

import android.content.Context
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Centralized settings manager for Manus application
 * Reads configuration from assets/settings.json and provides type-safe access
 */
object SettingsManager {
    private var settingsCache: JSONObject? = null
    private const val SETTINGS_FILE = "settings.json"
    
    /**
     * Load settings from assets/settings.json
     */
    private fun loadSettings(): JSONObject {
        if (settingsCache != null) {
            return settingsCache!!
        }
        
        try {
            val inputStream = app.assets.open(SETTINGS_FILE)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            settingsCache = JSONObject(jsonString)
            Logs.d("Settings loaded successfully from $SETTINGS_FILE")
            return settingsCache!!
        } catch (e: IOException) {
            Logs.w("Failed to load settings file: ${e.message}")
            return getDefaultSettings()
        } catch (e: JSONException) {
            Logs.w("Failed to parse settings JSON: ${e.message}")
            return getDefaultSettings()
        }
    }
    
    /**
     * Get default settings in case the settings file is missing or invalid
     */
    private fun getDefaultSettings(): JSONObject {
        return JSONObject().apply {
            put("network", JSONObject().apply {
                put("ping_timeout_ms", 3000)
                put("connection_test_concurrent", 5)
                put("url_test_timeout_ms", 10000)
                put("dns_query_timeout_ms", 5000)
            })
            put("dns", JSONObject().apply {
                put("auto_select_fastest", true)
                put("fallback_to_system", true)
            })
            put("ui", JSONObject().apply {
                put("auto_import_clipboard", true)
                put("show_ping_in_ms", true)
                put("auto_scroll_to_top_after_refresh", true)
                put("remove_failed_configs_after_attempts", 3)
            })
        }
    }
    
    /**
     * Get network-related settings
     */
    object Network {
        fun getPingTimeoutMs(): Int {
            return try {
                loadSettings().getJSONObject("network").getInt("ping_timeout_ms")
            } catch (e: Exception) {
                Logs.w("Failed to get ping timeout, using default: ${e.message}")
                3000
            }
        }
        
        fun getConnectionTestConcurrent(): Int {
            return try {
                loadSettings().getJSONObject("network").getInt("connection_test_concurrent")
            } catch (e: Exception) {
                Logs.w("Failed to get connection test concurrent, using default: ${e.message}")
                5
            }
        }
        
        fun getUrlTestTimeoutMs(): Int {
            return try {
                loadSettings().getJSONObject("network").getInt("url_test_timeout_ms")
            } catch (e: Exception) {
                Logs.w("Failed to get URL test timeout, using default: ${e.message}")
                10000
            }
        }
        
        fun getDnsQueryTimeoutMs(): Int {
            return try {
                loadSettings().getJSONObject("network").getInt("dns_query_timeout_ms")
            } catch (e: Exception) {
                Logs.w("Failed to get DNS query timeout, using default: ${e.message}")
                5000
            }
        }
    }
    
    /**
     * Get DNS-related settings
     */
    object DNS {
        fun getDefaultServers(): List<DnsServer> {
            return try {
                val servers = mutableListOf<DnsServer>()
                val dnsSettings = loadSettings().getJSONObject("dns")
                val serverArray = dnsSettings.getJSONArray("default_servers")
                
                for (i in 0 until serverArray.length()) {
                    val server = serverArray.getJSONObject(i)
                    servers.add(DnsServer(
                        name = server.getString("name"),
                        url = server.getString("url"),
                        type = server.getString("type"),
                        port = server.optInt("port", 53),
                        removable = server.optBoolean("removable", true)
                    ))
                }
                servers
            } catch (e: Exception) {
                Logs.w("Failed to get default DNS servers, using fallback: ${e.message}")
                listOf(
                    DnsServer("Cloudflare DoH", "https://cloudflare-dns.com/dns-query", "doh", 443, false),
                    DnsServer("Google DoH", "https://dns.google/dns-query", "doh", 443, false)
                )
            }
        }
        
        fun shouldAutoSelectFastest(): Boolean {
            return try {
                loadSettings().getJSONObject("dns").getBoolean("auto_select_fastest")
            } catch (e: Exception) {
                Logs.w("Failed to get auto select fastest DNS setting, using default: ${e.message}")
                true
            }
        }
        
        fun shouldFallbackToSystem(): Boolean {
            return try {
                loadSettings().getJSONObject("dns").getBoolean("fallback_to_system")
            } catch (e: Exception) {
                Logs.w("Failed to get DNS fallback setting, using default: ${e.message}")
                true
            }
        }
    }
    
    /**
     * Get routing-related settings
     */
    object Routing {
        fun getIranianAppPackages(): List<String> {
            return try {
                val routing = loadSettings().getJSONObject("routing")
                val iranianApps = routing.getJSONObject("iranian_apps_direct")
                val packages = iranianApps.getJSONArray("packages")
                
                val result = mutableListOf<String>()
                for (i in 0 until packages.length()) {
                    result.add(packages.getString(i))
                }
                result
            } catch (e: Exception) {
                Logs.w("Failed to get Iranian app packages, using default: ${e.message}")
                listOf(
                    "ir.sep.shaparak",
                    "ir.shaparak.shaparak",
                    "ir.resaneh1.resa",
                    "ir.bankmelli.mobile"
                )
            }
        }
        
        fun isIranianAppsDirectEnabled(): Boolean {
            return try {
                val routing = loadSettings().getJSONObject("routing")
                routing.getJSONObject("iranian_apps_direct").getBoolean("enabled")
            } catch (e: Exception) {
                Logs.w("Failed to get Iranian apps direct setting, using default: ${e.message}")
                true
            }
        }
    }
    
    /**
     * Get UI-related settings
     */
    object UI {
        fun shouldAutoImportFromClipboard(): Boolean {
            return try {
                loadSettings().getJSONObject("ui").getBoolean("auto_import_clipboard")
            } catch (e: Exception) {
                Logs.w("Failed to get auto import clipboard setting, using default: ${e.message}")
                true
            }
        }
        
        fun shouldShowPingInMs(): Boolean {
            return try {
                loadSettings().getJSONObject("ui").getBoolean("show_ping_in_ms")
            } catch (e: Exception) {
                Logs.w("Failed to get show ping setting, using default: ${e.message}")
                true
            }
        }
        
        fun shouldAutoScrollToTopAfterRefresh(): Boolean {
            return try {
                loadSettings().getJSONObject("ui").getBoolean("auto_scroll_to_top_after_refresh")
            } catch (e: Exception) {
                Logs.w("Failed to get auto scroll setting, using default: ${e.message}")
                true
            }
        }
        
        fun getRemoveFailedConfigsAfterAttempts(): Int {
            return try {
                loadSettings().getJSONObject("ui").getInt("remove_failed_configs_after_attempts")
            } catch (e: Exception) {
                Logs.w("Failed to get remove failed configs setting, using default: ${e.message}")
                3
            }
        }
    }
    
    /**
     * Get protocol-related settings
     */
    object Protocols {
        fun isProtocolEnabled(protocol: String): Boolean {
            return try {
                val protocols = loadSettings().getJSONObject("protocols")
                if (protocols.has(protocol)) {
                    protocols.getJSONObject(protocol).getBoolean("enabled")
                } else {
                    true // Default to enabled for unknown protocols
                }
            } catch (e: Exception) {
                Logs.w("Failed to get protocol enabled setting for $protocol, using default: ${e.message}")
                true
            }
        }
        
        fun getShadowsocksDefaultMethod(): String {
            return try {
                val protocols = loadSettings().getJSONObject("protocols")
                protocols.getJSONObject("shadowsocks").getString("default_method")
            } catch (e: Exception) {
                Logs.w("Failed to get Shadowsocks default method, using default: ${e.message}")
                "aes-256-gcm"
            }
        }
        
        fun getShadowsocksSupportedMethods(): List<String> {
            return try {
                val protocols = loadSettings().getJSONObject("protocols")
                val shadowsocks = protocols.getJSONObject("shadowsocks")
                val methods = shadowsocks.getJSONArray("supported_methods")
                
                val result = mutableListOf<String>()
                for (i in 0 until methods.length()) {
                    result.add(methods.getString(i))
                }
                result
            } catch (e: Exception) {
                Logs.w("Failed to get Shadowsocks supported methods, using default: ${e.message}")
                listOf("aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305")
            }
        }
    }
    
    /**
     * Clear settings cache to force reload
     */
    fun clearCache() {
        settingsCache = null
    }
    
    /**
     * Data class for DNS server configuration
     */
    data class DnsServer(
        val name: String,
        val url: String,
        val type: String, // "doh", "dot", "doq"
        val port: Int = 53,
        val removable: Boolean = true
    )
}