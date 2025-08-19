package io.nekohasekai.sagernet.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.SettingsManager
import kotlinx.coroutines.*
import libcore.Libcore
import org.json.JSONArray
import java.net.URL

class DohSettingsActivity : AppCompatActivity() {

    private lateinit var dohServerListView: ListView
    private lateinit var addDohServerButton: Button
    private lateinit var testAllDohServersButton: Button
    private lateinit var selectedDohServerText: TextView
    private lateinit var dohServerAdapter: ArrayAdapter<String>
    private val latencyMap: MutableMap<String, Int> = mutableMapOf()
    private val defaultDohServers = setOf(
        "https://cloudflare-dns.com/dns-query",
        "https://dns.google/dns-query",
        "https://dns.quad9.net/dns-query"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doh_settings)
        title = "DoH Settings"

        dohServerListView = findViewById(R.id.doh_server_list)
        addDohServerButton = findViewById(R.id.add_doh_server_button)
        testAllDohServersButton = findViewById(R.id.test_all_doh_servers_button)
        selectedDohServerText = findViewById(R.id.selected_doh_server_text)

        loadDohServers()
        updateSelectedDohServerText()

        addDohServerButton.setOnClickListener {
            showAddDohServerDialog()
        }

        testAllDohServersButton.setOnClickListener {
            testAllDohServers()
        }

        dohServerListView.setOnItemClickListener { _, _, position, _ ->
            val selectedServer = dohServerAdapter.getItem(position)
            if (selectedServer != null) {
                DataStore.dohServers = selectedServer
                updateSelectedDohServerText()
            }
        }
    }

    private fun loadDohServers() {
        val dohServers = DataStore.dohServersList.toMutableList()
        dohServerAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, dohServers) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val url = getItem(position) ?: ""
                val latency = latencyMap[url]
                view.text = if (latency == null) url else buildString {
                    append(url)
                    append(" (")
                    append(if (latency >= 0) "${latency}ms" else "Error")
                    append(")")
                }
                return view
            }
        }
        dohServerListView.adapter = dohServerAdapter

        dohServerListView.setOnItemLongClickListener { _, _, position, _ ->
            val selectedServer = dohServerAdapter.getItem(position)
            if (selectedServer != null && !defaultDohServers.contains(selectedServer)) {
                AlertDialog.Builder(this)
                    .setTitle("Remove DoH Server")
                    .setMessage("Are you sure you want to remove this DoH server?\n\n$selectedServer")
                    .setPositiveButton("Remove") { _, _ ->
                        val currentList = DataStore.dohServersList.toMutableSet()
                        currentList.remove(selectedServer)
                        DataStore.dohServersList = currentList
                        dohServerAdapter.remove(selectedServer)
                        dohServerAdapter.notifyDataSetChanged()
                        if (DataStore.dohServers == selectedServer) {
                            DataStore.dohServers = "" // Clear selected if removed
                            updateSelectedDohServerText()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            true
        }
    }

    private fun showAddDohServerDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add New DoH Server")

        val input = EditText(this)
        input.hint = "Enter DoH URL (e.g., https://dns.example.com/dns-query)"
        builder.setView(input)

        builder.setPositiveButton("Add") { _, _ ->
            val newDohUrl = input.text.toString().trim()
            if (newDohUrl.isNotEmpty()) {
                val isValid = try {
                    val parsed = URL(newDohUrl)
                    parsed.protocol == "https"
                } catch (e: Exception) { false }
                if (isValid) {
                    val currentList = DataStore.dohServersList.toMutableSet()
                    if (!currentList.contains(newDohUrl)) {
                        currentList.add(newDohUrl)
                        DataStore.dohServersList = currentList
                        dohServerAdapter.add(newDohUrl)
                        dohServerAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun updateSelectedDohServerText() {
        val selectedServer = DataStore.dohServers
        if (selectedServer.isNotEmpty()) {
            selectedDohServerText.text = "Selected DoH Server: $selectedServer"
        } else {
            selectedDohServerText.text = "Selected DoH Server: None"
        }
    }

    private fun testAllDohServers() {
        val servers = DataStore.dohServersList.toList()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val hostPorts = servers.mapNotNull { serverUrl ->
                    try {
                        val parsed = URL(serverUrl)
                        val host = parsed.host
                        val port = if (parsed.port > 0) parsed.port else 443
                        "$host:$port"
                    } catch (_: Exception) {
                        null
                    }
                }

                if (hostPorts.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        latencyMap.clear()
                        dohServerAdapter.notifyDataSetChanged()
                        DataStore.dohServers = ""
                        updateSelectedDohServerText()
                    }
                    return@launch
                }

                val timeout = SettingsManager.Network.getPingTimeoutMs()
                val resultsJson = Libcore.parallelPing(JSONArray(hostPorts).toString(), timeout)

                val resultsArray = JSONArray(resultsJson)
                val urlToLatency: MutableMap<String, Int> = mutableMapOf()

                var fastestUrl: String? = null
                var minLatency = Int.MAX_VALUE

                for (i in 0 until resultsArray.length()) {
                    if (i >= servers.size) break
                    val url = servers[i]
                    val result = resultsArray.getJSONObject(i)
                    val success = result.optBoolean("success", false)
                    val pingMs = if (success) result.optInt("ping_ms", -1) else -1
                    urlToLatency[url] = pingMs
                    if (success && pingMs >= 0 && pingMs < minLatency) {
                        minLatency = pingMs
                        fastestUrl = url
                    }
                }

                withContext(Dispatchers.Main) {
                    latencyMap.clear()
                    latencyMap.putAll(urlToLatency)
                    dohServerAdapter.notifyDataSetChanged()

                    if (fastestUrl != null && SettingsManager.DNS.shouldAutoSelectFastest()) {
                        DataStore.dohServers = fastestUrl!!
                    }
                    updateSelectedDohServerText()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    latencyMap.clear()
                    dohServerAdapter.notifyDataSetChanged()
                }
            }
        }
    }
}