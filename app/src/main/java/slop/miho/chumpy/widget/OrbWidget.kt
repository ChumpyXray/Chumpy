package slop.miho.chumpy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.VpnService
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import slop.miho.chumpy.ChumpyApp
import slop.miho.chumpy.Graph
import slop.miho.chumpy.MainActivity
import slop.miho.chumpy.R
import slop.miho.chumpy.core.ChumpyVpnService
import slop.miho.chumpy.core.CoreConfigFile
import slop.miho.chumpy.core.TunnelState
import slop.miho.chumpy.data.ConnectionStore
import slop.miho.chumpy.data.SettingsStore
import slop.miho.chumpy.data.ServersSort
import slop.miho.chumpy.data.ThemeMode
import slop.miho.chumpy.data.comparator
import slop.miho.chumpy.db.ProfileType
import slop.miho.chumpy.db.balancerMemberKeys
import slop.miho.chumpy.ui.components.splitLeadingFlag
import slop.miho.chumpy.ui.theme.orbWidgetColors
import slop.miho.chumpy.xray.RoutingMode
import slop.miho.chumpy.xray.XrayConfigBuilder

/**
 * The launcher widget: the orb with the flag, the region and the subscription in its
 * middle, the connect button riding its bottom-right edge, and the pinned servers —
 * flags alone — in squares along the top. The whole picture is one bitmap
 * ([WidgetRenderer]); the taps land on invisible zone views the provider frames to
 * the very pixel boxes that picture paints.
 *
 * The widget lives in the main process, next to the profile database and the repository,
 * and talks to the tunnel the way everything else does. The ":core" process announces
 * every state change with an explicit [ACTION_REPAINT] broadcast, so the orb repaints
 * even when this process was dead between two states.
 *
 * The taps: the button toggles the tunnel (or opens the app when the consent or a
 * config is missing, the QS tile's own hand-off); a pin picks its server and, when the
 * tunnel is up, reloads the running core onto it — the interface's own server switch,
 * the session never dropping; the orb itself opens the app, its circle handing over
 * — the morph [MainActivity] plays when it sees the orb's extra on the way in.
 */
class OrbWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "slop.miho.chumpy.widget.TOGGLE"
        private const val ACTION_PICK = "slop.miho.chumpy.widget.PICK"
        private const val EXTRA_PROFILE_ID = "profile_id"

        /**
         * The repaint telling: whatever changed — the tunnel's state (":core" sends it
         * from [slop.miho.chumpy.core.XrayCore.publish]), the selection, the settings —
         * the orb hears it here and redraws itself.
         */
        const val ACTION_REPAINT = "slop.miho.chumpy.widget.REPAINT"

        /** Broadcasts [ACTION_REPAINT] — the stores' and the core's one-line telling. */
        fun tell(context: Context) {
            context.applicationContext.sendBroadcast(
                Intent(context, OrbWidget::class.java).setAction(ACTION_REPAINT),
            )
        }

        /** How long a freshly born process waits for the binder's first status push. */
        private const val STATE_WAIT_MILLIS = 1_500L

        /** The size the first paint uses before the host has told its own; ~2×2. */
        private const val DEFAULT_SIZE_DP = 140

        /** Request codes that keep the PendingIntends apart. */
        private const val REQUEST_OPEN = 0
        private const val REQUEST_TOGGLE = 1
        private const val REQUEST_PIN_BASE = 10

        private fun allIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, OrbWidget::class.java))
    }

    // ------------------------------------------------------------- routing

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                val single = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                when {
                    ids != null && ids.isNotEmpty() -> goAsyncUpdate(context, ids)
                    single != AppWidgetManager.INVALID_APPWIDGET_ID -> goAsyncUpdate(context, intArrayOf(single))
                    else -> goAsyncUpdate(context, allIds(context))
                }
            }

            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val id = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) goAsyncUpdate(context, intArrayOf(id))
            }

            ACTION_REPAINT -> goAsyncUpdate(context, allIds(context))

            ACTION_TOGGLE -> goAsyncAction(context) {
                toggle(context)
                repaint(context)
            }

            ACTION_PICK -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
                goAsyncAction(context) {
                    pick(context, profileId)
                    repaint(context)
                }
            }
        }
    }

    private fun goAsyncUpdate(context: Context, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                repaint(context, ids)
            } finally {
                pending.finish()
            }
        }
    }

    private fun goAsyncAction(context: Context, action: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                action()
            } finally {
                pending.finish()
            }
        }
    }

    // ------------------------------------------------------------- painting

    private suspend fun repaint(context: Context, ids: IntArray = allIds(context)) {
        if (ids.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val state = currentState(context)
        val face = resolveFace(context, state)
        val pins = resolvePins(context, state)
        val colors = orbWidgetColors(context, widgetDarkTheme(context))
        for (id in ids) {
            val (width, height) = sizeOf(context, manager, id) ?: continue
            val bitmap = WidgetRenderer.render(context, width, height, state, face, pins, colors)
            manager.updateAppWidget(id, buildViews(context, bitmap, state, pins, width, height))
        }
    }

    private fun buildViews(
        context: Context,
        bitmap: android.graphics.Bitmap,
        state: TunnelState,
        pins: List<WidgetPin>,
        width: Int,
        height: Int,
    ): android.widget.RemoteViews {
        val views = android.widget.RemoteViews(context.packageName, R.layout.widget_orb)
        views.setImageViewBitmap(R.id.wgt_visual, bitmap)
        // The orb's own open: the widget hands its circle over, and the app grows out
        // of it — MainActivity plays the morph when it sees this extra.
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).putExtra(
                MainActivity.EXTRA_FROM_ORB,
                true,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.wgt_orb, open)
        // The button toggles when a config and the VPN consent stand; the app's own
        // setup is the only honest answer otherwise — the QS tile's own hand-off.
        val ready = CoreConfigFile.has(context) && VpnService.prepare(context) == null
        if (ready) {
            views.setOnClickPendingIntent(
                R.id.wgt_button,
                broadcast(context, ACTION_TOGGLE, REQUEST_TOGGLE, profileId = null),
            )
            views.setContentDescription(
                R.id.wgt_button,
                context.getString(
                    if (state == TunnelState.UP) R.string.widget_disconnect else R.string.widget_connect,
                ),
            )
        } else {
            views.setOnClickPendingIntent(R.id.wgt_button, open)
            views.setContentDescription(R.id.wgt_button, context.getString(R.string.widget_setup))
        }
        // The pins: a tap picks its server; a slot the row doesn't hold is gone, its
        // corner falling back through to the open-the-app base underneath.
        val shown = pins.size.coerceIn(0, WidgetRenderer.PIN_SLOTS)
        for (slot in 0 until WidgetRenderer.PIN_SLOTS) {
            val viewId = pinId(slot)
            val pin = pins.getOrNull(slot)
            if (slot >= shown || pin == null) {
                views.setViewVisibility(viewId, android.view.View.GONE)
                continue
            }
            views.setViewVisibility(viewId, android.view.View.VISIBLE)
            views.setOnClickPendingIntent(
                viewId,
                broadcast(context, ACTION_PICK, REQUEST_PIN_BASE + slot, pin.id),
            )
            views.setContentDescription(viewId, pin.label)
            frameZone(context, views, width, height, viewId) { w, h -> WidgetRenderer.pinRect(w, h, shown, slot) }
        }
        // The zones follow the paint pixel for pixel — Android 12's setViewLayout
        // family; a pre-12 launcher keeps the layout's static fallback sizes.
        frameZone(context, views, width, height, R.id.wgt_button) { w, h -> WidgetRenderer.buttonBox(w, h) }
        return views
    }

    /**
     * Frames a zone view to the painter's own box: the width and height come straight
     * from the geometry, the margins from the box's distance to the frame's edges —
     * the button pinned to the bottom-right corner, the pins to the top-left. All in
     * exact pixels; the exact-zone setters exist only on Android 12 and up.
     */
    private fun frameZone(
        context: Context,
        views: android.widget.RemoteViews,
        width: Int,
        height: Int,
        viewId: Int,
        box: (Float, Float) -> WidgetRenderer.FrameF?,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val rect = box(width.toFloat(), height.toFloat()) ?: return
        // The setters take their value in dp, so the exact pixels travel through the
        // density unchanged — the zone lands on the very box the painter drew.
        val density = context.resources.displayMetrics.density
        fun dp(px: Float) = px / density
        views.setViewLayoutWidth(viewId, dp(rect.width()), android.util.TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(viewId, dp(rect.height()), android.util.TypedValue.COMPLEX_UNIT_DIP)
        // All four margins, so whichever corner the zone is pinned to, its far edges
        // land on the same box the painter drew.
        views.setViewLayoutMargin(
            viewId, android.widget.RemoteViews.MARGIN_START,
            dp(rect.left), android.util.TypedValue.COMPLEX_UNIT_DIP,
        )
        views.setViewLayoutMargin(
            viewId, android.widget.RemoteViews.MARGIN_TOP,
            dp(rect.top), android.util.TypedValue.COMPLEX_UNIT_DIP,
        )
        views.setViewLayoutMargin(
            viewId, android.widget.RemoteViews.MARGIN_END,
            dp(width - rect.right), android.util.TypedValue.COMPLEX_UNIT_DIP,
        )
        views.setViewLayoutMargin(
            viewId, android.widget.RemoteViews.MARGIN_BOTTOM,
            dp(height - rect.bottom), android.util.TypedValue.COMPLEX_UNIT_DIP,
        )
    }

    private fun pinId(slot: Int): Int = when (slot) {
        0 -> R.id.wgt_pin_0
        1 -> R.id.wgt_pin_1
        2 -> R.id.wgt_pin_2
        3 -> R.id.wgt_pin_3
        else -> R.id.wgt_pin_4
    }

    private fun broadcast(context: Context, action: String, requestCode: Int, profileId: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, OrbWidget::class.java)
                .setAction(action)
                .apply { profileId?.let { putExtra(EXTRA_PROFILE_ID, it) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The host's own size for the widget, in pixels; null until the host has told one. */
    private fun sizeOf(context: Context, manager: AppWidgetManager, id: Int): Pair<Int, Int>? {
        val options = manager.getAppWidgetOptions(id) ?: return null
        val portrait =
            context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val widthDp = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
        )
        val heightDp = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
        )
        val density = context.resources.displayMetrics.density
        val width = ((if (widthDp > 0) widthDp else DEFAULT_SIZE_DP) * density).toInt()
        val height = ((if (heightDp > 0) heightDp else DEFAULT_SIZE_DP) * density).toInt()
        return width to height
    }

    // ------------------------------------------------------------- the taps

    /**
     * The button: up or climbing means down; down means up — the same config the last
     * connect wrote, the QS tile's own arrangement. Busy in the other direction
     * (disconnecting) is answered with silence, like the app's own button.
     */
    private suspend fun toggle(context: Context) {
        when (currentState(context)) {
            TunnelState.UP, TunnelState.CONNECTING -> ChumpyVpnService.startDisconnect(context)
            TunnelState.DISCONNECTING -> Unit
            TunnelState.DOWN ->
                if (CoreConfigFile.has(context) && VpnService.prepare(context) == null) {
                    ChumpyVpnService.startConnect(context)
                }
        }
    }

    /**
     * A pin's tap: the picked server becomes the selection, and a running tunnel
     * reloads onto it in place — the interface's own server switch, the interface
     * never opened, the session never dropping.
     */
    private suspend fun pick(context: Context, profileId: String) {
        val repository = Graph.subscriptions
        val profile = repository.profileById(profileId) ?: return
        if (profile.hidden || profile.concealed) return
        ConnectionStore.update(context) { it.copy(selectedProfileId = profile.id) }
        // The pick is a use: the "last used" order remembers which hand was played.
        repository.setProfileLastUsed(profile.id, System.currentTimeMillis())
        if (currentState(context) in setOf(TunnelState.UP, TunnelState.CONNECTING)) {
            handOffConfig(context, profile)
            ChumpyVpnService.startConnect(context)
        }
    }

    /**
     * The config build-and-hand-off the app's own connect performs, minus the UI: the
     * balancer's members resolved against the store, the geodata prepared, the rendered
     * JSON written for ":core" to read. Under [CoreConfigFile]'s lock, the way the view
     * model keeps its own writes.
     */
    private suspend fun handOffConfig(context: Context, profile: slop.miho.chumpy.db.ProfileEntity) {
        val repository = Graph.subscriptions
        val connection = ConnectionStore.current(context)
        val mask = SettingsStore.settings(context).value.maskAddresses
        val members = if (profile.type == ProfileType.BALANCER) {
            val keys = profile.balancerMemberKeys.toSet()
            val rows = repository.profilesOf(profile.subscriptionId).first()
            val resolved = rows.filter { it.type != ProfileType.BALANCER && it.linkKey in keys }
            resolved.ifEmpty { rows.filter { it.type != ProfileType.BALANCER } }
        } else {
            emptyList()
        }
        val config = repository.prepareGeodata(
            profile,
            XrayConfigBuilder.buildFull(
                profile,
                connection.routingMode,
                members,
                connection.delayTestUrl,
            ),
            connection.routingMode == RoutingMode.GLOBAL && profile.routingRules.isNotBlank(),
        )
        val subscriptionName = profile.subscriptionId?.let { repository.subscriptionById(it)?.name }
        synchronized(CoreConfigFile) {
            CoreConfigFile.write(
                context,
                config,
                profile.remarks.ifEmpty { if (mask) "" else profile.server },
                subscriptionName,
                profile.id,
                profile.subscriptionId,
                profile.server,
            )
        }
    }

    // ------------------------------------------------------------- the data

    /**
     * The tunnel's state, the honest way: a process that has already talked to ":core"
     * knows the truth in its flow; a freshly born one waits for the binder's first push
     * (the registration always answers with the live state) and falls back to the seed
     * if the link never lands.
     */
    private suspend fun currentState(context: Context): TunnelState {
        val client = ChumpyApp.tunnelClient
        if (client.isServiceConnected()) return client.status.value.state
        return withTimeoutOrNull(STATE_WAIT_MILLIS) {
            client.status.drop(1).first().state
        } ?: client.status.value.state
    }

    /**
     * What the orb says: while the tunnel runs, the session the running config was born
     * from (the snapshot ":core" reads for its own notification); otherwise the
     * selection, resolved the way Home's card resolves it — the flag lifted off the
     * name, the address kept behind the mask when the curtain is drawn.
     */
    private suspend fun resolveFace(context: Context, state: TunnelState): WidgetFace {
        val mask = SettingsStore.settings(context).value.maskAddresses
        if (state == TunnelState.UP || state == TunnelState.CONNECTING) {
            val session = CoreConfigFile.snapshot(context)
            if (session != null) {
                val raw = session.profileName?.takeIf { it.isNotBlank() }
                    ?: session.server?.takeIf { !mask }
                val (flag, label) = raw?.let { splitLeadingFlag(it.trim()) } ?: (null to "")
                return WidgetFace(
                    flag,
                    label.ifBlank { context.getString(R.string.notif_profile_unknown) },
                    session.subscriptionName,
                )
            }
        }
        val connection = ConnectionStore.current(context)
        val profile = connection.selectedProfileId?.let { Graph.subscriptions.profileById(it) }
        val subscription = profile?.subscriptionId?.let { Graph.subscriptions.subscriptionById(it) }
        val name = profile?.remarks.orEmpty()
            .ifEmpty { if (mask) "" else profile?.server.orEmpty() }
        val (flag, label) = splitLeadingFlag(name.trim())
        return WidgetFace(
            flag,
            label.ifBlank {
                if (profile != null) context.getString(R.string.notif_profile_unknown)
                else context.getString(R.string.app_name)
            },
            subscription?.name ?: if (profile == null) context.getString(R.string.widget_empty) else null,
        )
    }

    /**
     * The pinned squares: the selected subscription's own pins, the same order its list
     * keeps, the first five. The active one — the running session's while up, the
     * selection's while down — tells itself apart by its fill.
     */
    private suspend fun resolvePins(context: Context, state: TunnelState): List<WidgetPin> {
        val connection = ConnectionStore.current(context)
        val mask = SettingsStore.settings(context).value.maskAddresses
        val rows = Graph.subscriptions.profilesOf(connection.selectedSubscriptionId).first()
        val activeId = if (state == TunnelState.UP || state == TunnelState.CONNECTING) {
            CoreConfigFile.snapshot(context)?.profileId
        } else {
            connection.selectedProfileId
        }
        return rows
            .filter { it.pinned && !it.hidden && !it.concealed }
            .sortedWith(connection.serversSort.comparator())
            .take(WidgetRenderer.PIN_SLOTS)
            .map { profile ->
                val name = profile.remarks.ifEmpty { if (mask) "" else profile.server }
                val (flag, label) = splitLeadingFlag(name.trim())
                WidgetPin(
                    id = profile.id,
                    flag = flag,
                    label = label.ifBlank { context.getString(R.string.notif_profile_unknown) },
                    active = profile.id == activeId,
                )
            }
    }

    private fun widgetDarkTheme(context: Context): Boolean =
        when (SettingsStore.settings(context).value.themeMode) {
            ThemeMode.SYSTEM ->
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK, ThemeMode.AMOLED -> true
        }
}
