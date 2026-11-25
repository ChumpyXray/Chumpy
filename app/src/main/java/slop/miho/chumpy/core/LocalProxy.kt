package slop.miho.chumpy.core

import android.content.Context
import org.json.JSONObject
import slop.miho.chumpy.data.AtomicSnapshot
import slop.miho.chumpy.data.ConnectionStore
import slop.miho.chumpy.xray.XrayConfigBuilder
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

internal object LocalProxy {
    private lateinit var application: Context

    fun init(context: Context) {
        application = context.applicationContext
    }

    private fun portFile() = File(application.filesDir, "local-proxy-port")

    fun currentPort(): Int = AtomicSnapshot.read(portFile())?.toIntOrNull()
        ?.takeIf { it in 1024..65535 } ?: XrayConfigBuilder.LOCAL_SOCKS_PORT

    fun prepare(config: String): Pair<String, Int> {
        val settings = ConnectionStore.current(application)
        val port = if (settings.randomLocalPort) {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        } else settings.localProxyPort
        val json = JSONObject(config)
        val inbounds = json.getJSONArray("inbounds")
        var found = false
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.getJSONObject(index)
            if (inbound.optString("protocol") == "socks" && inbound.optString("listen") == "127.0.0.1") {
                inbound.put("port", port)
                found = true
            }
        }
        check(found) { "Local SOCKS inbound is missing" }
        return json.toString() to port
    }

    fun publish(port: Int) {
        AtomicSnapshot.write(portFile(), port.toString())
    }
}
