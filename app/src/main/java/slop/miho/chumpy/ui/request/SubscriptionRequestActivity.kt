package slop.miho.chumpy.ui.request

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import slop.miho.chumpy.Graph
import slop.miho.chumpy.data.SettingsStore
import slop.miho.chumpy.db.SubscriptionRequestSettings
import slop.miho.chumpy.data.SpoofMode
import slop.miho.chumpy.ui.components.*
import slop.miho.chumpy.R
import androidx.compose.ui.res.stringResource
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
    onBack: () -> Unit,
    load: suspend () -> SubscriptionRequestSettings,
    save: suspend (SubscriptionRequestSettings) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(initial) }
    androidx.compose.runtime.LaunchedEffect(Unit) { loaded = load() }
    val saveMutex = remember { Mutex() }
    val value = loaded ?: return
    fun commit(next: SubscriptionRequestSettings) {
        loaded = next
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { saveMutex.withLock { save(next) } }
        }
    }
    PreferenceEditor(R.string.subscription_request_title, onBack) {
        Text(stringResource(R.string.request_inherit_hint), style = MaterialTheme.typography.bodyMedium)
        PreferenceSection {
        Text(stringResource(R.string.request_client), style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            labels = listOf(stringResource(R.string.request_inherit), "Happ", "INCY", stringResource(R.string.request_off)),
            selected = when (value.spoofMode) { null -> 0; SpoofMode.HAPP -> 1; SpoofMode.INCY -> 2; SpoofMode.OFF -> 3 },
            onSelect = { index -> commit(value.copy(spoofMode = when (index) { 0 -> null; 1 -> SpoofMode.HAPP; 2 -> SpoofMode.INCY; else -> SpoofMode.OFF })) },
        )
        }
        PreferenceSection {
        Text("HWID", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            labels = listOf(stringResource(R.string.request_inherit), stringResource(R.string.request_send), stringResource(R.string.request_omit)),
            selected = when (value.sendHwid) { null -> 0; true -> 1; false -> 2 },
            onSelect = { index -> commit(value.copy(sendHwid = when (index) { 0 -> null; 1 -> true; else -> false })) },
        )
        }
        RequestField("User-Agent", value.userAgent) { commit(value.copy(userAgent = it)) }
        RequestField(stringResource(R.string.happ_app_version), value.appVersion) { commit(value.copy(appVersion = it)) }
        RequestField(stringResource(R.string.happ_user_id), value.userId) { commit(value.copy(userId = it)) }
        RequestField(stringResource(R.string.happ_hwid), value.hwid) { commit(value.copy(hwid = it)) }
        RequestField(stringResource(R.string.happ_os), value.os) { commit(value.copy(os = it)) }
        RequestField(stringResource(R.string.happ_os_version), value.osVersion) { commit(value.copy(osVersion = it)) }
        RequestField(stringResource(R.string.happ_device_model), value.deviceModel) { commit(value.copy(deviceModel = it)) }
        RequestField(stringResource(R.string.happ_locale), value.locale) { commit(value.copy(locale = it)) }
    }
}

@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    PreferenceChoices(labels, selected, onSelect)
}

@Composable
private fun RequestField(label: String, value: String?, onChange: (String?) -> Unit) {
    var text by remember(value) { mutableStateOf(value.orEmpty()) }
    PreferenceField(
        value = text,
        onValueChange = { text = it; onChange(it.takeIf(String::isNotBlank)) },
        label = label,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
