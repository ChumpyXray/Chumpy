package slop.miho.chumpy.ui.request

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import slop.miho.chumpy.Graph
import slop.miho.chumpy.R
import slop.miho.chumpy.data.SettingsStore
import slop.miho.chumpy.data.SpoofMode
import slop.miho.chumpy.data.UiSettings
import slop.miho.chumpy.db.SubscriptionRequestSettings
import slop.miho.chumpy.sub.HappSpoof
import slop.miho.chumpy.sub.HappSpoof.happFingerprint
import slop.miho.chumpy.ui.components.ControllerActivity
import slop.miho.chumpy.ui.components.HappFieldRow
import slop.miho.chumpy.ui.components.IconChoice
import slop.miho.chumpy.ui.components.IconChoiceRow
import slop.miho.chumpy.ui.components.PreferenceEditor
import slop.miho.chumpy.ui.components.SettingsGroup
import slop.miho.chumpy.ui.components.ToggleRow
import slop.miho.chumpy.ui.theme.ChumpyTheme

/** Per-row request identity: inherited fields stay global, explicit values belong to this row. */
class SubscriptionRequestActivity : ControllerActivity() {
    companion object { const val EXTRA_ID = "subscription_id" }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        setContent {
            val ui by SettingsStore.settings(this).collectAsStateWithLifecycle()
            ChumpyTheme(settings = ui) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    RequestSettingsScreen(
                        initial = null,
                        global = ui,
                        onBack = ::finish,
                        load = { id?.let { Graph.subscriptions.subscriptionById(it)?.requestSettings } ?: SubscriptionRequestSettings() },
                        save = { settings -> if (id != null) Graph.subscriptions.setSubscriptionRequestSettings(id, settings) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestSettingsScreen(
    initial: SubscriptionRequestSettings?,
    global: UiSettings,
    onBack: () -> Unit,
    load: suspend () -> SubscriptionRequestSettings,
    save: suspend (SubscriptionRequestSettings) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(initial) }
    var failure by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try { loaded = load() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.e("Chumpy/Request", "Could not load request settings", e); failure = true }
    }
    val saveMutex = remember { Mutex() }
    val value = loaded
    if (value == null) {
        PreferenceEditor(R.string.subscription_request_title, onBack) {
            if (failure) Text(stringResource(R.string.request_save_error), color = MaterialTheme.colorScheme.error)
            else CircularProgressIndicator()
        }
        return
    }
    fun commit(next: SubscriptionRequestSettings) {
        loaded = next
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                saveMutex.withLock {
                    try { save(next); failure = false }
                    catch (e: Exception) {
                        android.util.Log.e("Chumpy/Request", "Could not save request settings", e)
                        failure = true
                    }
                }
            }
        }
    }
    // A custom request is any field set; all null means the row follows the global
    // fingerprint — the toggle's "Default".
    val custom = value != SubscriptionRequestSettings()
    val effectiveMode = value.spoofMode ?: global.spoofMode
    val base = global.copy(spoofMode = if (global.spoofMode == SpoofMode.OFF) SpoofMode.HAPP else global.spoofMode)
        .happFingerprint()!!
    val effective = HappSpoof.override(global.happFingerprint(), value.copy(
        spoofMode = if (effectiveMode == SpoofMode.OFF) SpoofMode.HAPP else effectiveMode,
    ), base)!!
    val userAgent = HappSpoof.headers(effective).getValue("User-Agent")
    PreferenceEditor(R.string.subscription_request_title, onBack) {
        if (failure) Text(stringResource(R.string.request_save_error), color = MaterialTheme.colorScheme.error)
        // The screen's one doorway: off, the row asks with the app's own fingerprint
        // and there is nothing to edit; on, the row's own request stands below.
        SettingsGroup(
            {
                ToggleRow(
                    title = stringResource(R.string.request_custom_hwid),
                    supporting = if (custom) null else stringResource(R.string.request_custom_hwid_off),
                    icon = R.drawable.ic_tune,
                    checked = custom,
                    onChange = { enabled ->
                        commit(
                            if (enabled) {
                                // Materialize the doorway the fields ride on: the mode
                                // and the HWID's send flag take the global values, the
                                // strings stay inherited until edited.
                                value.copy(
                                    spoofMode = value.spoofMode ?: global.spoofMode,
                                    sendHwid = value.sendHwid ?: global.happSendHwid,
                                )
                            } else {
                                SubscriptionRequestSettings()
                            },
                        )
                    },
                )
            },
        )
        // The custom request's own sections stay on the screen while the doorway is
        // shut — asleep, dimmed, showing the fingerprint the row would ride on.
        SettingsGroup(
            {
                IconChoiceRow(
                    title = stringResource(R.string.request_client),
                    showSelectedLabel = true,
                    options = listOf(
                        IconChoice(R.drawable.ic_happ, R.string.spoof_as_happ, SpoofMode.HAPP),
                        IconChoice(R.string.spoof_as_incy, SpoofMode.INCY) {
                            Icon(
                                painterResource(R.drawable.ic_incy),
                                contentDescription = null,
                                modifier = Modifier.size(width = 36.dp, height = 24.dp),
                            )
                        },
                        IconChoice(R.drawable.ic_block, R.string.per_app_disabled, SpoofMode.OFF),
                    ),
                    selected = effectiveMode,
                    onSelect = { mode -> commit(value.copy(spoofMode = mode)) },
                    enabled = custom,
                )
            },
            {
                ToggleRow(
                    title = stringResource(R.string.happ_send_hwid),
                    supporting = stringResource(
                        if (value.sendHwid ?: global.happSendHwid) {
                            R.string.happ_send_hwid_on
                        } else {
                            R.string.happ_send_hwid_off
                        },
                    ),
                    icon = R.drawable.ic_device,
                    checked = value.sendHwid ?: global.happSendHwid,
                    onChange = { enabled -> commit(value.copy(sendHwid = enabled)) },
                    enabled = custom,
                )
            },
        )
        // The request's own strings, one field a row, the effective spoof shown
        // while a field stays inherited; clearing a field hands it back. The HWID's
        // own field sleeps while the request carries no HWID at all — nothing it
        // says would leave the device.
        val sendHwid = value.sendHwid ?: global.happSendHwid
        SettingsGroup(
            { RequestFieldRow("User-Agent", value.userAgent ?: userAgent, custom) { commit(value.copy(userAgent = it)) } },
            { RequestFieldRow(stringResource(R.string.happ_app_version), value.appVersion ?: effective.appVersion, custom) { commit(value.copy(appVersion = it)) } },
            { RequestFieldRow(stringResource(R.string.happ_user_id), value.userId ?: effective.userId, custom) { commit(value.copy(userId = it)) } },
            { RequestFieldRow(stringResource(R.string.happ_hwid), value.hwid ?: effective.hwid, custom && sendHwid) { commit(value.copy(hwid = it)) } },
            { RequestFieldRow(stringResource(R.string.happ_os), value.os ?: effective.os, custom) { commit(value.copy(os = it)) } },
            { RequestFieldRow(stringResource(R.string.happ_os_version), value.osVersion ?: effective.osVersion, custom) { commit(value.copy(osVersion = it)) } },
            { RequestFieldRow(stringResource(R.string.happ_device_model), value.deviceModel ?: effective.deviceModel, custom) { commit(value.copy(deviceModel = it)) } },
            { RequestFieldRow(stringResource(R.string.happ_locale), value.locale ?: effective.locale, custom) { commit(value.copy(locale = it)) } },
        )
    }
}

/** One request string on the settings' own field row: an emptied field inherits again. */
@Composable
private fun RequestFieldRow(label: String, value: String, enabled: Boolean, onChange: (String?) -> Unit) {
    HappFieldRow(
        label = label,
        value = value,
        onChange = { onChange(it.takeIf(String::isNotBlank)) },
        enabled = enabled,
    )
}
