package slop.miho.chumpy.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import slop.miho.chumpy.xray.RoutingMode
import slop.miho.chumpy.xray.XrayConfigBuilder
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the per-app list means: the picked apps stay out of the tunnel, or — the inverse —
 * only the picked apps ride it while everything else stays on the physical network.
 */
enum class PerAppMode { OFF, BYPASS, ONLY }

enum class CustomRouteStage { BLOCKED, DIRECT, PROXY }

/**
 * A standalone routing policy: its ordered stages replace all preset and
 * panel routing when enabled. Database URLs override the bundled geodata sources.
 */
data class CustomRouting(
    val enabled: Boolean = false,
    val blocked: List<String> = emptyList(),
    val direct: List<String> = emptyList(),
    val proxy: List<String> = emptyList(),
    val customGeosite: Boolean = false,
    val customGeoip: Boolean = false,
    val geodataRevision: Long = 0,
    val geositeUrl: String = "",
    val geoipUrl: String = "",
    val stageOrder: List<CustomRouteStage> = CustomRouteStage.entries.toList(),
) {
    val entryCount: Int get() = blocked.size + direct.size + proxy.size

    /** The logical database name to the imported file, for [GeodataFiles.prepare]. */
    val geodataOverrides: Map<String, String>
        get() = if (!enabled) emptyMap() else buildMap {
            if (customGeosite) put("geosite.dat", GeodataFiles.USER_GEOSITE_FILE)
            if (customGeoip) put("geoip.dat", GeodataFiles.USER_GEOIP_FILE)
        }

    val geodataSources: Map<String, String>
        get() = if (!enabled) emptyMap() else buildMap {
            geositeUrl.trim().takeIf(String::isNotEmpty)?.let { put("geosite.dat", it) }
            geoipUrl.trim().takeIf(String::isNotEmpty)?.let { put("geoip.dat", it) }
        }
}

/**
 * Connection preferences — the selected subscription and profile, the routing mode and
 * the delay test URL. Kept apart from [SettingsStore] (the interface's own settings) the
 * same way the connection screens are apart from the interface ones.
 */
data class ConnectionSettings(
    /** The subscription whose servers the servers screen shows; null until one exists. */
    val selectedSubscriptionId: String? = null,
    /** The profile a connect uses; restored across restarts. */
    val selectedProfileId: String? = null,
    val routingMode: RoutingMode = RoutingMode.DISABLED,
    /** The servers list's own order — the header's chip picks it, and it sticks. */
    val serversSort: ServersSort = ServersSort.DEFAULT,
    /** The URL real delay measurements fetch through the tested outbound. */
    val delayTestUrl: String = XrayConfigBuilder.DEFAULT_TEST_URL,
    /** Packages the tunnel leaves alone — the per-app bypass list, v2rayNG's arrangement. */
    val bypassedApps: Set<String> = emptySet(),
    /** The per-app proxy's own master switch: off, and the whole list stops applying. */
    val perAppEnabled: Boolean = true,
    /** The inverse list: only [bypassedApps] ride the tunnel, everything else stays out. */
    val perAppOnly: Boolean = false,
    /**
     * Whether the system's own demand for the VPN — the always-on restart, the sticky
     * resurrection after the core process died — brings the tunnel back by itself. Off
     * (the default): a fall stands, the alert notification's Reconnect being the way
     * back by the user's own hand.
     */
    val autoReconnect: Boolean = false,
    /**
     * Whether the local network stays out of the tunnel — the LAN-friendly default.
     * On, the tunnel claims the public internet only and LAN traffic rides the
     * physical network as if the VPN were off; off, the one 0.0.0.0/0 route swallows
     * everything again. The core reads it fresh off disk when a tunnel starts.
     */
    val bypassLan: Boolean = true,
    val randomLocalPort: Boolean = true,
    val localProxyPort: Int = XrayConfigBuilder.LOCAL_SOCKS_PORT,
    /** The user's own routing lists and databases; off, the mode above decides alone. */
    val customRouting: CustomRouting = CustomRouting(),
) {
    /** The per-app list's meaning, read off its two switches. */
    val perAppMode: PerAppMode
        get() = when {
            !perAppEnabled -> PerAppMode.OFF
            perAppOnly -> PerAppMode.ONLY
            else -> PerAppMode.BYPASS
        }

    /** The two switches set together — the mode picker's one write. */
    fun withPerAppMode(mode: PerAppMode): ConnectionSettings = copy(
        perAppEnabled = mode != PerAppMode.OFF,
        perAppOnly = mode == PerAppMode.ONLY,
    )
}

object ConnectionStore {
    private const val PREFS = "connection_settings"

    private val state = MutableStateFlow(ConnectionSettings())
    private var seeded = false

    @Synchronized
    fun settings(context: Context): StateFlow<ConnectionSettings> {
        if (!seeded) {
            state.value = current(context)
            writeSnapshot(context, state.value)
            seeded = true
        }
        return state.asStateFlow()
    }

    @Synchronized
    fun update(context: Context, transform: (ConnectionSettings) -> ConnectionSettings) {
        val updated = transform(settings(context).value)
        write(context, updated)
        state.value = updated
        // The launcher orb shows the selection and its pins; a write here is its
        // repaint telling.
        slop.miho.chumpy.widget.OrbWidget.tell(context)
    }

    /**
     * The settings straight off disk. The VPN service reads the bypass list from the
     * :core process, whose in-memory copy would go stale the moment the UI process
     * writes a new one.
     */
    fun current(context: Context): ConnectionSettings = runCatching {
        val json = JSONObject(AtomicSnapshot.read(snapshot(context)) ?: error("No published connection settings"))
        ConnectionSettings(
            selectedSubscriptionId = json.optString(KEY_SUB).takeIf { it.isNotEmpty() },
            selectedProfileId = json.optString(KEY_PROFILE).takeIf { it.isNotEmpty() },
            routingMode = RoutingMode.entries.firstOrNull { it.name == json.optString(KEY_ROUTING) }
                ?: RoutingMode.DISABLED,
            serversSort = json.optString(KEY_SERVERS_SORT)
                .let { stored -> ServersSort.entries.firstOrNull { it.name == stored } }
                ?: ServersSort.DEFAULT,
            delayTestUrl = json.optString(KEY_TEST_URL).ifBlank { XrayConfigBuilder.DEFAULT_TEST_URL },
            bypassedApps = json.optJSONArray(KEY_BYPASS_APPS)?.let { apps ->
                (0 until apps.length()).map { apps.getString(it) }.toSet()
            } ?: emptySet(),
            perAppEnabled = json.optBoolean(KEY_PER_APP, true),
            perAppOnly = json.optBoolean(KEY_PER_APP_ONLY, false),
            autoReconnect = json.optBoolean(KEY_AUTO_RECONNECT, false),
            bypassLan = json.optBoolean(KEY_BYPASS_LAN, true),
            customRouting = json.optJSONObject(KEY_CUSTOM_ROUTING)?.let(::customRoutingFromJson)
                ?: CustomRouting(),
            randomLocalPort = json.optBoolean("random_local_port", true),
            localProxyPort = json.optInt("local_proxy_port", XrayConfigBuilder.LOCAL_SOCKS_PORT)
                .takeIf { it in 1024..65535 } ?: XrayConfigBuilder.LOCAL_SOCKS_PORT,
        )
    }.getOrElse { read(context) }

    /**
     * The auto-reconnect flag read straight off the prefs *file*, not this process's
     * SharedPreferences cache — which never sees the UI process's writes. The flag rules
     * the mid-flight decisions (a handover re-dials or falls by it), so it must be the
     * value the user last set, not the one this process loaded at birth. Absent from the
     * file, it is the default: off.
     */
    fun autoReconnectFresh(context: Context): Boolean {
        return current(context).autoReconnect
    }

    private fun snapshot(context: Context) = File(context.filesDir, "connection-settings.json")

    private fun writeSnapshot(context: Context, settings: ConnectionSettings) {
        val json = JSONObject()
            .put(KEY_SUB, settings.selectedSubscriptionId.orEmpty())
            .put(KEY_PROFILE, settings.selectedProfileId.orEmpty())
            .put(KEY_ROUTING, settings.routingMode.name)
            .put(KEY_SERVERS_SORT, settings.serversSort.name)
            .put(KEY_TEST_URL, settings.delayTestUrl)
            .put(KEY_BYPASS_APPS, JSONArray(settings.bypassedApps.sorted()))
            .put(KEY_PER_APP, settings.perAppEnabled)
            .put(KEY_PER_APP_ONLY, settings.perAppOnly)
            .put(KEY_AUTO_RECONNECT, settings.autoReconnect)
            .put(KEY_BYPASS_LAN, settings.bypassLan)
            .put(KEY_CUSTOM_ROUTING, customRoutingToJson(settings.customRouting))
            .put("random_local_port", settings.randomLocalPort)
            .put("local_proxy_port", settings.localProxyPort)
        AtomicSnapshot.write(snapshot(context), json.toString())
    }

    private fun read(context: Context): ConnectionSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ConnectionSettings(
            selectedSubscriptionId = prefs.getString(KEY_SUB, null),
            selectedProfileId = prefs.getString(KEY_PROFILE, null),
            routingMode = prefs.getString(KEY_ROUTING, null)
                // "RU_BYPASS" is the old spelling of the Russia bypass from before the
                // region list grew; carried over so an update keeps the pick.
                ?.let { stored ->
                    RoutingMode.entries.firstOrNull { it.name == stored }
                        ?: if (stored == "RU_BYPASS") RoutingMode.BYPASS_RU else null
                }
                ?: RoutingMode.DISABLED,
            serversSort = prefs.getString(KEY_SERVERS_SORT, null)
                ?.let { stored -> ServersSort.entries.firstOrNull { it.name == stored } }
                ?: ServersSort.DEFAULT,
            delayTestUrl = prefs.getString(KEY_TEST_URL, null)?.takeIf { it.isNotBlank() }
                ?: XrayConfigBuilder.DEFAULT_TEST_URL,
            bypassedApps = prefs.getStringSet(KEY_BYPASS_APPS, null)?.toSet() ?: emptySet(),
            perAppEnabled = prefs.getBoolean(KEY_PER_APP, true),
            perAppOnly = prefs.getBoolean(KEY_PER_APP_ONLY, false),
            autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, false),
            bypassLan = prefs.getBoolean(KEY_BYPASS_LAN, true),
            customRouting = prefs.getString(KEY_CUSTOM_ROUTING, null)
                ?.let { runCatching { customRoutingFromJson(JSONObject(it)) }.getOrNull() }
                ?: CustomRouting(),
            randomLocalPort = prefs.getBoolean("random_local_port", true),
            localProxyPort = prefs.getInt("local_proxy_port", XrayConfigBuilder.LOCAL_SOCKS_PORT)
                .takeIf { it in 1024..65535 } ?: XrayConfigBuilder.LOCAL_SOCKS_PORT,
        )
    }

    private fun write(context: Context, settings: ConnectionSettings) {
        writeSnapshot(context, settings)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .also { editor ->
                val sub = settings.selectedSubscriptionId
                if (sub != null) editor.putString(KEY_SUB, sub) else editor.remove(KEY_SUB)
                val profile = settings.selectedProfileId
                if (profile != null) editor.putString(KEY_PROFILE, profile) else editor.remove(KEY_PROFILE)
                if (settings.bypassedApps.isEmpty()) {
                    editor.remove(KEY_BYPASS_APPS)
                } else {
                    editor.putStringSet(KEY_BYPASS_APPS, settings.bypassedApps)
                }
            }
            .putString(KEY_ROUTING, settings.routingMode.name)
            .putString(KEY_SERVERS_SORT, settings.serversSort.name)
            .putString(KEY_TEST_URL, settings.delayTestUrl)
            .putBoolean(KEY_PER_APP, settings.perAppEnabled)
            .putBoolean(KEY_PER_APP_ONLY, settings.perAppOnly)
            .putBoolean(KEY_AUTO_RECONNECT, settings.autoReconnect)
            .putBoolean(KEY_BYPASS_LAN, settings.bypassLan)
            .putString(KEY_CUSTOM_ROUTING, customRoutingToJson(settings.customRouting).toString())
            .putBoolean("random_local_port", settings.randomLocalPort)
            .putInt("local_proxy_port", settings.localProxyPort)
            .apply()
    }

    private fun customRoutingToJson(custom: CustomRouting): JSONObject = JSONObject()
        .put(KEY_CUSTOM_ENABLED, custom.enabled)
        .put(KEY_CUSTOM_BLOCKED, JSONArray(custom.blocked))
        .put(KEY_CUSTOM_DIRECT, JSONArray(custom.direct))
        .put(KEY_CUSTOM_PROXY, JSONArray(custom.proxy))
        .put(KEY_CUSTOM_GEOSITE, custom.customGeosite)
        .put(KEY_CUSTOM_GEOIP, custom.customGeoip)
        .put("geodata_revision", custom.geodataRevision)
        .put(KEY_CUSTOM_GEOSITE_URL, custom.geositeUrl)
        .put(KEY_CUSTOM_GEOIP_URL, custom.geoipUrl)
        .put(KEY_CUSTOM_STAGE_ORDER, JSONArray(custom.stageOrder.map { it.name }))

    private fun customRoutingFromJson(json: JSONObject): CustomRouting = CustomRouting(
        enabled = json.optBoolean(KEY_CUSTOM_ENABLED, false),
        blocked = json.optJSONArray(KEY_CUSTOM_BLOCKED)?.strings() ?: emptyList(),
        direct = json.optJSONArray(KEY_CUSTOM_DIRECT)?.strings() ?: emptyList(),
        proxy = json.optJSONArray(KEY_CUSTOM_PROXY)?.strings() ?: emptyList(),
        customGeosite = json.optBoolean(KEY_CUSTOM_GEOSITE, false),
        customGeoip = json.optBoolean(KEY_CUSTOM_GEOIP, false),
        geodataRevision = json.optLong("geodata_revision", 0),
        geositeUrl = json.optString(KEY_CUSTOM_GEOSITE_URL),
        geoipUrl = json.optString(KEY_CUSTOM_GEOIP_URL),
        stageOrder = json.optJSONArray(KEY_CUSTOM_STAGE_ORDER)?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                CustomRouteStage.entries.firstOrNull { it.name == array.optString(index) }
            }.distinct()
        }?.takeIf { it.size == CustomRouteStage.entries.size } ?: CustomRouteStage.entries.toList(),
    )

    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

    private const val KEY_SUB = "selected_subscription"
    private const val KEY_PROFILE = "selected_profile"
    private const val KEY_ROUTING = "routing_mode"
    private const val KEY_SERVERS_SORT = "servers_sort"
    private const val KEY_TEST_URL = "delay_test_url"
    private const val KEY_BYPASS_APPS = "bypassed_apps"
    private const val KEY_PER_APP = "per_app_enabled"
    private const val KEY_PER_APP_ONLY = "per_app_only"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_BYPASS_LAN = "bypass_lan"
    private const val KEY_CUSTOM_ROUTING = "custom_routing"
    private const val KEY_CUSTOM_ENABLED = "enabled"
    private const val KEY_CUSTOM_BLOCKED = "blocked"
    private const val KEY_CUSTOM_DIRECT = "direct"
    private const val KEY_CUSTOM_PROXY = "proxy"
    private const val KEY_CUSTOM_GEOSITE = "custom_geosite"
    private const val KEY_CUSTOM_GEOIP = "custom_geoip"
    private const val KEY_CUSTOM_GEOSITE_URL = "geosite_url"
    private const val KEY_CUSTOM_GEOIP_URL = "geoip_url"
    private const val KEY_CUSTOM_STAGE_ORDER = "stage_order"
}
