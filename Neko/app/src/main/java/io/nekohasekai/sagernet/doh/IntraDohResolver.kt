package io.nekohasekai.sagernet.doh

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

// Equivalent of Intra's doh.Resolver interface
interface IntraDohResolver {
    fun query(query: ByteArray): ByteArray
    fun getUrl(): String
}

// Equivalent of Intra's doh.resolver struct
class IntraDohResolverImpl(private val url: String) : IntraDohResolver {

    private val client: OkHttpClient

    init {
        val parsedUrl = url.toHttpUrlOrNull()
        if (parsedUrl == null || parsedUrl.scheme != "https") {
            throw IllegalArgumentException("Bad scheme or invalid URL: $url")
        }

        client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS) // Equivalent to tcpTimeout
            .readTimeout(20, TimeUnit.SECONDS) // Equivalent to ResponseHeaderTimeout
            .writeTimeout(10, TimeUnit.SECONDS) // Arbitrary write timeout
            .build()
    }

    override fun query(query: ByteArray): ByteArray {
        val request = Request.Builder()
            .url(url)
            .post(query.toRequestBody(MEDIA_TYPE_DNS_MESSAGE))
            .header("Content-Type", MEDIA_TYPE_DNS_MESSAGE)
            .header("Accept", MEDIA_TYPE_DNS_MESSAGE)
            .header("User-Agent", "Intra")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                return response.body?.bytes() ?: throw IOException("Empty response body")
            } else {
                throw IOException("HTTP error: ${response.code}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "DoH query failed: ${e.message}", e)
            throw e
        }
    }

    override fun getUrl(): String {
        return url
    }

    companion object {
        private const val TAG = "IntraDohResolver"
        private const val MEDIA_TYPE_DNS_MESSAGE = "application/dns-message"
    }
}


