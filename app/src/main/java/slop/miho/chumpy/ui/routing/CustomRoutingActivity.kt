package slop.miho.chumpy.ui.routing

import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import slop.miho.chumpy.data.ConnectionStore
import slop.miho.chumpy.data.CustomRouting
import slop.miho.chumpy.data.CustomRouteStage
import slop.miho.chumpy.data.GeodataFiles
import slop.miho.chumpy.Graph
import slop.miho.chumpy.ui.components.*
import slop.miho.chumpy.R
import slop.miho.chumpy.ui.theme.ChumpyTheme
import java.io.File

private enum class CategorySource { GEOSITE, GEOIP }

private data class GeodataPreset(val name: String, val geosite: String, val geoip: String = "")

private val geodataPresets = listOf(
    GeodataPreset(
        "v2fly/domain-list-community",
        "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat",
    ),
    GeodataPreset(
        "Chocolate4U/Iran-v2ray-rules",
        "https://raw.githubusercontent.com/Chocolate4U/Iran-v2ray-rules/release/geosite.dat",
        "https://raw.githubusercontent.com/Chocolate4U/Iran-v2ray-rules/release/geoip.dat",
    ),
    GeodataPreset(
        "runetfreedom/russia-v2ray-rules-dat",
        "https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/geosite.dat",
        "https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/geoip.dat",
    ),
)

/** The custom domain/IP rules and the optional user-supplied Xray geodata databases. */
internal class RoutingDraft : ViewModel() {
    var value by mutableStateOf<CustomRouting?>(null)
}

class CustomRoutingActivity : ControllerActivity() {
    private val draft by viewModels<RoutingDraft>()
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (draft.value == null) draft.value = ConnectionStore.settings(this).value.customRouting
        setContent {
            val ui by slop.miho.chumpy.data.SettingsStore.settings(this).collectAsStateWithLifecycle()
            val connection by ConnectionStore.settings(this).collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            ChumpyTheme(settings = ui) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    CustomRoutingScreen(
                        value = draft.value ?: connection.customRouting,
                        dirty = draft.value != connection.customRouting,
                        onSave = {
                            val saved = draft.value ?: return@CustomRoutingScreen
                            scope.launch { withContext(Dispatchers.IO) {
                                ConnectionStore.update(this@CustomRoutingActivity) { it.copy(customRouting = saved) }
                            } }
                        },
                        onBack = ::finish,
                        onChange = { value -> draft.value = value },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomRoutingScreen(value: CustomRouting, dirty: Boolean, onSave: () -> Unit, onBack: () -> Unit, onChange: (CustomRouting) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentValue by androidx.compose.runtime.rememberUpdatedState(value)
    var importing by remember { mutableStateOf(false) }
    var importGeoip by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var geositeCategories by remember { mutableStateOf(emptyList<String>()) }
    var geoipCategories by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(value.geositeUrl, value.geoipUrl, value.customGeosite, value.customGeoip) {
        geositeCategories = emptyList()
        geoipCategories = emptyList()
        val catalogs = withContext(Dispatchers.IO) {
            fun load(url: String, custom: Boolean, customName: String, bundledName: String): List<String> {
                return runCatching {
                    if (custom) {
                        val file = File(File(context.filesDir, "xray_assets"), customName)
                        if (!file.isFile || file.length() !in 1..GeodataFiles.MAX_SOURCE_BYTES) return@runCatching emptyList()
                        file.inputStream().use { GeodataFiles.categories(it, GeodataFiles.MAX_SOURCE_BYTES).toList().sorted() }
                    } else if (url.isNotBlank()) {
                        val file = GeodataFiles.catalogFile(context, url)
                        if (!file.isFile || file.length() !in 1..GeodataFiles.MAX_SOURCE_BYTES) return@runCatching emptyList()
                        file.inputStream().use { GeodataFiles.categories(it, GeodataFiles.MAX_SOURCE_BYTES).toList().sorted() }
                    } else {
                        context.assets.open(bundledName).use { GeodataFiles.categories(it, GeodataFiles.MAX_SOURCE_BYTES).toList().sorted() }
                    }
                }.getOrDefault(emptyList())
            }
            load(value.geositeUrl, value.customGeosite, GeodataFiles.USER_GEOSITE_FILE, "geosite.dat") to
                load(value.geoipUrl, value.customGeoip, GeodataFiles.USER_GEOIP_FILE, "geoip.dat")
        }
        geositeCategories = catalogs.first
        geoipCategories = catalogs.second
    }
    val categorySuggestionsGeo = geositeCategories
    val categorySuggestionsIp = geoipCategories
    fun loadCategories(source: CategorySource) {
        if (importing) return
        val url = if (source == CategorySource.GEOSITE) value.geositeUrl else value.geoipUrl
        if (url.isBlank()) {
            notice = "Set a geodata URL first"
            return
        }
        scope.launch {
            importing = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val target = GeodataFiles.catalogFile(context, url)
                    val temporary = File.createTempFile("catalog-", ".part", target.parentFile)
                    try {
                        Graph.subscriptions.fetchCustomGeodataCatalog(url, temporary)
                        val names = temporary.inputStream().use {
                            GeodataFiles.categories(it, GeodataFiles.MAX_SOURCE_BYTES).toList().sorted()
                        }
                        java.nio.file.Files.move(temporary.toPath(), target.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        names
                    } finally {
                        temporary.delete()
                    }
                }
            }
            importing = false
            result.onSuccess { names ->
                if (url != (if (source == CategorySource.GEOSITE) currentValue.geositeUrl else currentValue.geoipUrl)) return@onSuccess
                if (source == CategorySource.GEOSITE) geositeCategories = names else geoipCategories = names
                notice = context.getString(slop.miho.chumpy.R.string.custom_routing_loaded_count, names.size)
            }.onFailure { notice = it.message ?: "Could not load categories" }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val directory = File(context.filesDir, "xray_assets").apply { mkdirs() }
                    val name = if (importGeoip) GeodataFiles.USER_GEOIP_FILE else GeodataFiles.USER_GEOSITE_FILE
                    val target = File(directory, name)
                    val temporary = File.createTempFile("import-", ".part", directory)
                    try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= GeodataFiles.MAX_SOURCE_BYTES) { "File must be under 512 MiB" }
                            output.write(buffer, 0, count)
                        }
                        }
                    } ?: error("Could not read file")
                    require(temporary.length() > 0) { "Empty geodata file" }
                    val categories = temporary.inputStream().use { GeodataFiles.categories(it, GeodataFiles.MAX_SOURCE_BYTES) }
                    java.nio.file.Files.move(temporary.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    name to categories.toList().sorted()
                    } finally { temporary.delete() }
                }
            }
            importing = false
            result.onSuccess { (filename, importedCategories) ->
                onChange(if (filename == GeodataFiles.USER_GEOIP_FILE) currentValue.copy(customGeoip = true, geodataRevision = currentValue.geodataRevision + 1)
                    else currentValue.copy(customGeosite = true, geodataRevision = currentValue.geodataRevision + 1))
                if (filename == GeodataFiles.USER_GEOIP_FILE) geoipCategories = importedCategories else geositeCategories = importedCategories
                notice = context.getString(slop.miho.chumpy.R.string.custom_routing_imported_count, importedCategories.size)
            }.onFailure { notice = it.message ?: "Could not import geodata" }
        }
    }
    PreferenceEditor(R.string.custom_routing_title, onBack) {
        Text(stringResource(R.string.custom_routing_chain_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        PreferenceSection {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(slop.miho.chumpy.R.string.custom_routing_enabled), style = MaterialTheme.typography.bodyLarge)
            }
            Switch(value.enabled, onCheckedChange = { onChange(value.copy(enabled = it)) })
        }
        }
        PreferenceSection {
        Text(stringResource(slop.miho.chumpy.R.string.custom_routing_order), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.custom_routing_last_stage_fallback), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        value.stageOrder.forEachIndexed { index, stage ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stageLabel(stage), modifier = Modifier.weight(1f))
                FilledTonalIconButton(enabled = index > 0, onClick = {
                    val order = value.stageOrder.toMutableList()
                    java.util.Collections.swap(order, index, index - 1)
                    onChange(value.copy(stageOrder = order))
                }) { Icon(painterResource(R.drawable.ic_chevron_right),
                    contentDescription = stringResource(R.string.custom_routing_move_up, stageLabel(stage)),
                    modifier = Modifier.size(24.dp).rotate(-90f)) }
                FilledTonalIconButton(enabled = index < value.stageOrder.lastIndex, onClick = {
                    val order = value.stageOrder.toMutableList()
                    java.util.Collections.swap(order, index, index + 1)
                    onChange(value.copy(stageOrder = order))
                }) { Icon(painterResource(R.drawable.ic_chevron_right),
                    contentDescription = stringResource(R.string.custom_routing_move_down, stageLabel(stage)),
                    modifier = Modifier.size(24.dp).rotate(90f)) }
            }
        }
        }
        EntryField(
            stringResource(slop.miho.chumpy.R.string.custom_routing_target_blocked), value.blocked,
            categorySuggestionsGeo, categorySuggestionsIp,
            onChange = { onChange(value.copy(blocked = it)) },
        )
        EntryField(
            stringResource(slop.miho.chumpy.R.string.custom_routing_target_direct), value.direct,
            categorySuggestionsGeo, categorySuggestionsIp,
            onChange = { onChange(value.copy(direct = it)) },
        )
        EntryField(
            stringResource(slop.miho.chumpy.R.string.custom_routing_target_proxy), value.proxy,
            categorySuggestionsGeo, categorySuggestionsIp,
            onChange = { onChange(value.copy(proxy = it)) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            geodataPresets.forEach { preset ->
                FilterChip(
                    selected = value.geositeUrl == preset.geosite && value.geoipUrl == preset.geoip,
                    onClick = { onChange(value.copy(geositeUrl = preset.geosite, geoipUrl = preset.geoip, customGeosite = false, customGeoip = false)) },
                    label = { Text(preset.name) },
                )
            }
        }
        PreferenceField(
            value = value.geositeUrl,
            onValueChange = { onChange(value.copy(geositeUrl = it, customGeosite = false)) },
            label = stringResource(R.string.custom_routing_geosite_url),
            singleLine = true,
        )
        PreferenceField(
            value = value.geoipUrl,
            onValueChange = { onChange(value.copy(geoipUrl = it, customGeoip = false)) },
            label = stringResource(R.string.custom_routing_geoip_url),
            singleLine = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategorySource.entries.forEach { source ->
                val url = if (source == CategorySource.GEOSITE) value.geositeUrl else value.geoipUrl
                FilledTonalButton(enabled = !importing && url.isNotBlank(), onClick = { loadCategories(source) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.custom_routing_load_categories,
                        if (source == CategorySource.GEOSITE) "GeoSite" else "GeoIP"))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(slop.miho.chumpy.R.string.custom_routing_use_imported_geosite), modifier = Modifier.weight(1f))
            Switch(value.customGeosite, onCheckedChange = { onChange(value.copy(customGeosite = it)) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(slop.miho.chumpy.R.string.custom_routing_use_imported_geoip), modifier = Modifier.weight(1f))
            Switch(value.customGeoip, onCheckedChange = { onChange(value.copy(customGeoip = it)) })
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(modifier = Modifier.fillMaxWidth(), enabled = !importing, onClick = { importGeoip = false; picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                Text(stringResource(if (value.customGeosite) slop.miho.chumpy.R.string.custom_routing_replace_geosite else slop.miho.chumpy.R.string.custom_routing_import_geosite))
            }
            FilledTonalButton(modifier = Modifier.fillMaxWidth(), enabled = !importing, onClick = { importGeoip = true; picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                Text(stringResource(if (value.customGeoip) slop.miho.chumpy.R.string.custom_routing_replace_geoip else slop.miho.chumpy.R.string.custom_routing_import_geoip))
            }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), enabled = dirty && !importing,
            onClick = {
                val valid = listOf(value.geositeUrl, value.geoipUrl).all { raw ->
                    raw.isBlank() || raw.trim().toHttpUrlOrNull()?.let {
                        it.isHttps && it.username.isEmpty() && it.password.isEmpty()
                    } == true
                }
                if (valid) onSave() else notice = context.getString(R.string.custom_routing_https_required)
            }) { Text(stringResource(R.string.custom_routing_save)) }
    }
}

@Composable
private fun stageLabel(stage: CustomRouteStage): String = stringResource(
    when (stage) {
        CustomRouteStage.BLOCKED -> slop.miho.chumpy.R.string.custom_routing_target_blocked
        CustomRouteStage.DIRECT -> slop.miho.chumpy.R.string.custom_routing_target_direct
        CustomRouteStage.PROXY -> slop.miho.chumpy.R.string.custom_routing_target_proxy
    },
)

@Composable
private fun EntryField(
    title: String,
    entries: List<String>,
    geositeCategories: List<String>,
    geoipCategories: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var text by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(entries.joinToString("\n")) }
    val current = text.substringAfterLast('\n')
    val prefix = when {
        current.startsWith("geosite:", ignoreCase = true) -> "geosite:"
        current.startsWith("geoip:", ignoreCase = true) -> "geoip:"
        else -> null
    }
    val categoryQuery = if (prefix == null) "" else current.substring(prefix.length)
    val candidates = when (prefix) {
        "geosite:" -> geositeCategories
        "geoip:" -> geoipCategories
        else -> emptyList()
    }
    val terms = categoryQuery.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank)
    val suggestions = if (prefix == null) emptyList() else candidates.filter { candidate ->
        val normalized = candidate.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        terms.all { term -> normalized.contains(term) }
    }.take(16)
    fun update(next: String) {
        text = next
        onChange(next.lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList())
    }
    PreferenceField(
        value = text,
        onValueChange = { next ->
            update(next)

        },
        label = title,
        singleLine = false,
        minLines = 3,
    )
    if (suggestions.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            suggestions.forEach { category ->
                FilterChip(
                    selected = false,
                    onClick = {
                        val completed = prefix + category.lowercase()
                        val next = if ('\n' in text) text.substringBeforeLast('\n') + "\n" + completed else completed
                        update(next)
                    },
                    label = { Text(category) },
                )
            }
        }
    }
}
