package io.nekohasekai.sagernet.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class DohSettingsActivity : AppCompatActivity() {

    private lateinit var dohServerListView: ListView
    private lateinit var addDohServerButton: Button
    private lateinit var testAllDohServersButton: Button
    private lateinit var selectedDohServerText: TextView
    private lateinit var dohServerAdapter: ArrayAdapter<String>
    private val defaultDohServers = setOf(
        "https://cloudflare-dns.com/dns-query",
        "https://dns.google/dns-query",
        "https://dns.quad9.net/dns-query"
    )
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

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
        dohServerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, dohServers)
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
                val currentList = DataStore.dohServersList.toMutableSet()
                currentList.add(newDohUrl)
                DataStore.dohServersList = currentList
                dohServerAdapter.add(newDohUrl)
                dohServerAdapter.notifyDataSetChanged()
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
        val results = mutableMapOf<String, Long>()

        GlobalScope.launch(Dispatchers.IO) {
            val deferredResults = servers.map { serverUrl ->
                async { serverUrl to testDohServer(serverUrl) }
            }
            deferredResults.awaitAll().forEach { (serverUrl, latency) ->
                results[serverUrl] = latency
            }

            withContext(Dispatchers.Main) {
                val sortedResults = results.entries.sortedBy { it.value }
                val updatedServers = mutableListOf<String>()
                var fastestServer: String? = null
                var minLatency = Long.MAX_VALUE

                for ((serverUrl, latency) in sortedResults) {
                    val latencyText = if (latency == -1L) "Error" else "${latency}ms"
                    updatedServers.add("$serverUrl ($latencyText)")

                    if (latency != -1L && latency < minLatency) {
                        minLatency = latency
                        fastestServer = serverUrl
                    }
                }

                dohServerAdapter.clear()
                dohServerAdapter.addAll(updatedServers)
                dohServerAdapter.notifyDataSetChanged()

                if (fastestServer != null) {
                    DataStore.dohServers = fastestServer!!
                    updateSelectedDohServerText()
                } else {
                    DataStore.dohServers = ""
                    updateSelectedDohServerText()
                }
            }
        }
    }

    private suspend fun testDohServer(url: String): Long {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/dns-json")
                .get()
                .build()

            val startTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val endTime = System.currentTimeMillis()

            if (response.isSuccessful) {
                endTime - startTime
            } else {
                -1L // Indicate error
            }
        } catch (e: IOException) {
            -1L // Indicate error
        }
    }
}

