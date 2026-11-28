package slop.miho.chumpy.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import slop.miho.chumpy.R
import slop.miho.chumpy.ui.wallpapers.withSurfaceOpacity

/**
 * The settings screens' shared row language, living here so the deep-dive screens (the
 * subscription request's own) speak it too without importing the main screens' file.
 */

/**
 * A rounded tonal container of setting rows: extra-large outer corners, extra-small inner
 * corners and a 2dp gap between rows instead of dividers (the MD3 grouped-list pattern the
 * server list also uses).
 *
 * [isVisible] folds rows (past the first) in and out by index. The gap travels with its row,
 * so a folding row takes its gap along; and the group's own clip keeps the outer rounding on
 * whichever row ends up last.
 */
@Composable
internal fun SettingsGroup(
    vararg rows: @Composable () -> Unit,
    isVisible: (Int) -> Boolean = { true },
) {
    val outer = MaterialTheme.shapes.extraLarge
    val inner = MaterialTheme.shapes.extraSmall
    val motion = MaterialTheme.motionScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outer),
    ) {
        rows.forEachIndexed { index, row ->
            val surface: @Composable () -> Unit = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer.withSurfaceOpacity(),
                    shape = RoundedCornerShape(
                        topStart = if (index == 0) outer.topStart else inner.topStart,
                        topEnd = if (index == 0) outer.topEnd else inner.topEnd,
                        bottomStart = if (index == rows.lastIndex) outer.bottomStart else inner.bottomStart,
                        bottomEnd = if (index == rows.lastIndex) outer.bottomEnd else inner.bottomEnd,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row()
                }
            }
            if (index == 0) {
                surface()
            } else {
                AnimatedVisibility(
                    visible = isVisible(index),
                    enter = expandVertically(motion.defaultSpatialSpec()) +
                        fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkVertically(motion.defaultSpatialSpec()) +
                        fadeOut(motion.defaultEffectsSpec()),
                ) {
                    Column(Modifier.padding(top = 2.dp)) {
                        surface()
                    }
                }
            }
        }
    }
}

/**
 * One option of an [IconChoiceRow]: its label (read out, never shown), its value, and the
 * 24dp art drawn in its tile — a plain drawable icon, or anything else (the flag previews).
 * [onLongClick] is an optional hidden action on the long tap.
 */
internal class IconChoice<T>(
    val label: Int,
    val value: T,
    val onLongClick: (() -> Unit)? = null,
    val content: @Composable () -> Unit,
) {
    constructor(@DrawableRes icon: Int, label: Int, value: T, onLongClick: (() -> Unit)? = null) : this(
        label,
        value,
        onLongClick,
        { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
    )
}

/**
 * A setting with a few mutually exclusive options, picked with square icon buttons.
 * [showSelectedLabel] names the picked option under the title, as the font row does.
 */
@Composable
internal fun <T> IconChoiceRow(
    title: String,
    options: List<IconChoice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    showSelectedLabel: Boolean = false,
    enabled: Boolean = true,
) {
    // Same content padding as the accent row below: one unified 16dp from the group edge
    // to the controls in both rows, and 12dp between the buttons.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Asleep, the whole row dims — the options stay visible, untouchable.
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
            if (showSelectedLabel) {
                options.firstOrNull { it.value == selected }?.let { option ->
                    Text(
                        text = stringResource(option.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { option ->
                SquareIconButton(
                    label = stringResource(option.label),
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    onLongClick = option.onLongClick,
                    enabled = enabled,
                    content = option.content,
                )
            }
        }
    }
}

/**
 * A square icon toggle: raised tonal container by default, the accent container while
 * selected, with the usual springy color swap. Pressing squashes it down a touch and dips
 * the container tone (a 10% on-surface state layer); every click bounces it back with a
 * pop, so a quick tap reads as clearly as a held one.
 */
@Composable
internal fun SquareIconButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val poke = rememberHapticPoke()
    // Bumped in the click handler: the pop keys off it, so it fires on every click — the
    // press state alone can go unnoticed on a quick tap.
    var taps by remember { mutableIntStateOf(0) }
    val resting = (if (selected) colors.primary else colors.secondaryContainer)
    val container by animateColorAsState(
        targetValue = if (pressed) lerp(resting, colors.onSurface, 0.1f) else resting,
        animationSpec = motion.defaultEffectsSpec(),
        label = "icon button container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSecondaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "icon button content",
    )
    // A plain surface with the click on its modifier: Surface's own onClick has no long tap.
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        modifier = Modifier
            .size(48.dp)
            // Asleep, the tile dims — visible, untouchable.
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .pressBounce(pressed, taps)
            // The controller's ring rides before the clip — outside it, as every ring
            // sits outside what it marks — the 16dp corners matching the tile's own.
            .focusRing()
            .clip(shape)
            .controllerContextMenu(onLongClick)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick?.let { action ->
                    {
                        poke()
                        taps++
                        action()
                    }
                },
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
 * A boolean setting. The whole row is the touch target, like the server list buttons; the
 * state shows as a square icon tile instead of a switch: tonal while off, accent while on,
 * with the icon swapping to match the state. Laid out by hand rather than via ListItem:
 * ListItem's own vertical geometry shifts with the line count (the tile rides at 8dp on
 * two-line rows and top-aligns on wrapped ones), while here every square button sits at the
 * same 16dp from the card edges as the pickers above.
 */
@Composable
internal fun ToggleRow(
    title: String,
    supporting: String?,
    icon: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    /** The tile leads on the left instead of trailing — the per-app master's own layout. */
    leadingTile: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val poke = rememberHapticPoke()
    // Bumped in the click handler: the tile's pop keys off it, so it fires on every click —
    // the press state alone can go unnoticed on a quick tap.
    var taps by remember { mutableIntStateOf(0) }
    // The controller's eye on the row: the whole row carries the focus, and its tile —
    // the switch's own stand-in — wears the ring for it.
    var rowFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Asleep, the whole row dims — the state stays readable, untouchable.
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .onFocusChanged { rowFocused = it.isFocused }
            .controllerContextMenu(onLongClick)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onLongClick = onLongClick?.let { action ->
                    {
                        poke()
                        action()
                    }
                },
            ) {
                poke()
                taps++
                onChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingTile) {
            ToggleIconTile(
                icon = icon,
                checked = checked,
                pressed = pressed,
                taps = taps,
                focused = { rowFocused },
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            supporting?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!leadingTile) {
            ToggleIconTile(
                icon = icon,
                checked = checked,
                pressed = pressed,
                taps = taps,
                focused = { rowFocused },
            )
        }
    }
}

/** The square icon tile that replaces a switch: raised tonal container while off, the
 *  accent container while on, and a crossfading icon in the middle. While the row is held
 *  down the tile squashes and dips its tone, and every click bounces it with a pop. The
 *  row carries the focus, never the tile — [focused] lends it the row's own, so the ring
 *  lands on the row's trailing mark. */
@Composable
internal fun ToggleIconTile(
    icon: Int,
    checked: Boolean,
    pressed: Boolean,
    taps: Int,
    focused: (() -> Boolean)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val resting = (if (checked) colors.primary else colors.secondaryContainer)
    val container by animateColorAsState(
        targetValue = if (pressed) lerp(resting, colors.onSurface, 0.1f) else resting,
        animationSpec = motion.defaultEffectsSpec(),
        label = "toggle tile container",
    )
    val content by animateColorAsState(
        targetValue = if (checked) colors.onPrimary else colors.onSecondaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "toggle tile content",
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        modifier = Modifier
            .size(48.dp)
            .pressBounce(pressed, taps)
            .focusRing(focused = focused),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Crossfade(
                targetState = icon,
                animationSpec = motion.defaultEffectsSpec(),
                label = "toggle tile icon",
            ) { res ->
                Icon(
                    painter = painterResource(res),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * One fingerprint field on its own line: a plain row's own two lines — the value where
 * the title goes and the field's name where the supporting goes — only the title line
 * is the editable one, a bare text field with no stadium and no air of its own, so
 * seven of them in a stack read as rows, not as a form. The pencil keeps its place at
 * the end, asleep, marking the row for a text field. Committed on every keystroke;
 * Done commits the trim.
 */
@Composable
internal fun HappFieldRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    // The same ask as the delay test URL's field: the tap lands before the keyboard
    // does, so the field re-asks for its visibility once the keys have risen.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottom, focused) {
        if (focused && imeBottom > 0) requester.bringIntoView()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Asleep, the whole row dims — the value stays readable, uneditable.
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val trimmedNow = value.trim()
                if (value != trimmedNow) onChange(trimmedNow)
            }),
            decorationBox = { field ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    field()
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
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
        // The pencil, asleep: a pencil, no press, marking the row for a text field.
        Surface(
            shape = CircleShape,
            color = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            modifier = Modifier
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
}
