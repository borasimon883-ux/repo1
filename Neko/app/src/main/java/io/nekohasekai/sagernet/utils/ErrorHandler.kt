package io.nekohasekai.sagernet.utils

import android.content.Context
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.readableMessage
import org.json.JSONException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/**
 * Enhanced error handler for Manus application
 * Provides user-friendly error messages and centralized error handling
 */
object ErrorHandler {
    
    /**
     * Convert technical exceptions to user-friendly messages
     */
    fun getReadableErrorMessage(throwable: Throwable, context: Context = app): String {
        return when (throwable) {
            is UnknownHostException -> {
                context.getString(R.string.error_network_dns_resolution_failed, throwable.message ?: "unknown host")
            }
            is SocketTimeoutException -> {
                context.getString(R.string.error_network_connection_timeout)
            }
            is ConnectException -> {
                if (throwable.message?.contains("ECONNREFUSED") == true) {
                    context.getString(R.string.error_network_connection_refused)
                } else {
                    context.getString(R.string.error_network_connection_failed, throwable.message ?: "")
                }
            }
            is SSLException, is CertificateException -> {
                context.getString(R.string.error_network_ssl_certificate_error)
            }
            is IOException -> {
                when {
                    throwable.message?.contains("ENETUNREACH") == true -> {
                        context.getString(R.string.error_network_unreachable)
                    }
                    throwable.message?.contains("EHOSTUNREACH") == true -> {
                        context.getString(R.string.error_host_unreachable)
                    }
                    else -> {
                        context.getString(R.string.error_network_io_error, throwable.message ?: "")
                    }
                }
            }
            is JSONException -> {
                context.getString(R.string.error_config_invalid_json, throwable.message ?: "")
            }
            is IllegalArgumentException -> {
                context.getString(R.string.error_config_invalid_parameter, throwable.message ?: "")
            }
            is SecurityException -> {
                context.getString(R.string.error_permission_denied, throwable.message ?: "")
            }
            else -> {
                // For unknown errors, provide the original readable message but with context
                val originalMessage = throwable.readableMessage
                if (originalMessage.length > 100) {
                    // Truncate very long error messages
                    "${originalMessage.take(97)}..."
                } else {
                    originalMessage
                }
            }
        }
    }
    
    /**
     * Handle config parsing errors with specific context
     */
    fun handleConfigError(throwable: Throwable, configType: String = "unknown", context: Context = app): String {
        Logs.w("Config parsing error for $configType", throwable)
        
        return when (throwable) {
            is JSONException -> {
                context.getString(R.string.error_config_invalid_format, configType)
            }
            is IllegalArgumentException -> {
                when {
                    throwable.message?.contains("method") == true -> {
                        context.getString(R.string.error_config_unsupported_method, configType)
                    }
                    throwable.message?.contains("port") == true -> {
                        context.getString(R.string.error_config_invalid_port, configType)
                    }
                    throwable.message?.contains("address") == true -> {
                        context.getString(R.string.error_config_invalid_address, configType)
                    }
                    else -> {
                        context.getString(R.string.error_config_invalid_parameter, throwable.message ?: "")
                    }
                }
            }
            else -> getReadableErrorMessage(throwable, context)
        }
    }
    
    /**
     * Handle network-related errors with specific context
     */
    fun handleNetworkError(throwable: Throwable, operation: String = "network operation", context: Context = app): String {
        Logs.w("Network error during $operation", throwable)
        return getReadableErrorMessage(throwable, context)
    }
    
    /**
     * Handle DNS-related errors
     */
    fun handleDnsError(throwable: Throwable, serverName: String = "DNS server", context: Context = app): String {
        Logs.w("DNS error with $serverName", throwable)
        
        return when (throwable) {
            is UnknownHostException -> {
                context.getString(R.string.error_dns_server_unreachable, serverName)
            }
            is SocketTimeoutException -> {
                context.getString(R.string.error_dns_query_timeout, serverName)
            }
            else -> getReadableErrorMessage(throwable, context)
        }
    }
    
    /**
     * Handle protocol-specific errors
     */
    fun handleProtocolError(throwable: Throwable, protocol: String, context: Context = app): String {
        Logs.w("Protocol error for $protocol", throwable)
        
        return when {
            throwable.message?.contains("unknown method") == true -> {
                context.getString(R.string.error_protocol_unsupported_method, protocol)
            }
            throwable.message?.contains("initialize outbound") == true -> {
                context.getString(R.string.error_protocol_initialization_failed, protocol)
            }
            throwable.message?.contains("transport") == true && throwable.message?.contains("registry") == true -> {
                context.getString(R.string.error_protocol_transport_registry, protocol)
            }
            else -> getReadableErrorMessage(throwable, context)
        }
    }
    
    /**
     * Safe execution wrapper that handles exceptions and returns user-friendly messages
     */
    inline fun <T> safeExecute(
        operation: String,
        context: Context = app,
        block: () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Logs.w("Safe execution failed for $operation", e)
            val errorMessage = getReadableErrorMessage(e, context)
            Result.failure(Exception(errorMessage, e))
        }
    }
    
    /**
     * Safe async execution for suspend functions
     */
    suspend inline fun <T> safeExecuteAsync(
        operation: String,
        context: Context = app,
        crossinline block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Logs.w("Safe async execution failed for $operation", e)
            val errorMessage = getReadableErrorMessage(e, context)
            Result.failure(Exception(errorMessage, e))
        }
    }
    
    /**
     * Log and format error for debugging purposes
     */
    fun logError(throwable: Throwable, context: String) {
        Logs.e("Error in $context: ${throwable.readableMessage}", throwable)
    }
    
    /**
     * Check if an error is recoverable (network-related vs configuration-related)
     */
    fun isRecoverableError(throwable: Throwable): Boolean {
        return when (throwable) {
            is UnknownHostException,
            is SocketTimeoutException,
            is ConnectException,
            is IOException -> true
            is JSONException,
            is IllegalArgumentException,
            is SecurityException -> false
            else -> false
        }
    }
}