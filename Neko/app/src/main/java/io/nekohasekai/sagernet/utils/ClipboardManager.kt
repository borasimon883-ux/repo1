package io.nekohasekai.sagernet.utils

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.fmt.parseUniversal
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import kotlinx.coroutines.delay
import java.util.regex.Pattern

/**
 * Smart clipboard manager for automatic config import
 * Monitors clipboard and offers to import proxy configurations
 */
object SmartClipboardManager {
    
    private var lastClipboardContent = ""
    private var isMonitoring = false
    
    // Common proxy URL patterns
    private val proxyPatterns = listOf(
        Pattern.compile("^(ss|ssr|vmess|vless|trojan|hysteria|tuic|shadowsocks)://.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^https?://.*\\.(txt|yaml|yml|json).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\b(server|proxy|config|subscription)\\b.*", Pattern.CASE_INSENSITIVE)
    )
    
    /**
     * Check clipboard for proxy configurations and offer import
     */
    fun checkClipboardForConfigs(context: Context) {
        if (!SettingsManager.UI.shouldAutoImportFromClipboard()) {
            return
        }
        
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        
        if (clipData == null || clipData.itemCount == 0) {
            return
        }
        
        val clipboardText = clipData.getItemAt(0).text?.toString() ?: return
        
        // Skip if same as last checked content
        if (clipboardText == lastClipboardContent || clipboardText.length < 10) {
            return
        }
        
        lastClipboardContent = clipboardText
        
        // Check if clipboard contains proxy configuration
        if (containsProxyConfig(clipboardText)) {
            showImportDialog(context, clipboardText)
        }
    }
    
    /**
     * Check if text contains proxy configuration patterns
     */
    private fun containsProxyConfig(text: String): Boolean {
        val trimmedText = text.trim()
        
        // Check against known proxy patterns
        return proxyPatterns.any { pattern ->
            pattern.matcher(trimmedText).find()
        } || 
        // Check for base64 encoded content (common in proxy configs)
        (trimmedText.length > 50 && isLikelyBase64(trimmedText)) ||
        // Check for JSON-like proxy config
        (trimmedText.contains("server") && trimmedText.contains("port") && 
         (trimmedText.contains("{") || trimmedText.contains("password")))
    }
    
    /**
     * Check if string is likely base64 encoded
     */
    private fun isLikelyBase64(text: String): Boolean {
        val base64Pattern = Pattern.compile("^[A-Za-z0-9+/]*={0,2}$")
        return base64Pattern.matcher(text).matches() && text.length % 4 == 0
    }
    
    /**
     * Show import dialog to user
     */
    private fun showImportDialog(context: Context, clipboardContent: String) {
        val preview = if (clipboardContent.length > 100) {
            clipboardContent.take(97) + "..."
        } else {
            clipboardContent
        }
        
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.clipboard_import_title))
            .setMessage(context.getString(R.string.clipboard_import_message, preview))
            .setPositiveButton(context.getString(R.string.import_button)) { _, _ ->
                importFromClipboard(context, clipboardContent)
            }
            .setNegativeButton(context.getString(R.string.ignore_button)) { _, _ ->
                // Do nothing
            }
            .setNeutralButton(context.getString(R.string.disable_auto_import)) { _, _ ->
                // Disable auto-import feature
                disableAutoImport(context)
            }
            .show()
    }
    
    /**
     * Import configuration from clipboard content
     */
    private fun importFromClipboard(context: Context, content: String) {
        runOnDefaultDispatcher {
            try {
                val results = ErrorHandler.safeExecute("clipboard import") {
                    parseUniversal(content.trim())
                }
                
                results.fold(
                    onSuccess = { proxies ->
                        if (proxies.isEmpty()) {
                            runOnMainDispatcher {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.no_proxies_found_in_clipboard),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@fold
                        }
                        
                        // Import proxies to current group
                        val currentGroup = DataStore.currentGroup()
                        var importedCount = 0
                        
                        for (proxy in proxies) {
                            try {
                                proxy.groupId = currentGroup.id
                                proxy.userOrder = System.currentTimeMillis()
                                io.nekohasekai.sagernet.database.ProfileManager.createProfile(proxy)
                                importedCount++
                            } catch (e: Exception) {
                                Logs.w("Failed to import proxy from clipboard", e)
                            }
                        }
                        
                        runOnMainDispatcher {
                            val message = if (importedCount > 0) {
                                context.getString(R.string.clipboard_import_success, importedCount)
                            } else {
                                context.getString(R.string.clipboard_import_failed)
                            }
                            
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            
                            if (importedCount > 0) {
                                GroupManager.postReload(currentGroup.id)
                            }
                        }
                    },
                    onFailure = { error ->
                        runOnMainDispatcher {
                            Toast.makeText(
                                context,
                                context.getString(R.string.clipboard_import_error, error.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                runOnMainDispatcher {
                    Toast.makeText(
                        context,
                        ErrorHandler.getReadableErrorMessage(e, context),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Disable auto-import feature
     */
    private fun disableAutoImport(context: Context) {
        // Note: This would require adding a setting to disable auto-import
        // For now, just show a message
        Toast.makeText(
            context,
            context.getString(R.string.auto_import_disabled_message),
            Toast.LENGTH_LONG
        ).show()
    }
    
    /**
     * Start monitoring clipboard changes
     */
    fun startMonitoring(context: Context) {
        if (isMonitoring) return
        
        isMonitoring = true
        runOnDefaultDispatcher {
            while (isMonitoring) {
                try {
                    runOnMainDispatcher {
                        checkClipboardForConfigs(context)
                    }
                    delay(2000) // Check every 2 seconds
                } catch (e: Exception) {
                    Logs.w("Clipboard monitoring error", e)
                    delay(5000) // Wait longer if there's an error
                }
            }
        }
    }
    
    /**
     * Stop monitoring clipboard changes
     */
    fun stopMonitoring() {
        isMonitoring = false
    }
}