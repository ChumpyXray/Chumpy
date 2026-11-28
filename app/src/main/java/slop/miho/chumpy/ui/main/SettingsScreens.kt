package slop.miho.chumpy.ui.main

import androidx.core.net.toUri
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import slop.miho.chumpy.ui.components.controllerWindowInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import slop.miho.chumpy.Graph
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import kotlin.math.roundToInt
import slop.miho.chumpy.BuildConfig
import slop.miho.chumpy.ui.components.controllerContextMenu
import slop.miho.chumpy.ui.components.externalAction
import slop.miho.chumpy.ui.components.controllerHorizontalFocusGroup
import slop.miho.chumpy.ui.components.controllerReadFocus
import slop.miho.chumpy.R
import slop.miho.chumpy.data.Accent
import slop.miho.chumpy.data.FlagStyle
import slop.miho.chumpy.data.IconVariant
import slop.miho.chumpy.data.SpoofMode
import slop.miho.chumpy.data.ThemeMode
import slop.miho.chumpy.data.UiSettings
import slop.miho.chumpy.ui.components.FlipTriangleTile
import slop.miho.chumpy.ui.components.FoldingGroup
import slop.miho.chumpy.ui.components.HappFieldRow
import slop.miho.chumpy.ui.components.IconChoice
import slop.miho.chumpy.ui.components.IconChoiceRow
import slop.miho.chumpy.ui.components.SettingsGroup
import slop.miho.chumpy.ui.components.SwatchDivider
import slop.miho.chumpy.ui.components.ToggleRow
import slop.miho.chumpy.ui.components.InfoChip
import slop.miho.chumpy.ui.components.UpdateCard
import slop.miho.chumpy.ui.components.InputMode
import slop.miho.chumpy.ui.components.LocalInputMode
import slop.miho.chumpy.ui.components.TriangleTilePolygon
import slop.miho.chumpy.ui.components.TriangleTileRingPath
import slop.miho.chumpy.ui.components.AccentRow
import slop.miho.chumpy.ui.components.CountryFlag
import slop.miho.chumpy.ui.components.RainbowFlag
import slop.miho.chumpy.ui.components.focusRing
import slop.miho.chumpy.ui.components.pressBounce
import slop.miho.chumpy.ui.components.rememberHapticPoke
import slop.miho.chumpy.ui.fonts.FontPickerRow
import slop.miho.chumpy.ui.UpdateUiState
import slop.miho.chumpy.ui.theme.LocalUiSettings
import slop.miho.chumpy.ui.theme.backdropScheme
import slop.miho.chumpy.ui.wallpapers.LocalBackdropDark
import slop.miho.chumpy.ui.wallpapers.LocalHasWallpaper
import slop.miho.chumpy.ui.wallpapers.ReportBarCoverage
import slop.miho.chumpy.ui.wallpapers.WallpaperActivity
import slop.miho.chumpy.ui.wallpapers.withSurfaceOpacity

/**
 * The "Interface" settings page. One screen, two sections — Appearance and Privacy — each a
 * stack of headerless grouped blocks (MD3 expressive grouped lists: tonal surface-container
 * groups, extra-large outer corners, extra-small inner corners, 2dp gaps instead of dividers).
 * No screen headline: the bottom bar pill already says where we are.
 */
@Composable
fun InterfaceScreen(
    settings: UiSettings,
    onChange: ((UiSettings) -> UiSettings) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    // The groups sit under the floating bar while the page outgrows the screen; derived so
    // only the flip recomposes, not every scroll pixel.
    val contentUnderBar by remember(scrollState) {
        derivedStateOf { scrollState.canScrollForward || scrollState.canScrollBackward }
    }
    val contentReachesBar by rememberContentUnderBar(scrollState)
    ReportBarCoverage(contentUnderBar, underlaid = contentReachesBar)
    // Padding after verticalScroll is content padding: it scrolls away at the top and
    // keeps the last row clear of the floating bar at the bottom.
    Column(
        modifier = modifier
            .fillMaxSize()
            .screenPadding()
            .verticalScroll(scrollState)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    LocalBarReservedHeight.current,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // No section header any more: the blocks stand on their own, at the gap they had
        // under it.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsGroup(
                {
                    IconChoiceRow(
                        title = stringResource(R.string.settings_colors),
                        options = listOf(
                            IconChoice(
                                R.drawable.ic_brightness_auto,
                                R.string.settings_theme_system,
                                ThemeMode.SYSTEM,
                            ),
                            IconChoice(
                                R.drawable.ic_light_mode,
                                R.string.settings_theme_light,
                                ThemeMode.LIGHT,
                            ),
                            // The long tap turns on AMOLED: the dark theme on a pure black
                            // background. It lights the dark tile; a plain tap goes back.
                            IconChoice(
                                R.drawable.ic_dark_mode,
                                R.string.settings_theme_dark,
                                ThemeMode.DARK,
                                onLongClick = { onChange { it.copy(themeMode = ThemeMode.AMOLED) } },
                            ),
                        ),
                        selected = if (settings.themeMode == ThemeMode.AMOLED) ThemeMode.DARK else settings.themeMode,
                        onSelect = { mode -> onChange { it.copy(themeMode = mode) } },
                    )
                },
                {
                    AccentRow(
                        selected = settings.accent,
                        onSelect = { accent -> onChange { it.copy(accent = accent) } },
                        customColor = Color(settings.customAccent),
                        onPickCustom = { color ->
                            onChange { it.copy(accent = Accent.CUSTOM, customAccent = color.toArgb()) }
                        },
                    )
                },
            )
            // The app icon and the wallpaper share this block: both dress the app itself,
            // the icon its face and the picture its backdrop. The picker sits above.
            SettingsGroup(
                {
                    IconVariantRow(
                        selected = settings.iconVariant,
                        onSelect = { variant -> onChange { it.copy(iconVariant = variant) } },
                    )
                },
                {
                    ScreenLinkRow(
                        title = stringResource(R.string.settings_wallpapers),
                        supporting = stringResource(R.string.settings_wallpapers_hint),
                        icon = R.drawable.ic_wallpaper,
                        target = WallpaperActivity::class.java,
                    )
                },
            )
            // The fonts are picked right in their row, no screen of their own. The flags'
            // look shares their block: both are about how text and glyphs are drawn.
            // While the rainbow accent is on, the previews fly its flag.
            val rainbow = settings.accent == Accent.RAINBOW
            SettingsGroup(
                {
                    FontPickerRow(
                        selected = settings.fontId,
                        onSelect = { id -> onChange { it.copy(fontId = id) } },
                    )
                },
                {
                    IconChoiceRow(
                        title = stringResource(R.string.settings_flag_style),
                        // Like the font row, the picked style is named under the title.
                        showSelectedLabel = true,
                        options = listOf(
                            IconChoice(R.string.settings_flag_system, FlagStyle.SYSTEM) {
                                PreviewFlag(rainbow, twemoji = false)
                            },
                            IconChoice(R.string.settings_flag_twemoji, FlagStyle.TWEMOJI) {
                                PreviewFlag(rainbow, twemoji = true)
                            },
                            IconChoice(
                                R.drawable.ic_public,
                                R.string.settings_flag_globe,
                                FlagStyle.GLOBE,
                            ),
                        ),
                        selected = settings.flagStyle,
                        onSelect = { style -> onChange { it.copy(flagStyle = style) } },
                    )
                },
                // The stickers the flag row's style also dresses: the emoji a name
                // begins with ride the region icon as outlined stickers, or stay
                // plain title text.
                {
                    ToggleRow(
                        title = stringResource(R.string.settings_emoji_stickers),
                        supporting = stringResource(R.string.settings_emoji_stickers_hint),
                        icon = R.drawable.ic_sticker,
                        checked = settings.emojiStickers,
                        onChange = { checked ->
                            onChange { it.copy(emojiStickers = checked) }
                        },
                    )
                },
            )
            // The navigation bar's own block: which bar, and its frost.
            val classicBar = classicNavigationBar()
            val smallScreen = isSmallScreen()
            val smallByWidth = smallScreenByWidth()
            SettingsGroup(
                {
                    ToggleRow(
                        title = stringResource(R.string.settings_classic_navbar),
                        // While the small-screen layout is forced, the hint says so — it's
                        // the only trace of the long tap besides the margins themselves.
                        supporting = stringResource(
                            when (settings.smallScreen) {
                                true -> R.string.settings_small_screen_forced_on
                                false -> R.string.settings_small_screen_forced_off
                                null -> R.string.settings_classic_navbar_hint
                            },
                        ),
                        // A hidden switch: the long tap forces the small-screen layout the
                        // other way, and forcing it back to what the width says lets go.
                        onLongClick = {
                            val flipped = !smallScreen
                            onChange { it.copy(smallScreen = flipped.takeIf { forced -> forced != smallByWidth }) }
                        },
                        // The edge-to-edge bar while on, the inset floating one while off.
                        icon = if (classicBar) {
                            R.drawable.ic_bottom_navigation
                        } else {
                            R.drawable.ic_call_to_action
                        },
                        // Shows the resolved state: unpicked, the screen's default.
                        checked = classicBar,
                        onChange = { checked ->
                            onChange { it.copy(classicNavigationBar = checked) }
                        },
                    )
                },
                {
                    ToggleRow(
                        title = stringResource(R.string.settings_navbar_blur),
                        supporting = stringResource(R.string.settings_navbar_blur_hint),
                        // Frosted glass while on, plain translucency while off.
                        icon = if (settings.navigationBarBlur) {
                            R.drawable.ic_blur_on
                        } else {
                            R.drawable.ic_opacity
                        },
                        checked = settings.navigationBarBlur,
                        onChange = { checked ->
                            onChange { it.copy(navigationBarBlur = checked) }
                        },
                    )
                },
                // The intensity is a row of its own, unfolding under the toggle only while
                // the frost is on: the higher, the denser the pill's tint and the deeper its blur.
                {
                    SliderRow(
                        icon = R.drawable.ic_blur_on,
                        hint = stringResource(R.string.settings_navbar_blur_intensity),
                        value = settings.navigationBarBlurIntensity,
                        // The floor keeps a ghost of the bar legible; the ceiling keeps a
                        // ghost of what passes beneath — the frost never fully dissolves
                        // either side.
                        valueRange = 0.35f..0.85f,
                        onChange = { value ->
                            onChange { it.copy(navigationBarBlurIntensity = value) }
                        },
                    )
                },
                isVisible = { index -> index != 2 || settings.navigationBarBlur },
            )
            SettingsGroup(
                {
                    ToggleRow(
                        title = stringResource(R.string.settings_slow_animations),
                        supporting = stringResource(R.string.settings_slow_animations_hint),
                        // The speedometer with a 2 while on (every animation takes twice as
                        // long), the plain one while off: normal or slow, nothing faster.
                        icon = if (settings.slowAnimations) {
                            R.drawable.ic_speed_2
                        } else {
                            R.drawable.ic_speed_normal
                        },
                        checked = settings.slowAnimations,
                        onChange = { checked -> onChange { it.copy(slowAnimations = checked) } },
                    )
                },
                {
                    ToggleRow(
                        title = stringResource(R.string.settings_simple_transitions),
                        supporting = stringResource(R.string.settings_simple_transitions_hint),
                        // Fade circle for the simple switch, sliding arrows while the
                        // carousel strip is on.
                        icon = if (settings.simpleTransitions) {
                            R.drawable.ic_opacity
                        } else {
                            R.drawable.ic_swap_horizontal
                        },
                        checked = settings.simpleTransitions,
                        onChange = { checked -> onChange { it.copy(simpleTransitions = checked) } },
                    )
                },
            )
            // Haptics get their own block, right under the animation settings.
            SettingsGroup(
                {
                    ToggleRow(
                        title = stringResource(R.string.settings_haptic_feedback),
                        supporting = stringResource(R.string.settings_haptic_feedback_hint),
                        // Vibrating phone while on, crossed-out hand while off.
                        icon = if (settings.hapticFeedback) {
                            R.drawable.ic_vibration
                        } else {
                            R.drawable.ic_do_not_touch
                        },
                        checked = settings.hapticFeedback,
                        onChange = { checked -> onChange { it.copy(hapticFeedback = checked) } },
                    )
                },
            )
            // The addresses' own curtain, the block of its own: an eye struck through
            // while the curtain stands, the plain eye while it doesn't.
            SettingsGroup(
                {
                    ToggleRow(
                        title = stringResource(R.string.settings_mask_addresses),
                        supporting = stringResource(R.string.settings_mask_addresses_hint),
                        icon = if (settings.maskAddresses) {
                            R.drawable.ic_visibility_off
                        } else {
                            R.drawable.ic_visibility
                        },
                        checked = settings.maskAddresses,
                        onChange = { checked -> onChange { it.copy(maskAddresses = checked) } },
                    )
                },
            )
        }
    }
}

/** The country the flag style tiles preview their look with. */
private const val FLAG_PREVIEW_COUNTRY = "JP"

/** A flag style tile's preview: the country's flag, or the rainbow one while [rainbow]. */
@Composable
private fun PreviewFlag(rainbow: Boolean, twemoji: Boolean) {
    Crossfade(rainbow, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "flag preview") { pride ->
        if (pride) {
            RainbowFlag(twemoji)
        } else {
            CountryFlag(code = FLAG_PREVIEW_COUNTRY, twemoji = twemoji)
        }
    }
}

/** A slider row: the icon stands in for the label, the percentage keeps the number. */
@Composable
private fun SliderRow(
    icon: Int,
    hint: String,
    value: Float,
    onChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f).focusRing(inset = true, gap = 1.dp),
            )
            Text(
                text = stringResource(R.string.wallpapers_percent, (value * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A row that opens a deep-dive screen (the wallpapers) instead of toggling. Same layout
 * as the toggle rows, but the trailing tile is a pair: the icon on its own square tile,
 * tucked just left of the empty right-pointing triangle — the little "menu" the screen
 * unfolds from.
 */
@Composable
private fun ScreenLinkRow(
    title: String,
    supporting: String,
    icon: Int,
    target: Class<*>,
    extra: Pair<String, String>? = null,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val poke = rememberHapticPoke()
    // Bumped in the click handler: the pop keys off it, so it fires on every click — the
    // press state alone can go unnoticed on a quick tap.
    var taps by remember { mutableIntStateOf(0) }
    // The controller's eye on the row: the whole row carries the focus, and its trailing
    // triangle — the menu the screen unfolds from — wears the ring for it.
    var rowFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { rowFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ) {
                poke()
                taps++
                val intent = Intent(context, target)
                extra?.let { (key, value) -> intent.putExtra(key, value) }
                context.startActivity(intent)
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The pair bounces as one widget when tapped.
        Box(
            modifier = Modifier.pressBounce(pressed, taps),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinkIconTile(icon = icon, pressed = pressed)
                TriangleTile(pressed = pressed, focused = { rowFocused })
            }
        }
    }
}

/**
 * The screen's icon on the standard square tile: raised tonal container, dipping its tone
 * while the row is held down. The row carries the focus, never the tile — [focused] lends
 * it the row's own, so the ring lands on the row's trailing mark.
 */
@Composable
private fun LinkIconTile(
    icon: Int,
    pressed: Boolean,
    focused: (() -> Boolean)? = null,
    /** The subscription update button's own trick: while the work runs, the mark spins. */
    spinning: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val resting = colors.secondaryContainer
    val container by animateColorAsState(
        targetValue = if (pressed) lerp(resting, colors.onSurface, 0.1f) else resting,
        animationSpec = motion.defaultEffectsSpec(),
        label = "link tile container",
    )
    val spin = if (spinning) {
        rememberInfiniteTransition(label = "link tile spin")
            .animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 1_400, easing = LinearEasing)),
                label = "link tile spin angle",
            )
    } else {
        null
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = colors.onSecondaryContainer,
        modifier = Modifier
            .size(48.dp)
            .focusRing(focused = focused),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .let { if (spin != null) it.graphicsLayer { rotationZ = spin.value } else it },
            )
        }
    }
}

/**
 * The empty right-pointing triangle: the same tonal raised container as the square tiles,
 * dipping its tone while the row is held down. The row carries the focus, never the
 * triangle — [focused] lends it the row's own, so the ring lands on the row's trailing
 * mark.
 */
@Composable
private fun TriangleTile(
    pressed: Boolean,
    focused: (() -> Boolean)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val resting = colors.secondaryContainer
    val container by animateColorAsState(
        targetValue = if (pressed) lerp(resting, colors.onSurface, 0.1f) else resting,
        animationSpec = motion.defaultEffectsSpec(),
        label = "triangle tile container",
    )
    Surface(
        shape = TriangleTilePolygon.toShape(),
        color = container,
        modifier = Modifier
            .size(48.dp)
            // The ring takes the tile's own shape — a triangle around the triangle,
            // outset past its points — rather than a square around them both.
            .focusRing(
                focused = focused,
                path = { TriangleTileRingPath(it) },
            ),
    ) {}
}

/**
 * The "Connection" settings page. One screen: how traffic is routed — the mode's tile,
 * the country's flag on it like the fonts' tiles carry their own glyphs, with the
 * per-app and LAN toggles beside it — and the URL the real ping measurements fetch
 * through every server. The per-subscription doors (the background refresh's switch,
 * the request and HWID settings) live in the server list's own row menus — a
 * subscription's asks stand on its row, not on this page. Same grouped-list language
 * as the interface page.
 */
@Composable
fun ConnectionScreen(
    connection: slop.miho.chumpy.data.ConnectionSettings,
    flagStyle: slop.miho.chumpy.data.FlagStyle,
    settings: slop.miho.chumpy.data.UiSettings,
    onChangeConnection: ((slop.miho.chumpy.data.ConnectionSettings) -> slop.miho.chumpy.data.ConnectionSettings) -> Unit,
    onChangeSettings: ((slop.miho.chumpy.data.UiSettings) -> slop.miho.chumpy.data.UiSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val contentUnderBar by remember(scrollState) {
        derivedStateOf { scrollState.canScrollForward || scrollState.canScrollBackward }
    }
    val contentReachesBar by rememberContentUnderBar(scrollState)
    ReportBarCoverage(contentUnderBar, underlaid = contentReachesBar)
    // The keyboard counts as the edge while it is up: the delay test URL's field — and
    // the fingerprint's own — stays above the keys, the focused one asking for its
    // visibility again once the keys have risen. The bar's reserved height drops out
    // for the same reason: the bar rides the navigation bars only, so while the keys
    // stand over it there is nothing under them to keep the seat for — but the page
    // still keeps its own margin off the edge, the same 16 it holds everywhere else.
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(
        modifier = modifier
            .fillMaxSize()
            .screenPadding()
            .verticalScroll(scrollState)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = WindowInsets.navigationBars.union(WindowInsets.ime)
                    .asPaddingValues().calculateBottomPadding() +
                    if (imeOpen) 16.dp else LocalBarReservedHeight.current,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Routing, the per-app bypass and the LAN's own toggle share one block: all
        // three decide what the tunnel carries — whole countries by their sites and
        // addresses, single apps by name, and the local network by whether it rides
        // the tunnel at all.
        SettingsGroup(
            {
                // The strip rides in every state: the custom rules sit in it as their
                // own tile instead of banishing the picker.
                RoutingRow(
                    selected = connection.routingMode,
                    selectedProfileId = connection.selectedProfileId,
                    customRoutingEnabled = connection.customRouting.enabled,
                    flagStyle = flagStyle,
                    onSelectMode = { mode ->
                        onChangeConnection {
                            it.copy(routingMode = mode, customRouting = it.customRouting.copy(enabled = false))
                        }
                    },
                )
            },
            {
                ScreenLinkRow(
                    title = stringResource(R.string.per_app_title),
                    supporting = when {
                        !connection.perAppEnabled -> stringResource(R.string.per_app_disabled)
                        connection.perAppOnly -> pluralStringResource(
                            R.plurals.per_app_only_count,
                            connection.bypassedApps.size,
                            connection.bypassedApps.size,
                        )
                        connection.bypassedApps.isEmpty() -> stringResource(R.string.per_app_hint)
                        else -> pluralStringResource(
                            R.plurals.per_app_count,
                            connection.bypassedApps.size,
                            connection.bypassedApps.size,
                        )
                    },
                    icon = R.drawable.ic_apps,
                    target = slop.miho.chumpy.ui.perapp.PerAppProxyActivity::class.java,
                )
            },
            {
                ToggleRow(
                    title = stringResource(R.string.connection_bypass_lan),
                    supporting = stringResource(
                        if (connection.bypassLan) {
                            R.string.connection_bypass_lan_on
                        } else {
                            R.string.connection_bypass_lan_off
                        },
                    ),
                    icon = R.drawable.ic_leak_add,
                    checked = connection.bypassLan,
                    onChange = { enabled ->
                        onChangeConnection { it.copy(bypassLan = enabled) }
                    },
                )
            },
        )
        // The tunnel's own afterlife, apart from what it carries: whether a fall gets back
        // up on its own. Off (the default), the fall stands and the alert's Reconnect is
        // the way back by the user's own hand; on, the answer is immediate — the climb
        // back begins the moment the fall finishes, the notice edited to the outcome.
        SettingsGroup(
            {
                ToggleRow(
                    title = stringResource(R.string.connection_auto_reconnect),
                    supporting = stringResource(
                        if (connection.autoReconnect) {
                            R.string.connection_auto_reconnect_on
                        } else {
                            R.string.connection_auto_reconnect_off
                        },
                    ),
                    icon = R.drawable.ic_renew,
                    checked = connection.autoReconnect,
                    onChange = { enabled ->
                        onChangeConnection { it.copy(autoReconnect = enabled) }
                    },
                )
            },
        )
        SettingsGroup(
            {
                ToggleRow(
                    title = stringResource(R.string.random_local_port),
                    supporting = stringResource(R.string.local_port_reconnect),
                    icon = R.drawable.ic_renew,
                    checked = connection.randomLocalPort,
                    onChange = { enabled -> onChangeConnection { it.copy(randomLocalPort = enabled) } },
                )
            },
            {
                var port by remember(connection.localProxyPort) { mutableStateOf(connection.localProxyPort.toString()) }
                HappFieldRow(
                    label = stringResource(R.string.local_proxy_port),
                    value = port,
                    onChange = { value ->
                        port = value
                        value.toIntOrNull()?.takeIf { it in 1024..65535 }?.let { valid ->
                            onChangeConnection { it.copy(localProxyPort = valid) }
                        }
                    },
                )
            },
        )
        // The spoof's own block, between the carrying and the measuring. The header
        // carries two rows of its own, separated by the fold rows' own 2dp seam: the
        // picker ("Spoofer", with the picked client named beneath it — Happ's big
        // official client marks, the crossed-out circle for going out
        // bare) and the strings' doorway beneath it — "Edit spoof strings" with the
        // background refresh's own triangle — so a working spoof sits compact, two
        // rows tall, and the fingerprint's seven fields ride folded behind the
        // doorway, one row each, editable whatever the picker says. The regenerator
        // leads the fold's list: the tap asks first, the dialog's confirm rewires the
        // identity over the strings it rewrites.
        var happOpen by rememberSaveable { mutableStateOf(false) }
        // A fresh identity is one tap away on a whole-row button — too easy to trip.
        // The tap asks first; the dialog's confirm is the deliberate tap the fold's
        // comment means.
        var happRegenPending by rememberSaveable { mutableStateOf(false) }
        if (happRegenPending) {
            HappRegenerateDialog(
                onConfirm = {
                    happRegenPending = false
                    onChangeSettings {
                        it.copy(
                            happUserId = slop.miho.chumpy.sub.HappSpoof.randomUserId(),
                            happHwid = slop.miho.chumpy.sub.HappSpoof.randomHwid(),
                        )
                    }
                },
                onDismiss = { happRegenPending = false },
            )
        }
        FoldingGroup(
            expanded = happOpen,
            headerRows = listOf(
                {
                    IconChoiceRow(
                        title = stringResource(R.string.spoof_title),
                        showSelectedLabel = true,
                        options = listOf(
                            IconChoice(R.drawable.ic_happ, R.string.spoof_as_happ, SpoofMode.HAPP),
                            IconChoice(R.string.spoof_as_incy, SpoofMode.INCY) {
                                Icon(painterResource(R.drawable.ic_incy), contentDescription = null,
                                    modifier = Modifier.size(width = 36.dp, height = 24.dp))
                            },
                            IconChoice(R.drawable.ic_block, R.string.per_app_disabled, SpoofMode.OFF),
                        ),
                        selected = settings.spoofMode,
                        onSelect = { mode -> onChangeSettings { it.copy(spoofMode = mode) } },
                    )
                },
                {
                    EditSpoofStringsRow(
                        expanded = happOpen,
                        onToggle = { happOpen = !happOpen },
                    )
                },
            ),
            rows = listOf(
                {
                    ToggleRow(
                        title = stringResource(R.string.happ_send_hwid),
                        supporting = stringResource(
                            if (settings.happSendHwid) R.string.happ_send_hwid_on else R.string.happ_send_hwid_off,
                        ),
                        icon = R.drawable.ic_device,
                        checked = settings.happSendHwid,
                        onChange = { enabled -> onChangeSettings { it.copy(happSendHwid = enabled) } },
                    )
                },
                {
                    HappRegenerateRow(
                        onRegenerate = { happRegenPending = true },
                    )
                },
                {
                    HappFieldRow(
                        label = stringResource(R.string.happ_app_version),
                        value = if (settings.spoofMode == SpoofMode.INCY) settings.incyAppVersion else settings.happAppVersion,
                        onChange = { value ->
                            onChangeSettings {
                                if (it.spoofMode == SpoofMode.INCY) it.copy(incyAppVersion = value) else it.copy(happAppVersion = value)
                            }
                        },
                    )
                },
                {
                    HappFieldRow(
                        label = stringResource(R.string.happ_locale),
                        value = settings.happLocale,
                        onChange = { value -> onChangeSettings { it.copy(happLocale = value) } },
                    )
                },
                {
                    HappFieldRow(
                        label = stringResource(R.string.happ_os),
                        value = settings.happOs,
                        onChange = { value -> onChangeSettings { it.copy(happOs = value) } },
                    )
                },
                {
                    HappFieldRow(
                        label = stringResource(R.string.happ_os_version),
                        value = settings.happOsVersion,
                        onChange = { value -> onChangeSettings { it.copy(happOsVersion = value) } },
                    )
                },
                {
                    HappFieldRow(
                        label = stringResource(R.string.happ_device_model),
                        value = settings.happDeviceModel,
                        onChange = { value -> onChangeSettings { it.copy(happDeviceModel = value) } },
                    )
                },
                {
                    HappFieldRow(
                        label = stringResource(R.string.happ_user_id),
                        value = settings.happUserId,
                        onChange = { value -> onChangeSettings { it.copy(happUserId = value) } },
                    )
                },
                {
                    HappFieldRow(
                        label = stringResource(R.string.happ_hwid),
                        value = settings.happHwid,
                        onChange = { value -> onChangeSettings { it.copy(happHwid = value) } },
                    )
                },
            ),
        )
        SettingsGroup(
            {
                TestUrlRow(
                    value = connection.delayTestUrl,
                    onChange = { url -> onChangeConnection { it.copy(delayTestUrl = url) } },
                )
            },
        )
        // The per-subscription doors — the background refresh's switch and the request
        // and HWID settings — live in the subscription row's own menu on the servers
        // screen, next to the pin and the delete.
    }
}

/**
 * The routing strip, picked right in its row the way the font is, titleless as the
 * fonts' crowded form. The app-level modes lead: the plain everything mode, the
 * subscription's own rules — the tile sleeps while the selected profile carries none —
 * and the user's custom rules; a thin divider; then the regions, each its own flag (the
 * plain-globe style spells it out in its two letters instead). The selected mode's
 * story runs under the strip: a region tells which one stays out of the tunnel.
 */
@Composable
private fun RoutingRow(
    selected: slop.miho.chumpy.xray.RoutingMode,
    selectedProfileId: String?,
    customRoutingEnabled: Boolean,
    flagStyle: slop.miho.chumpy.data.FlagStyle,
    onSelectMode: (slop.miho.chumpy.xray.RoutingMode) -> Unit,
) {
    val context = LocalContext.current
    // Whether the selected profile carries the panel's own routing. A "follow the
    // subscription" pick without rules to follow shows as the plain everything mode —
    // the config builder falls back the same way.
    var subscriptionAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(selectedProfileId) {
        subscriptionAvailable = selectedProfileId?.let {
            Graph.subscriptions.profileById(it)?.routingRules?.isNotBlank()
        } == true
    }
    val effective = if (selected == slop.miho.chumpy.xray.RoutingMode.GLOBAL && !subscriptionAvailable) {
        slop.miho.chumpy.xray.RoutingMode.DISABLED
    } else {
        selected
    }
    val hint = when {
        customRoutingEnabled -> stringResource(R.string.custom_routing_exclusive_hint)
        effective == slop.miho.chumpy.xray.RoutingMode.DISABLED ->
            stringResource(R.string.settings_routing_disabled_hint)
        effective == slop.miho.chumpy.xray.RoutingMode.GLOBAL ->
            stringResource(R.string.settings_routing_subscription_hint)
        else -> regionNameRes(effective)?.let { name ->
            stringResource(R.string.settings_routing_dont_route, stringResource(name))
        } ?: ""
    }
    Column(Modifier.fillMaxWidth()) {
        // The scroll runs the card's full width with the padding inside the scrolled
        // content, as in the font picker: the tiles slide under the card's rounding
        // instead of stopping short of it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .controllerHorizontalFocusGroup()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoutingTile(
                label = stringResource(R.string.settings_routing_disabled),
                selected = !customRoutingEnabled &&
                    effective == slop.miho.chumpy.xray.RoutingMode.DISABLED,
                onClick = { onSelectMode(slop.miho.chumpy.xray.RoutingMode.DISABLED) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_block),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            RoutingTile(
                label = stringResource(R.string.settings_routing_subscription),
                selected = !customRoutingEnabled &&
                    effective == slop.miho.chumpy.xray.RoutingMode.GLOBAL,
                onClick = { onSelectMode(slop.miho.chumpy.xray.RoutingMode.GLOBAL) },
                enabled = subscriptionAvailable,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_directions),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            // The custom rules' tile is a doorway, not a toggle: it leads to their
            // screen and only wears the accent while the rules are on.
            RoutingTile(
                label = stringResource(R.string.custom_routing_title),
                selected = customRoutingEnabled,
                onClick = {
                    context.startActivity(Intent(context, slop.miho.chumpy.ui.routing.CustomRoutingActivity::class.java))
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            // The divider: the app-level modes on one side, the regions' presets on
            // the other.
            SwatchDivider()
            slop.miho.chumpy.xray.RoutingMode.entries.filter { it.region != null }.forEach { mode ->
                key(mode) {
                    val region = mode.region!!
                    RoutingTile(
                        label = regionNameRes(mode)?.let { name -> stringResource(name) }
                            ?: region.uppercase(),
                        selected = !customRoutingEnabled && effective == mode,
                        onClick = { onSelectMode(mode) },
                    ) {
                        when (flagStyle) {
                            slop.miho.chumpy.data.FlagStyle.TWEMOJI ->
                                CountryFlag(code = region.uppercase(), twemoji = true)
                            slop.miho.chumpy.data.FlagStyle.SYSTEM ->
                                CountryFlag(code = region.uppercase(), twemoji = false)
                            slop.miho.chumpy.data.FlagStyle.GLOBE ->
                                Text(
                                    text = region.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                        }
                    }
                }
            }
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

/** The region modes' country names; the app-level modes have none. */
private fun regionNameRes(mode: slop.miho.chumpy.xray.RoutingMode): Int? = when (mode) {
    slop.miho.chumpy.xray.RoutingMode.BYPASS_RU -> R.string.region_ru
    slop.miho.chumpy.xray.RoutingMode.BYPASS_CN -> R.string.region_cn
    slop.miho.chumpy.xray.RoutingMode.BYPASS_IR -> R.string.region_ir
    slop.miho.chumpy.xray.RoutingMode.BYPASS_AF -> R.string.region_af
    slop.miho.chumpy.xray.RoutingMode.BYPASS_ID -> R.string.region_id
    slop.miho.chumpy.xray.RoutingMode.BYPASS_TR -> R.string.region_tr
    slop.miho.chumpy.xray.RoutingMode.BYPASS_BR -> R.string.region_br
    slop.miho.chumpy.xray.RoutingMode.DISABLED -> null
    slop.miho.chumpy.xray.RoutingMode.GLOBAL -> null
}

/**
 * A routing mode's square tile, the font tile's twin: raised tonal container, the accent
 * container while selected, the press squash and the click pop, and the mode's mark in
 * the middle — read out for screen readers, never shown.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RoutingTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val poke = rememberHapticPoke()
    var taps by remember { mutableIntStateOf(0) }
    val resting = if (selected) colors.primary else colors.secondaryContainer
    val container by animateColorAsState(
        targetValue = if (pressed) lerp(resting, colors.onSurface, 0.1f) else resting,
        animationSpec = motion.defaultEffectsSpec(),
        label = "routing tile container",
    )
    val tileContent by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSecondaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "routing tile content",
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = tileContent,
        modifier = Modifier
            .size(48.dp)
            // Asleep, the tile dims — visible, untouchable.
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .pressBounce(pressed, taps)
            // The controller's ring rides before the clip — outside it, as every ring
            // sits outside what it marks — the 16dp corners matching the tile's own.
            .focusRing()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                enabled = enabled,
            ) {
                poke()
                taps++
                onClick()
            }
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * The delay test URL's own card: the bare field — filling the card edge to edge, its own
 * inner padding is the card's, so the text sits at the same 16dp as everything else —
 * and the add-font button's narrow twin at the field's end, asleep: a pencil, no press,
 * marking the row for a text field. The description rides under the field, no name.
 * Committed on every keystroke; Done commits the trim.
 */
@Composable
private fun TestUrlRow(
    value: String,
    onChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // The tap lands while the bottom edge still sits under the bar — the keyboard rises
    // after, so the field asks for its visibility again once it has: the scroll then
    // hands it the space the keys took.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottom, focused) {
        if (focused && imeBottom > 0) requester.bringIntoView()
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 80, not the field's own 56: the stadium gets its 16dp of air above
                // and below to match the sides — the gap reads square, not wider
                // than tall, the add row's cross the same way.
                .heightIn(min = 80.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.settings_test_url)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onChange(value.trim()) }),
                // No outline and no fill of its own: the card is the field's.
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused }
                    .bringIntoViewRequester(requester)
                    // Under the controller the field sits out of the dpad's walk — the
                    // cursor passing over it must not stop to raise the keyboard. Touch
                    // keeps the field as it always was.
                    .then(
                        if (LocalInputMode.current == InputMode.CONTROLLER) {
                            Modifier.focusRing()
                        } else {
                            Modifier
                        },
                    ),
            )
            Surface(
                shape = CircleShape,
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(width = 36.dp, height = 48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        // The glyph turns inside the backing — the backing stays put.
                        // A pencil resting at its writing angle, tip down.
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = -45f },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.settings_test_url_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

/**
 * The strings' doorway, the background refresh row's own twin only one line tall:
 * "Edit spoof strings", and the triangle pointing down while the fold is closed,
 * flipping over to point up as it opens. The tap unrolls the fingerprint's fields —
 * and the regenerator above them — whatever the toggle says.
 */
@Composable
private fun EditSpoofStringsRow(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val poke = rememberHapticPoke()
    // Bumped in the click handler: the pop keys off it, so it fires on every click —
    // the press state alone can go unnoticed on a quick tap.
    var taps by remember { mutableIntStateOf(0) }
    // The controller's eye on the row: the whole row carries the focus, and its
    // triangle — the doorway's own handle — wears the ring for it.
    var rowFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { rowFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ) {
                poke()
                taps++
                onToggle()
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.happ_edit),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // The triangle bounces alone when tapped.
        Box(
            modifier = Modifier.pressBounce(pressed, taps),
        ) {
            FlipTriangleTile(pressed = pressed, expanded = expanded, focused = { rowFocused })
        }
    }
}

/**
 * The regenerate confirmation: the row is one whole-row tap, and a fresh identity is
 * not what a stray tap should buy — the panel the spoof rides to sees a brand-new
 * user on a brand-new device, and the old one is not coming back. The confirm carries
 * the error color, the delete dialog's own mark of no-way-back.
 */
@Composable
private fun HappRegenerateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        modifier = Modifier.controllerWindowInput(onDismiss),
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_renew),
                contentDescription = null,
                tint = colors.error,
            )
        },
        title = { Text(stringResource(R.string.happ_regenerate_title)) },
        text = { Text(stringResource(R.string.happ_regenerate_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
            ) {
                Text(stringResource(R.string.happ_regenerate_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.subscription_cancel))
            }
        },
    )
}

/**
 * The regenerator, the fold's first row: the row asks, the dialog does — a fresh
 * identity is a deliberate tap, not a stray one on a whole-row button. Awake whatever
 * the toggle says: new strings are worth picking before the spoof ever rides.
 */
@Composable
private fun HappRegenerateRow(
    onRegenerate: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val regenSource = remember { MutableInteractionSource() }
    val regenPressed by regenSource.collectIsPressedAsState()
    val poke = rememberHapticPoke()
    // Bumped in the click handler: the pop keys off it, so it fires on every click.
    var regenTaps by remember { mutableIntStateOf(0) }
    // The controller's eye on the row: the whole row carries the focus, and its renew
    // tile — the row's own trailing mark — wears the ring for it.
    var rowFocused by remember { mutableStateOf(false) }
    // The toggle tile's own resting tone and pressed dip.
    val resting = colors.secondaryContainer
    val container by animateColorAsState(
        targetValue = if (regenPressed) lerp(resting, colors.onSurface, 0.1f) else resting,
        animationSpec = motion.defaultEffectsSpec(),
        label = "regen tile container",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { rowFocused = it.isFocused }
            .combinedClickable(
                interactionSource = regenSource,
                indication = LocalIndication.current,
                role = Role.Button,
            ) {
                poke()
                regenTaps++
                onRegenerate()
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.happ_regenerate),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        // The tile the header's trailing pair wore, trailing still: the toggle's own
        // square, its renew mark centered.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = container,
            contentColor = colors.onSecondaryContainer,
            modifier = Modifier
                .size(48.dp)
                .pressBounce(regenPressed, regenTaps)
                // The row carries the focus; the tile wears the ring for it — the
                // row's own trailing mark.
                .focusRing(focused = { rowFocused }),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_renew),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** A plain text row: a title with a supporting line, no icon, no interaction. */
@Composable
private fun PlainRow(
    title: String,
    supporting: String?,
    /** Sits after the supporting text on its own line — the version row's stage chip. */
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .controllerReadFocus()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (supporting != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing()
                }
            }
        }
    }
}

/**
 * The "About" page: the welcome screen's own hero turned upright — the app's mark big
 * at the top of the page, its name under it, the tagline under that — then the build's
 * own numbers, and the project's two homes. The core's version is the number alone,
 * with its run state riding beside it as a quiet pill — the version is knowable either
 * way, the process answers from its first boot on.
 */
@Composable
fun AboutScreen(
    coreVersion: String?,
    coreRunning: Boolean,
    modifier: Modifier = Modifier,
    update: UpdateUiState = UpdateUiState(),
    onUpdateChipTap: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val contentUnderBar by remember(scrollState) {
        derivedStateOf { scrollState.canScrollForward || scrollState.canScrollBackward }
    }
    val contentReachesBar by rememberContentUnderBar(scrollState)
    ReportBarCoverage(contentUnderBar, underlaid = contentReachesBar)
    // The hero lies bare on the wallpaper, and the wallpaper owes it nothing: the mark,
    // the name, the tagline recolor by the dimmed picture's own light — too dark behind
    // and the text turns light, too bright and it turns dark, the backdrop scheme's own
    // roles either way, the swap riding the house's color clock. With no picture the
    // theme carries them, as it always has.
    val heroScheme = if (LocalHasWallpaper.current) {
        backdropScheme(darkTheme = LocalBackdropDark.current)
    } else {
        MaterialTheme.colorScheme
    }
    val motion = MaterialTheme.motionScheme
    val markColor by animateColorAsState(
        targetValue = heroScheme.primary,
        animationSpec = motion.defaultEffectsSpec(),
        label = "about mark color",
    )
    val nameColor by animateColorAsState(
        targetValue = heroScheme.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "about name color",
    )
    val taglineColor by animateColorAsState(
        targetValue = heroScheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "about tagline color",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .screenPadding()
            .verticalScroll(scrollState)
            .padding(
                // The hero's own sky: the mark stands a mark's height clear of the top
                // edge, so the name under it sits well down the page, on its way to the
                // middle rather than hugging the status bar.
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 96.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    LocalBarReservedHeight.current,
            ),
    ) {
        // The hero, the welcome screen's own text stack standing at the top of the page
        // instead of hanging off its bottom: the mark beside the waving hand there, here
        // grown to stand level with its own name below, the one-liner under that.
        Column(Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(R.drawable.ic_chumpy_mark),
                contentDescription = null,
                tint = markColor,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = nameColor,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = taglineColor,
            )
        }

        // The greeting keeps its distance from the cards below, as on the welcome.
        Spacer(Modifier.height(24.dp))

        // A release the repo holds beyond the running build: the failure card's own
        // cut retold in the accent, under the hero and over the numbers — and unlike
        // the home chip, a wave-away does not touch it: it hangs as long as the repo
        // holds anything newer. The tap pauses a running download, resumes a held
        // one, installs an APK already on disk, and otherwise starts one.
        if (update.available != null) {
            UpdateCard(
                downloading = update.downloading,
                paused = update.paused,
                percent = update.downloadPercent,
                message = when {
                    update.paused -> stringResource(R.string.update_paused, update.downloadPercent)
                    update.downloadedFile != null -> stringResource(R.string.update_ready)
                    else -> stringResource(R.string.update_available_chip)
                },
                onClick = onUpdateChipTap,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }

        SettingsGroup(
            {
                PlainRow(
                    title = stringResource(R.string.settings_about_version),
                    supporting = BuildConfig.VERSION_NAME,
                    // The build's own stage rides with the number it qualifies — the
                    // chip's compact cut, one quiet word on the value's own line.
                    trailing = {
                        InfoChip(
                            value = stringResource(R.string.badge_alpha),
                            compact = true,
                        )
                    },
                )
            },
            {
                PlainRow(
                    title = stringResource(R.string.settings_about_core),
                    supporting = coreVersion,
                    // The run state rides as the stage chip's twin — a quiet pill on
                    // the version's own line, the process's answer from its first boot on.
                    trailing = {
                        InfoChip(
                            value = stringResource(
                                if (coreRunning) R.string.settings_about_core_running
                                else R.string.settings_about_core_not_running,
                            ),
                            compact = true,
                        )
                    },
                )
            },
            {
                PlainRow(
                    title = stringResource(R.string.settings_about_build_date),
                    supporting = BuildConfig.BUILD_DATE,
                )
            },
        )

        Spacer(Modifier.height(16.dp))

        // The update check's own ask, over the language below it: the repo is polled
        // on every open and this row asks again on demand. The system's own per-app
        // language sheet joins it — below Android 13 the sheet doesn't exist and the
        // row stays out with it.
        val checkRow: @Composable () -> Unit = {
            CheckForUpdatesRow(
                checking = update.checking,
                updateAvailable = update.available != null,
                onClick = onCheckForUpdate,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SettingsGroup(
                checkRow,
                { LanguageRow() },
            )
        } else {
            SettingsGroup(
                checkRow,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            {
                ExternalLinkRow(
                    title = stringResource(R.string.settings_about_telegram),
                    supporting = stringResource(R.string.settings_about_telegram_handle),
                    icon = R.drawable.ic_telegram,
                    url = "https://t.me/ChumpyXray",
                )
            },
            {
                ExternalLinkRow(
                    title = stringResource(R.string.settings_about_github),
                    supporting = stringResource(R.string.settings_about_github_handle),
                    icon = R.drawable.ic_github,
                    url = "https://github.com/ChumpyXray/Chumpy",
                )
            },
        )
    }
}

/**
 * A row that leaves the app: the screen-link row's own layout, but the tap opens the
 * browser on [url] instead of one of this app's own screens. The trailing mark is the
 * brand's own sign on the tile, alone — no "menu" triangle: nothing unfolds here, the
 * row simply goes out.
 */
@Composable
private fun ExternalLinkRow(
    title: String,
    supporting: String,
    icon: Int,
    url: String,
) {
    val context = LocalContext.current
    OutgoingRow(
        title = title,
        supporting = supporting,
        icon = icon,
    ) {
        context.externalAction { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
}

/**
 * The manual update check: the repo is polled on every open, and this row asks again —
 * the answer riding the supporting line, the check in flight, the release waiting, or
 * nothing beyond the running build.
 */
@Composable
private fun CheckForUpdatesRow(
    checking: Boolean,
    updateAvailable: Boolean,
    onClick: () -> Unit,
) {
    OutgoingRow(
        title = stringResource(R.string.settings_about_check_updates),
        supporting = stringResource(
            when {
                checking -> R.string.update_checking
                updateAvailable -> R.string.update_available_chip
                else -> R.string.update_up_to_date
            },
        ),
        icon = R.drawable.ic_renew,
        spinning = checking,
        onClick = onClick,
    )
}

/**
 * The language row: the app's own current language named in its own words (English,
 * Русский), the tap handing the choice over to the system's per-app language sheet —
 * the app keeps no language setting of its own to drift out of step with it.
 */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    OutgoingRow(
        title = stringResource(R.string.settings_about_language),
        supporting = locale.getDisplayName(locale),
        icon = R.drawable.ic_language,
    ) {
        context.externalAction { context.startActivity(
            Intent(
                if (android.os.Build.VERSION.SDK_INT >= 33) Settings.ACTION_APP_LOCALE_SETTINGS
                else Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        ) }
    }
}

/** The shared body of the rows that leave the app somewhere on their tap. */
@Composable
private fun OutgoingRow(
    title: String,
    supporting: String,
    icon: Int,
    /** The mark spins while the row's own work runs (the update check). */
    spinning: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val poke = rememberHapticPoke()
    // Bumped in the click handler: the pop keys off it, so it fires on every click — the
    // press state alone can go unnoticed on a quick tap.
    var taps by remember { mutableIntStateOf(0) }
    // The controller's eye on the row: the whole row carries the focus, and its tile
    // wears the ring for it — the row's own trailing mark.
    var rowFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { rowFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ) {
                // The leaving goes first: the vibrator's binder call can sit on slow
                // ROMs for a good while, and the handoff must never wait behind it.
                onClick()
                poke()
                taps++
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The tile bounces as one widget when tapped.
        Box(
            modifier = Modifier.pressBounce(pressed, taps),
        ) {
            LinkIconTile(icon = icon, pressed = pressed, focused = { rowFocused }, spinning = spinning)
        }
    }
}

/**
 * The launcher icon picker: the variants as the icons themselves — round, the way a
 * launcher draws them, not glyph-in-a-tile like the other pickers. Chumpy is the flat sky
 * art on white, Inverted swaps the two, Dark lays the same white art on a near-black night
 * sky, Themed shows the app's own live scheme (the neutral art over the surface, in the
 * accent's primary). Picking one swaps the launcher entry on the spot, through the
 * activity aliases in the manifest.
 *
 * Like the font row, the previews sit beside the title while the run of them fits; once
 * they would crowd it, the title steps aside and the previews take the row's full width
 * beneath it.
 */
@Composable
private fun IconVariantRow(
    selected: IconVariant,
    onSelect: (IconVariant) -> Unit,
) {
    // Themed is benched for now — its launcher alias doesn't sit right yet.
    // Everything behind it (the alias, the storage, the QS tile) stays, so it
    // walks back in the day it's fixed.
    val variants = IconVariant.entries.filter { it != IconVariant.THEMED }
    val title = stringResource(R.string.settings_app_icon)

    // The title block, shared by both layouts below: the setting's name over the picked
    // variant's, like the font and flag rows.
    val labels: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    when (selected) {
                        IconVariant.CHUMPY -> R.string.settings_icon_chumpy
                        IconVariant.INVERTED -> R.string.settings_icon_inverted
                        IconVariant.DARK -> R.string.settings_icon_dark
                        IconVariant.THEMED -> R.string.settings_icon_themed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // The previews themselves, shared too: tighter than the swatches look, every
    // preview's wrapper carries its own margin around the 48dp icon.
    val previews: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            variants.forEach { variant ->
                IconPreviewButton(
                    variant = variant,
                    selected = variant == selected,
                    onClick = { onSelect(variant) },
                )
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val titleStyle = MaterialTheme.typography.bodyLarge
        val titleWidth = remember(title, titleStyle, density) {
            with(density) { measurer.measure(title, titleStyle).size.width.toDp() }
        }
        // Everything the inline layout needs on one line: the padding either side, the
        // title, the gap after it and the whole run of previews.
        val runWidth = ICON_PREVIEW_SIZE * variants.size + 4.dp * (variants.size - 1)
        val inlineWidth = 16.dp * 2 + titleWidth + 16.dp + runWidth
        if (inlineWidth <= maxWidth) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels(Modifier.weight(1f))
                Row(modifier = Modifier.padding(start = 16.dp)) {
                    previews()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                labels(Modifier)
                Spacer(Modifier.height(12.dp))
                previews()
            }
        }
    }
}

/**
 * One variant's preview: a 48dp circle carrying the real icon — background color, art
 * color, exactly what the launcher will show — riding centered in a wider wrapper that
 * carries the pick's mark: the accent's ring, grown with a breath of clearance between
 * it and the icon so the two never touch. The art is the monochrome mask (an alpha
 * drawing), tinted per variant, drawn 1.4 times the launcher's own scale — the adaptive
 * icon's safe margins swallow half the circle, and the mark would read as a crumb. The
 * hairline outline keeps the white-on-white, dark-on-dark and surface-on-surface variants
 * from dissolving into the card.
 */
@Composable
private fun IconPreviewButton(
    variant: IconVariant,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val sky = Color(0xFF6FACED)
    val night = Color(0xFF191919)
    val (background, art) = when (variant) {
        IconVariant.CHUMPY -> Color.White to sky
        IconVariant.INVERTED -> sky to Color.White
        // The dark icon: the white art on its near-black night sky.
        IconVariant.DARK -> night to Color.White
        // The live scheme, as the QS tile and the app itself draw the themed icon.
        IconVariant.THEMED -> colors.surface to colors.primary
    }
    val label = stringResource(
        when (variant) {
            IconVariant.CHUMPY -> R.string.settings_icon_chumpy
            IconVariant.INVERTED -> R.string.settings_icon_inverted
            IconVariant.DARK -> R.string.settings_icon_dark
            IconVariant.THEMED -> R.string.settings_icon_themed
        },
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val poke = rememberHapticPoke()
    // Bumped in the click handler: the pop keys off it, so it fires on every click.
    var taps by remember { mutableIntStateOf(0) }
    // The pick's mark: the accent's ring, a breath off the icon — never touching it —
    // while the icon itself yields, shrinking a little under the ring so the marked
    // preview reads no bigger than its neighbors.
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "icon preview ring width",
    )
    val mark by animateFloatAsState(
        targetValue = if (selected) ICON_MARK_SCALE else 1f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "icon preview mark scale",
    )
    Box(
        modifier = Modifier
            .size(ICON_PREVIEW_SIZE)
            .pressBounce(pressed, taps)
            .clip(CircleShape)
            .drawBehind {
                // Drawn only once there's width to draw: a zero-width stroke would still
                // leave its hairline behind, a phantom ring at rest.
                val stroke = ringWidth.toPx()
                if (stroke > 0.5f) {
                    drawCircle(
                        color = colors.primary,
                        style = Stroke(stroke),
                        // The ring rides off the shrinking icon's own rim, the gap held
                        // at a breath — it closes in as the icon yields, settling level
                        // with the neighbors' edges.
                        radius = 24.dp.toPx() * mark + 3.dp.toPx() + stroke / 2,
                    )
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
            ) {
                poke()
                taps++
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = mark
                    scaleY = mark
                }
                .clip(CircleShape)
                .background(background)
                .border(1.dp, colors.outlineVariant, CircleShape),
        ) {
            Image(
                painter = painterResource(
                    when (variant) {
                        IconVariant.CHUMPY -> R.drawable.ic_launcher_foreground
                        // White art: shown as-is for Inverted and Dark, tinted with the
                        // live primary for Themed — the vector is the real launcher art,
                        // not a stand-in.
                        IconVariant.INVERTED -> R.drawable.ic_launcher_fg_inverted
                        IconVariant.DARK -> R.drawable.ic_launcher_fg_dark
                        IconVariant.THEMED -> R.drawable.ic_launcher_fg_inverted
                    },
                ),
                contentDescription = null,
                colorFilter = if (variant == IconVariant.THEMED) ColorFilter.tint(art) else null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.4f),
            )
        }
    }
}

/** How far the icon yields under the pick's ring. */
private const val ICON_MARK_SCALE = 0.85f

/** One preview's wrapper: the 48dp icon plus the room its pick's ring needs around it. */
private val ICON_PREVIEW_SIZE = 60.dp
