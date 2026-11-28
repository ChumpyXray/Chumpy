package slop.miho.chumpy.ui.routing

import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
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
import slop.miho.chumpy.ui.main.FloatingBottomBarReservedHeight
import slop.miho.chumpy.ui.theme.ChumpyTheme
import slop.miho.chumpy.ui.wallpapers.withSurfaceOpacity
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
                        onSaveAndExit = {
                            val saved = draft.value ?: return@CustomRoutingScreen
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    ConnectionStore.update(this@CustomRoutingActivity) { it.copy(customRouting = saved) }
                                }
                                finish()
                            }
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
private fun CustomRoutingScreen(value: CustomRouting, dirty: Boolean, onSaveAndExit: () -> Unit, onBack: () -> Unit, onChange: (CustomRouting) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val currentValue by androidx.compose.runtime.rememberUpdatedState(value)
    var importing by remember { mutableStateOf(false) }
    var downloadPercent by remember { mutableStateOf<Int?>(null) }
    var loadingSource by remember { mutableStateOf<CategorySource?>(null) }
    var importGeoip by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeError by remember { mutableStateOf(false) }
    var geositeCategories by remember { mutableStateOf(emptyList<String>()) }
    var geoipCategories by remember { mutableStateOf(emptyList<String>()) }
    // The exit's own doorway: leaving with an unsaved draft asks first, the same card
    // menu a server's long tap opens. Both back paths — the gesture and the bar's own
    // button — walk through it.
    var exitAsk by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { exitAsk = true }
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
            notice = resources.getString(R.string.custom_routing_https_required)
            noticeError = true
            return
        }
        downloadPercent = null
        loadingSource = source
        notice = null
        scope.launch {
            importing = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val target = GeodataFiles.catalogFile(context, url)
                    val temporary = File.createTempFile("catalog-", ".part", target.parentFile)
                    try {
                        Graph.subscriptions.fetchCustomGeodataCatalog(url, temporary) { percent ->
                            scope.launch { downloadPercent = percent }
                        }
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
            loadingSource = null
            result.onSuccess { names ->
                if (url != (if (source == CategorySource.GEOSITE) currentValue.geositeUrl else currentValue.geoipUrl)) return@onSuccess
                if (source == CategorySource.GEOSITE) geositeCategories = names else geoipCategories = names
                notice = resources.getQuantityString(R.plurals.custom_routing_loaded_count, names.size, names.size)
                noticeError = false
            }.onFailure {
                android.util.Log.e("Chumpy/Routing", "Could not load categories", it)
                notice = resources.getString(R.string.custom_routing_operation_error)
                noticeError = true
            }
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
                notice = resources.getQuantityString(R.plurals.custom_routing_imported_count, importedCategories.size, importedCategories.size)
                noticeError = false
            }.onFailure {
                android.util.Log.e("Chumpy/Routing", "Could not import geodata", it)
                notice = resources.getString(R.string.custom_routing_operation_error)
                noticeError = true
            }
        }
    }
    // The save's own gatekeeper: HTTPS links without credentials, and the imported
    // databases actually on the disk before the rules they feed may ride. A failure
    // stays on the screen as the notice instead of a save.
    fun validate(): Boolean {
        val valid = !value.enabled || listOf(value.geositeUrl, value.geoipUrl).all { raw ->
            raw.isBlank() || raw.trim().toHttpUrlOrNull()?.let {
                it.isHttps && it.username.isEmpty() && it.password.isEmpty()
            } == true
        }
        val directory = File(context.filesDir, "xray_assets")
        val importedReady = !value.enabled || listOf(
            value.customGeosite to GeodataFiles.USER_GEOSITE_FILE,
            value.customGeoip to GeodataFiles.USER_GEOIP_FILE,
        ).all { (enabled, name) -> !enabled || File(directory, name).let { it.isFile && it.length() > 0 } }
        when {
            !valid -> {
                notice = resources.getString(R.string.custom_routing_https_required)
                noticeError = true
            }
            !importedReady -> {
                notice = resources.getString(R.string.custom_routing_import_required)
                noticeError = true
            }
            else -> return true
        }
        return false
    }
    Box(Modifier.fillMaxSize()) {
        PreferenceEditor(R.string.custom_routing_title, onBack = { if (dirty) exitAsk = true else onBack() }) {            notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (noticeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The screen's one doorway: off, the preset regional routing stands and the
            // rules below are preparation; on, the stages replace the presets entirely.
            SettingsGroup(
                {
                    ToggleRow(
                        title = stringResource(R.string.custom_routing_enabled),
                        supporting = stringResource(
                            if (value.enabled) R.string.custom_routing_exclusive_hint else R.string.custom_routing_off,
                        ),
                        icon = R.drawable.ic_tune,
                        checked = value.enabled,
                        onChange = { enabled -> onChange(value.copy(enabled = enabled)) },
                    )
                },
            )
            // The stages' order, its own group: swapping two neighbors slides them past
            // each other, the way the servers list's own reorder moves.
            StageOrderGroup(
                order = value.stageOrder,
                onSwap = { first, second ->
                    val order = value.stageOrder.toMutableList()
                    java.util.Collections.swap(order, first, second)
                    onChange(value.copy(stageOrder = order))
                },
            )
            EntryField(
                stringResource(R.string.custom_routing_target_blocked), value.blocked,
                categorySuggestionsGeo, categorySuggestionsIp,
                onChange = { onChange(value.copy(blocked = it)) },
            )
            EntryField(
                stringResource(R.string.custom_routing_target_direct), value.direct,
                categorySuggestionsGeo, categorySuggestionsIp,
                onChange = { onChange(value.copy(direct = it)) },
            )
            EntryField(
                stringResource(R.string.custom_routing_target_proxy), value.proxy,
                categorySuggestionsGeo, categorySuggestionsIp,
                onChange = { onChange(value.copy(proxy = it)) },
            )
            // The geodata source: a preset strip riding the card's full width, then the
            // two URLs the strip fills in. A hand-edited URL matching no preset leaves
            // the strip with nothing picked.
            SettingsGroup(
                {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        PreferenceChoices(
                            labels = geodataPresets.map { it.name },
                            selected = geodataPresets.indexOfFirst {
                                it.geosite == value.geositeUrl && it.geoip == value.geoipUrl &&
                                    !value.customGeosite && !value.customGeoip
                            },
                            onSelect = { index ->
                                val preset = geodataPresets[index]
                                onChange(value.copy(
                                    geositeUrl = preset.geosite, geoipUrl = preset.geoip,
                                    customGeosite = false, customGeoip = false,
                                ))
                            },
                            edgePadding = 16.dp,
                        )
                    }
                },
                {
                    HappFieldRow(
                        label = stringResource(R.string.custom_routing_geosite_url),
                        value = value.geositeUrl,
                        onChange = { onChange(value.copy(geositeUrl = it, customGeosite = false)) },
                    )
                },
                {
                    HappFieldRow(
                        label = stringResource(R.string.custom_routing_geoip_url),
                        value = value.geoipUrl,
                        onChange = { onChange(value.copy(geoipUrl = it, customGeoip = false)) },
                    )
                },
            )
            // The catalogs' download: each row fetches its database, the tile spinning
            // the percent under the title while it runs.
            SettingsGroup(
                *CategorySource.entries.map { source ->
                    val name = if (source == CategorySource.GEOSITE) "GeoSite" else "GeoIP"
                    val url = if (source == CategorySource.GEOSITE) value.geositeUrl else value.geoipUrl
                    val loading = loadingSource == source
                    val row: @Composable () -> Unit = {
                        RoutingActionRow(
                            title = stringResource(R.string.custom_routing_load_categories, name),
                            supporting = when {
                                loading && downloadPercent != null ->
                                    stringResource(R.string.custom_routing_loading_percent, name, downloadPercent!!)
                                loading -> stringResource(R.string.custom_routing_loading_categories, name)
                                else -> null
                            },
                            icon = R.drawable.ic_download,
                            loading = loading,
                            enabled = !importing && url.isNotBlank(),
                            onClick = { loadCategories(source) },
                        )
                    }
                    row
                }.toTypedArray(),
            )
            // The user's own databases, imported from a file: the toggle says the
            // database rides, the row under it swaps the file in.
            SettingsGroup(
                {
                    ToggleRow(
                        title = stringResource(R.string.custom_routing_use_imported_geosite),
                        supporting = null,
                        icon = R.drawable.ic_database,
                        checked = value.customGeosite,
                        onChange = { enabled -> onChange(value.copy(customGeosite = enabled)) },
                    )
                },
                {
                    RoutingActionRow(
                        title = stringResource(
                            if (value.customGeosite) R.string.custom_routing_replace_geosite
                            else R.string.custom_routing_import_geosite,
                        ),
                        supporting = null,
                        icon = R.drawable.ic_upload_file,
                        loading = importing && !importGeoip,
                        enabled = !importing,
                        onClick = { importGeoip = false; picker.launch(arrayOf("application/octet-stream", "*/*")) },
                    )
                },
                {
                    ToggleRow(
                        title = stringResource(R.string.custom_routing_use_imported_geoip),
                        supporting = null,
                        icon = R.drawable.ic_database,
                        checked = value.customGeoip,
                        onChange = { enabled -> onChange(value.copy(customGeoip = enabled)) },
                    )
                },
                {
                    RoutingActionRow(
                        title = stringResource(
                            if (value.customGeoip) R.string.custom_routing_replace_geoip
                            else R.string.custom_routing_import_geoip,
                        ),
                        supporting = null,
                        icon = R.drawable.ic_upload_file,
                        loading = importing && importGeoip,
                        enabled = !importing,
                        onClick = { importGeoip = true; picker.launch(arrayOf("application/octet-stream", "*/*")) },
                    )
                },
            )
        }
        // The ask's own dim: while the menu stands, the page and the bar's ground
        // behind it sink under a scrim; tapping it closes the ask, the popup's own
        // dismissal walking the same path.
        val scrimAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (exitAsk) 1f else 0f,
            label = "exit ask scrim",
        )
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        enabled = exitAsk,
                    ) { exitAsk = false },
            )
        }
        // The exit ask's anchor: the menu hangs off this strip, centered over the bar's
        // own ground.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        FloatingBottomBarReservedHeight,
                ),
        ) {
            CardMenu(expanded = exitAsk, onDismissRequest = { exitAsk = false }) {
                Text(
                    text = stringResource(R.string.custom_routing_save_prompt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.custom_routing_save)) },
                    onClick = {
                        exitAsk = false
                        if (validate()) onSaveAndExit()
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.primary,
                        leadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.custom_routing_discard)) },
                    onClick = {
                        exitAsk = false
                        onBack()
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
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

/**
 * The stages' order as an animated stack in the settings group's own clothes: the header
 * row with the fallback story on top, then the stages, each keyed by its stage so a swap
 * moves it instead of rewriting it — the rows slide to their new slots on the motion
 * scheme's spatial spec, the servers list's own reorder dance.
 */
@Composable
private fun StageOrderGroup(
    order: List<CustomRouteStage>,
    onSwap: (first: Int, second: Int) -> Unit,
) {
    val outer = MaterialTheme.shapes.extraLarge
    val inner = MaterialTheme.shapes.extraSmall
    val motion = MaterialTheme.motionScheme
    val rowHeight = 80.dp
    val gap = 2.dp
    Column(Modifier.fillMaxWidth().clip(outer)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.withSurfaceOpacity(),
            shape = RoundedCornerShape(
                topStart = outer.topStart,
                topEnd = outer.topEnd,
                bottomStart = inner.bottomStart,
                bottomEnd = inner.bottomEnd,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(R.string.custom_routing_order),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.custom_routing_last_stage_fallback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(gap))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * order.size + gap * (order.size - 1)),
        ) {
            order.forEachIndexed { index, stage ->
                key(stage) {
                    val offsetY by animateDpAsState(
                        targetValue = (rowHeight + gap) * index,
                        animationSpec = motion.defaultSpatialSpec(),
                        label = "stage row offset",
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer.withSurfaceOpacity(),
                        shape = RoundedCornerShape(
                            topStart = if (index == 0) outer.topStart else inner.topStart,
                            topEnd = if (index == 0) outer.topEnd else inner.topEnd,
                            bottomStart = if (index == order.lastIndex) outer.bottomStart else inner.bottomStart,
                            bottomEnd = if (index == order.lastIndex) outer.bottomEnd else inner.bottomEnd,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .offset { IntOffset(0, offsetY.roundToPx()) },
                    ) {
                        StageMoveRow(
                            title = stageLabel(stage),
                            canMoveUp = index > 0,
                            canMoveDown = index < order.lastIndex,
                            onMoveUp = { onSwap(index, index - 1) },
                            onMoveDown = { onSwap(index, index + 1) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One stage of the rules' order: its name and the two square icon tiles that move it,
 * the icon rows' own language — asleep dimmed, the disabled direction untouchable.
 */
@Composable
private fun StageMoveRow(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SquareIconButton(
                label = stringResource(R.string.custom_routing_move_up, title),
                selected = false,
                onClick = onMoveUp,
                enabled = canMoveUp,
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).rotate(-90f),
                )
            }
            SquareIconButton(
                label = stringResource(R.string.custom_routing_move_down, title),
                selected = false,
                onClick = onMoveDown,
                enabled = canMoveDown,
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).rotate(90f),
                )
            }
        }
    }
}

/**
 * A whole row whose tap is one action (a download or an import): the title on the left,
 * the settings' own square icon tile trailing — a spinner in the tile's place while the
 * work runs. Asleep, the row dims.
 */
@Composable
private fun RoutingActionRow(
    title: String,
    supporting: String?,
    icon: Int,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SquareIconButton(
            label = title,
            selected = false,
            onClick = onClick,
            enabled = enabled && !loading,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

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
        placeholder = stringResource(R.string.custom_routing_entries_placeholder),
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
