package slop.miho.chumpy.xray

import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import slop.miho.chumpy.db.ProfileEntity

internal object NaiveTunnelConfig {
    private const val SECTION = "chumpyNaive"
    private const val PROXY = "proxy"
    private const val DNS = "naive-dns"
    private const val DOH = "naive-doh"
    private const val LOOPBACK = "127.0.0.1"

    data class Bridge(val xray: String, val singBox: String)

    fun outbound(profile: ProfileEntity): JsonObject = buildJsonObject {
        put("type", "naive")
        put("tag", PROXY)
        put("server", profile.server)
        put("server_port", profile.serverPort)
        put("username", profile.username)
        put("password", profile.password)
        put("quic", profile.network == "quic")
        put("tls", buildJsonObject { put("enabled", true) })
    }

    fun buildProbe(profile: ProfileEntity): String = buildJsonObject {
        put("log", buildJsonObject { put("disabled", true) })
        put("outbounds", buildJsonArray { add(outbound(profile)) })
    }.toString()

    fun isProbe(config: String): Boolean = config.contains("\"type\":\"naive\"")

    fun attach(config: JsonObject, profile: ProfileEntity): String =
        JsonObject(config + (SECTION to outbound(profile))).toString()

    fun prepareBridge(config: String): Bridge? {
        if (!config.contains("\"$SECTION\"")) return null
        val root = Json.parseToJsonElement(config) as? JsonObject ?: return null
        val naive = root[SECTION] as? JsonObject ?: return null
        val port = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }
        val password = UUID.randomUUID().toString()
        val singBox = buildJsonObject {
            put("log", buildJsonObject { put("disabled", true) })
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("type", "socks")
                    put("listen", LOOPBACK)
                    put("listen_port", port)
                    put("users", buildJsonArray {
                        add(buildJsonObject {
                            put("username", PROXY)
                            put("password", password)
                        })
                    })
                })
            })
            put("outbounds", buildJsonArray { add(naive) })
        }
        val xrayOutbounds = root.getValue("outbounds") as JsonArray
        val bridge = buildJsonObject {
            put("tag", PROXY)
            put("protocol", "socks")
            put("settings", buildJsonObject {
                put("address", LOOPBACK)
                put("port", port)
                put("user", PROXY)
                put("pass", password)
            })
        }
        val dnsOutbound = buildJsonObject {
            put("tag", DNS)
            put("protocol", "dns")
        }
        val routing = root.getValue("routing") as JsonObject
        val rules = routing.getValue("rules") as JsonArray
        val dnsRules = buildJsonArray {
            add(buildJsonObject {
                put("type", "field")
                put("inboundTag", JsonArray(listOf(JsonPrimitive(DOH))))
                put("outboundTag", PROXY)
            })
            add(buildJsonObject {
                put("type", "field")
                put("port", "53")
                put("outboundTag", DNS)
            })
        }
        val dns = buildJsonObject {
            put("tag", DOH)
            put("servers", JsonArray(listOf(JsonPrimitive("https://1.1.1.1/dns-query"))))
        }
        val xray = JsonObject(root.minus(SECTION) + mapOf(
            "outbounds" to JsonArray(listOf(bridge, dnsOutbound) + xrayOutbounds),
            "routing" to JsonObject(routing + ("rules" to JsonArray(dnsRules + rules))),
            "dns" to dns,
        ))
        return Bridge(xray.toString(), singBox.toString())
    }
}
