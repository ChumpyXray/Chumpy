package slop.miho.chumpy.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.horizontalScroll
import slop.miho.chumpy.R
import slop.miho.chumpy.ui.main.*
import slop.miho.chumpy.ui.theme.LocalDarkTheme
import slop.miho.chumpy.ui.theme.LocalUiSettings
import slop.miho.chumpy.ui.wallpapers.*

@Composable
internal fun PreferenceEditor(title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val destination = remember(title) { object : PillDestination {
        override val icon = R.drawable.ic_tune
        override val label = title
    } }
    val scroll = rememberScrollState()
    val coverage = remember { mutableStateOf(false) }
    val underlay = remember { mutableStateOf(false) }
    val behind = rememberGraphicsLayer()
    val hasContent by rememberContentUnderBar(scroll)
    SystemBarsIconsDark(dark = LocalDarkTheme.current)
    CompositionLocalProvider(LocalBarCoverage provides coverage, LocalBarUnderlay provides underlay) {
        ReportBarCoverage(hasContent)
        Box(Modifier.fillMaxSize().imePadding()) {
            Column(
                Modifier.fillMaxSize().recordBehindBar(behind, LocalUiSettings.current.navigationBarBlur)
                    .verticalScroll(scroll).screenPadding()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingBottomBarReservedHeight + 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
            SubScreenBottomBar(listOf(destination), destination, {}, onBack, behind, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
internal fun PreferenceSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun PreferenceField(label: String, value: String, onValueChange: (String) -> Unit,
    singleLine: Boolean = true, minLines: Int = 1) {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, ime) { if (focused && ime > 0) requester.bringIntoView() }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value = value, onValueChange = onValueChange, singleLine = singleLine, minLines = minLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }.onFocusChanged { focused = it.isFocused }
                    .bringIntoViewRequester(requester).focusRing(shape = RoundedCornerShape(8.dp)))
        }
    }
}

@Composable
internal fun PreferenceChoices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            FilledTonalButton(onClick = { onSelect(index) }, modifier = Modifier.semantics { this.selected = selected == index },
                colors = if (selected == index) ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                else ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text(label) }
        }
    }
}
