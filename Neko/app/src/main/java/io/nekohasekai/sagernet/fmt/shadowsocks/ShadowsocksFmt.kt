package io.nekohasekai.sagernet.fmt.shadowsocks

import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.SettingsManager
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

fun ShadowsocksBean.fixPluginName() {
    if (plugin.startsWith("simple-obfs")) {
        plugin = plugin.replaceFirst("simple-obfs", "obfs-local")
    }
}

fun ShadowsocksBean.validateAndFixMethod() {
    val supportedMethods = SettingsManager.Protocols.getShadowsocksSupportedMethods()
    
    // If method is empty or null, use default
    if (method.isNullOrBlank()) {
        method = SettingsManager.Protocols.getShadowsocksDefaultMethod()
        return
    }
    
    // Check if method is supported
    if (!supportedMethods.contains(method)) {
        // Try to map legacy methods to modern equivalents
        method = when (method.lowercase()) {
            "aes-256-cfb8", "aes-256-ofb" -> "aes-256-cfb"
            "aes-192-cfb8", "aes-192-ofb" -> "aes-192-cfb"
            "aes-128-cfb8", "aes-128-ofb" -> "aes-128-cfb"
            "chacha20" -> "chacha20-ietf"
            "salsa20" -> "chacha20-ietf" // Fallback to similar method
            "rc4", "rc4-md5" -> "aes-256-gcm" // Upgrade insecure methods
            "bf-cfb", "cast5-cfb", "des-cfb" -> "aes-256-gcm" // Upgrade weak methods
            else -> {
                // If still not supported, use default
                if (!supportedMethods.contains(method)) {
                    SettingsManager.Protocols.getShadowsocksDefaultMethod()
                } else {
                    method
                }
            }
        }
    }
}

fun parseShadowsocks(url: String): ShadowsocksBean {

    if (url.substringBefore("#").contains("@")) {
        var link = url.replace("ss://", "https://").toHttpUrlOrNull() ?: error(
            "invalid ss-android link $url"
        )

        if (link.username.isBlank()) { // fix justmysocks's shit link
            link = (("https://" + url.substringAfter("ss://")
                .substringBefore("#")
                .decodeBase64UrlSafe()).toHttpUrlOrNull()
                ?: error("invalid jms link $url")
                    ).newBuilder().fragment(url.substringAfter("#")).build()
        }

        // ss-android style

        if (link.password.isNotBlank()) {
            return ShadowsocksBean().apply {
                serverAddress = link.host
                serverPort = link.port
                method = link.username
                password = link.password
                plugin = link.queryParameter("plugin") ?: ""
                name = link.fragment
                fixPluginName()
                validateAndFixMethod()
            }
        }

        val methodAndPswd = link.username.decodeBase64UrlSafe()

        return ShadowsocksBean().apply {
            serverAddress = link.host
            serverPort = link.port
            method = methodAndPswd.substringBefore(":")
            password = methodAndPswd.substringAfter(":")
            plugin = link.queryParameter("plugin") ?: ""
            name = link.fragment
            fixPluginName()
            validateAndFixMethod()
        }
    } else {
        // v2rayN style
        var v2Url = url

        if (v2Url.contains("#")) v2Url = v2Url.substringBefore("#")

        val link = ("https://" + v2Url.substringAfter("ss://")
            .decodeBase64UrlSafe()).toHttpUrlOrNull() ?: error("invalid v2rayN link $url")

        return ShadowsocksBean().apply {
            serverAddress = link.host
            serverPort = link.port
            method = link.username
            password = link.password
            plugin = ""
            val remarks = url.substringAfter("#").unUrlSafe()
            if (remarks.isNotBlank()) name = remarks
            validateAndFixMethod()
        }
    }

}

fun ShadowsocksBean.toUri(): String {

    val builder = linkBuilder().username(Util.b64EncodeUrlSafe("$method:$password"))
        .host(serverAddress)
        .port(serverPort)

    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink("ss").replace("$serverPort/", "$serverPort")

}

fun JSONObject.parseShadowsocks(): ShadowsocksBean {
    return ShadowsocksBean().apply {
        serverAddress = getStr("server")
        serverPort = getIntNya("server_port")
        password = getStr("password")
        method = getStr("method")
        name = optString("remarks", "")

        val pId = getStr("plugin")
        if (!pId.isNullOrBlank()) {
            plugin = pId + ";" + optString("plugin_opts", "")
        }
        
        validateAndFixMethod()
    }
}

fun buildSingBoxOutboundShadowsocksBean(bean: ShadowsocksBean): SingBoxOptions.Outbound_ShadowsocksOptions {
    return SingBoxOptions.Outbound_ShadowsocksOptions().apply {
        type = "shadowsocks"
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password
        method = bean.method
        if (bean.plugin.isNotBlank()) {
            plugin = bean.plugin.substringBefore(";")
            plugin_opts = bean.plugin.substringAfter(";")
        }
    }
}
