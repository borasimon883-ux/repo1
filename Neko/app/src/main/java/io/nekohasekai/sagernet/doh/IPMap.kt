package io.nekohasekai.sagernet.doh

import android.util.Log
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

// Equivalent of Intra's ipmap.IPMap interface
interface IPMap {
    fun get(hostname: String): IPSet
}

// Equivalent of Intra's ipMap struct
class IPMapImpl : IPMap {
    private val map = ConcurrentHashMap<String, IPSet>()

    override fun get(hostname: String): IPSet {
        return map.computeIfAbsent(hostname) { IPSet(hostname) }
    }
}

// Equivalent of Intra's IPSet struct
class IPSet(private val hostname: String) {
    private val ips = CopyOnWriteArrayList<InetAddress>()
    @Volatile
    private var confirmed: InetAddress? = null

    init {
        // Resolve hostname initially
        add(hostname)
    }

    // Equivalent of Intra's add function (internal)
    private fun add(ip: InetAddress) {
        if (!ips.contains(ip)) {
            ips.add(ip)
        }
    }

    // Equivalent of Intra's Add function (public)
    fun add(hostnameOrIp: String) {
        try {
            val addresses = InetAddress.getAllByName(hostnameOrIp)
            for (addr in addresses) {
                add(addr)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve $hostnameOrIp: ${e.message}")
        }
    }

    // Equivalent of Intra's Empty function
    fun isEmpty(): Boolean {
        return ips.isEmpty()
    }

    // Equivalent of Intra's GetAll function
    fun getAll(): List<InetAddress> {
        val shuffledList = ips.toMutableList()
        shuffledList.shuffle(Random)
        return shuffledList
    }

    // Equivalent of Intra's Confirmed function
    fun getConfirmed(): InetAddress? {
        return confirmed
    }

    // Equivalent of Intra's Confirm function
    fun confirm(ip: InetAddress) {
        if (confirmed != ip) {
            add(ip)
            confirmed = ip
        }
    }

    // Equivalent of Intra's Disconfirm function
    fun disconfirm(ip: InetAddress) {
        if (confirmed == ip) {
            confirmed = null
        }
    }

    companion object {
        private const val TAG = "IPSet"
    }
}


