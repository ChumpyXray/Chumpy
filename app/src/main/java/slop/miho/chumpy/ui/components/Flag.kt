package slop.miho.chumpy.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import slop.miho.chumpy.R

/** Mozilla's COLR build of Twemoji; its GSUB turns regional-indicator pairs into flags. */
private val TwemojiFamily = FontFamily(Font(R.font.twemoji))

/**
 * The emoji flag of an ISO 3166-1 alpha-2 code ("FI" → 🇫🇮): each letter maps to its
 * regional indicator symbol, the same conversion the panel's web page does.
 */
fun countryFlagEmoji(code: String): String = buildString {
    code.forEach { letter -> appendCodePoint(REGIONAL_INDICATOR_A + (letter - 'A')) }
}

/**
 * A country's flag drawn as emoji, centered in a [size] box. [twemoji] picks the bundled
 * Twemoji font over the system's emoji. The glyph is sized in dp, not sp: it stands in for
 * an icon, so the user's font scale shouldn't swell it past its backing.
 */
@Composable
fun CountryFlag(
    code: String,
    twemoji: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    FlagEmoji(countryFlagEmoji(code), twemoji, modifier, size)
}

/**
 * Any flag emoji string — the leading flag parsed off a profile's name — drawn like
 * [CountryFlag] draws a code's flag: centered in a [size] box, Twemoji or the system's.
 */
@Composable
fun EmojiFlag(
    emoji: String,
    twemoji: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    FlagEmoji(emoji, twemoji, modifier, size)
}

/** The rainbow flag (🏳️‍🌈): the white flag, VS16 and a rainbow joined by a ZWJ. */
private const val RAINBOW_FLAG_EMOJI = "🏳️‍🌈"

/** The trans flag (🏳️‍⚧️): the white flag, VS16 and the trans symbol joined by a ZWJ. */
private const val TRANS_FLAG_EMOJI = "🏳️‍⚧️"

/**
 * The pride flags a profile's name can lead with, and the easter egg they trip: the row
 * drifts through the rainbow, the [PrideFlag.RAINBOW] accent's slow cycle in miniature.
 */
enum class PrideFlag {
    RAINBOW,
    TRANS,
}

/**
 * Splits a leading pride flag off a profile's name, [splitLeadingFlag]'s sibling for the
 * emoji panels sometimes put there instead of (or before) a country's flag. Returns the
 * pride flag and the rest of the name, trimmed; a name without one keeps a null flag.
 */
fun splitLeadingPrideFlag(name: String): Pair<PrideFlag?, String> {
    val trimmed = name.trimStart()
    return when {
        trimmed.startsWith(RAINBOW_FLAG_EMOJI) ->
            PrideFlag.RAINBOW to trimmed.substring(RAINBOW_FLAG_EMOJI.length).trimStart()

        trimmed.startsWith(TRANS_FLAG_EMOJI) ->
            PrideFlag.TRANS to trimmed.substring(TRANS_FLAG_EMOJI.length).trimStart()

        else -> null to name
    }
}

/**
 * The pride flag itself in an icon slot: the rainbow gets Twemoji's own drawable, the
 * trans one rides the system emoji (the Twemoji font has no layered glyph for it).
 */
@Composable
fun PrideFlagIcon(
    pride: PrideFlag,
    twemoji: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    when (pride) {
        PrideFlag.RAINBOW -> RainbowFlag(twemoji, modifier, size)
        PrideFlag.TRANS -> FlagEmoji(TRANS_FLAG_EMOJI, twemoji = false, modifier, size)
    }
}

/**
 * The rainbow flag, drawn like a [CountryFlag]. The system emoji take the text; Twemoji's
 * comes from a drawable of the font's own layers instead — the font has the glyph, but text
 * layout hands any VS16 sequence to the system emoji font, so the text would never reach it.
 * The drawable is sized like the glyph would be at a [size] box.
 */
@Composable
fun RainbowFlag(
    twemoji: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    if (!twemoji) {
        FlagEmoji(RAINBOW_FLAG_EMOJI, twemoji = false, modifier, size)
        return
    }
    val width = size * FLAG_GLYPH_SCALE
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_flag_rainbow_twemoji),
            contentDescription = null,
            modifier = Modifier.size(width, width * RAINBOW_FLAG_ASPECT),
        )
    }
}

/** The Twemoji flag glyph's height over its width, in font units. */
private const val RAINBOW_FLAG_ASPECT = 370f / 512f

/**
 * Splits the flag emoji a panel put at the front of a profile's name off the name itself:
 * clients lift it into the icon slot and show the name without it. Returns the flag (all
 * leading ones — a run of regional-indicator pairs) and the rest of the name, trimmed; a
 * name without a leading flag comes back whole with a null flag.
 */
fun splitLeadingFlag(name: String): Pair<String?, String> {
    var end = 0
    val trimmed = name.trimStart()
    while (end + 1 < trimmed.length &&
        trimmed[end] == FLAG_HIGH_SURROGATE &&
        trimmed[end + 1] in FLAG_LOW_SURROGATE_START..FLAG_LOW_SURROGATE_END
    ) {
        end += 2
    }
    if (end == 0) return null to name
    return trimmed.take(end) to trimmed.substring(end).trimStart()
}

/** The regional indicators (U+1F1E6..U+1F1FF) as a UTF-16 surrogate pair. */
private const val FLAG_HIGH_SURROGATE = '\uD83C'
private const val FLAG_LOW_SURROGATE_START = '\uDDE6'
private const val FLAG_LOW_SURROGATE_END = '\uDDFF'

/** A flag [emoji] drawn as text, centered in a [size] box. */
@Composable
private fun FlagEmoji(
    emoji: String,
    twemoji: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val fontSize = with(LocalDensity.current) { (size * FLAG_GLYPH_SCALE).toSp() }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            text = emoji,
            style = TextStyle(
                fontFamily = if (twemoji) TwemojiFamily else null,
                fontSize = fontSize,
                lineHeight = fontSize,
                textAlign = TextAlign.Center,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6

/** Emoji glyphs run a touch wider than their em; this keeps the flag inside the icon's box. */
private const val FLAG_GLYPH_SCALE = 0.85f

// ------------------------------------------------------------------ emoji stickers

/**
 * Splits the run of emoji a panel packed in front of a profile's name (past any flag)
 * off the name itself: with the sticker setting on they ride the region icon's corners
 * as outlined stickers, and the title shows the name without them. Each sticker is one
 * whole emoji cluster — base, skin tone, variation selectors, ZWJ chains; the run stops
 * at the first plain character, so emoji inside or at the end of a name stay part of it.
 * A leading pride flag is not a sticker: it leads the name its own way (the easter egg).
 */
fun splitLeadingEmoji(name: String): Pair<List<String>, String> {
    val trimmed = name.trimStart()
    if (trimmed.startsWith(RAINBOW_FLAG_EMOJI) || trimmed.startsWith(TRANS_FLAG_EMOJI)) {
        return emptyList<String>() to name
    }
    val stickers = mutableListOf<String>()
    var index = 0
    while (stickers.size < STICKER_LIMIT) {
        val clusterEnd = emojiClusterEnd(trimmed, index) ?: break
        stickers.add(trimmed.substring(index, clusterEnd))
        index = clusterEnd
        while (index < trimmed.length && trimmed[index].isWhitespace()) index++
    }
    if (stickers.isEmpty()) return emptyList<String>() to name
    return stickers to trimmed.substring(index).trimStart()
}

/** The most stickers one region tile carries; the rest of a longer run stays in the name. */
private const val STICKER_LIMIT = 8

/**
 * Where the emoji cluster starting at [index] ends (exclusive), or null when no emoji
 * base sits there. Regional indicators are not emoji here — flags carry their own split.
 */
private fun emojiClusterEnd(text: String, index: Int): Int? {
    if (index >= text.length) return null
    val base = text.codePointAt(index)
    if (!isEmojiBase(base)) return null
    var end = index + Character.charCount(base)
    while (end < text.length) {
        val cp = text.codePointAt(end)
        val width = Character.charCount(cp)
        when {
            // The ZWJ binds whatever follows into the same glyph — the next member of
            // the family, the rainbow of a pride flag, the keycap.
            cp == ZWJ && end + width < text.length -> {
                end += width + Character.charCount(text.codePointAt(end + width))
            }
            cp.isSkinTone() || cp.isVariationSelector() || cp == KEYCAP || cp.isTag() -> end += width
            else -> break
        }
    }
    return end
}

private fun isEmojiBase(cp: Int): Boolean = when {
    // Regional indicators pair into flags; the flag split already owns them.
    cp in 0x1F1E6..0x1F1FF -> false
    cp in 0x1F000..0x1FAFF -> true                      // the emoji blocks proper
    cp in 0x2194..0x2199 || cp == 0x21A9 || cp == 0x21AA -> true  // the emoji arrows
    cp in 0x2300..0x23FF -> true                        // clocks and hourglasses
    cp in 0x25A0..0x25FF -> true                        // the geometric shapes that are emoji
    cp in 0x2600..0x27BF -> true                        // symbols and dingbats
    cp in 0x2B00..0x2BFF -> true                        // stars and thick arrows
    cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299 -> true  // the CJK emoji
    else -> false
}

private fun Int.isSkinTone(): Boolean = this in 0x1F3FB..0x1F3FF
private fun Int.isVariationSelector(): Boolean = this in 0xFE00..0xFE0F

/** A tag character (U+E0020..U+E007F): the Scotland/Wales-style flags spell theirs in tags. */
private fun Int.isTag(): Boolean = this in 0xE0020..0xE007F

private const val ZWJ = 0x200D
private const val KEYCAP = 0x20E3

/**
 * The name's leading emoji scattered around the region tile like stickers: each takes
 * its own corner or edge midpoint, thrown with its own slight tilt and nudge — the
 * dice seeded by the emoji itself, so the scatter looks hand-pinned but never
 * shimmers on a recompose. Each is drawn with its white outline (see [EmojiSticker]);
 * [twemoji] matches the flag style.
 */
@Composable
fun EmojiStickers(
    emojis: List<String>,
    twemoji: Boolean,
    modifier: Modifier = Modifier,
    tile: Dp = 40.dp,
    stickerSize: Dp = 18.dp,
) {
    val overhang = 5.dp
    val far = tile - stickerSize + overhang
    val mid = (tile - stickerSize) / 2
    // The spots the stickers take, in order: the corners first (top-end leading), then
    // the edge midpoints, so a lone sticker sits where a sticker would be pinned —
    // and a crowd of them spreads around the tile instead of piling onto one corner.
    val spots = listOf(
        far to -overhang,           // top-end
        -overhang to far,           // bottom-start
        -overhang to -overhang,     // top-start
        far to far,                 // bottom-end
        far to mid,                 // end edge
        -overhang to mid,           // start edge
        mid to -overhang,           // top edge
        mid to far,                 // bottom edge
    )
    Box(modifier = modifier.size(tile)) {
        emojis.forEachIndexed { index, emoji ->
            // The sticker's own throw of the dice, keyed by what it is and which of the
            // crowd it is: the same emoji in the same name always lands the same way.
            val (dx, dy, tilt) = remember(emoji, index) {
                val dice = Random(STICKER_SEED * (index + 1) + emoji.hashCode())
                Triple(
                    STICKER_JITTER * (dice.nextFloat() * 2f - 1f),
                    STICKER_JITTER * (dice.nextFloat() * 2f - 1f),
                    (dice.nextFloat() * 2f - 1f) * STICKER_TILT,
                )
            }
            val (x, y) = spots[index % spots.size]
            EmojiSticker(
                emoji = emoji,
                twemoji = twemoji,
                modifier = Modifier
                    .offset(x = x + dx, y = y + dy)
                    .graphicsLayer { rotationZ = tilt },
                size = stickerSize,
            )
        }
    }
}

/** The dice's seed base — any fixed number does; the emoji and its seat finish it. */
private const val STICKER_SEED = 0x5EED

/** How far a sticker may drift off its spot, either way. */
private val STICKER_JITTER = 3.dp

/** How far a sticker may tilt off level, either way, in degrees. */
private const val STICKER_TILT = 20f

/**
 * One emoji drawn as a sticker: the glyph on a small box with a thin white outline around
 * it, so it reads over whatever the tile already shows. [twemoji] matches the flag style;
 * the glyph is sized in dp, not sp, standing in for an icon like the flags do.
 */
@Composable
fun EmojiSticker(
    emoji: String,
    twemoji: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val boxPx = with(density) { size.toPx() }
    val bitmap = remember(emoji, twemoji, boxPx) { renderSticker(context, emoji, twemoji, boxPx) }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/**
 * The sticker as a bitmap: the emoji glyph drawn once, then re-drawn around itself at
 * sixteen bearings, tinted solid white — emoji arrive as color bitmaps or COLR layers,
 * so no paint color can whiten them; a color filter on the bitmap copies can. The glyph
 * on top of its white silhouette leaves [STICKER_OUTLINE_SCALE] of the box as the outline.
 */
private fun renderSticker(context: Context, emoji: String, twemoji: Boolean, boxPx: Float): ImageBitmap {
    val outline = boxPx * STICKER_OUTLINE_SCALE
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = boxPx * STICKER_GLYPH_SCALE
        typeface = if (twemoji) twemojiStickerTypeface(context) else null
    }
    // A wide glyph backs its size off so the white outline keeps clear of the edges.
    val room = boxPx - outline * 2f
    paint.measureText(emoji).takeIf { it > room }?.let { paint.textSize *= room / it }
    val width = paint.measureText(emoji)
    val metrics = paint.fontMetrics
    val side = boxPx.toInt().coerceAtLeast(1)
    // The glyph bitmap: the emoji alone, centered.
    val glyph = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    Canvas(glyph).drawText(
        emoji,
        (boxPx - width) / 2f,
        (boxPx - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent,
        paint,
    )
    // The sticker: the silhouette copies go down first, white-tinted; the color glyph
    // lands on top after, re-covering the middle and leaving the white ring around it.
    val sticker = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sticker)
    val silhouette = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
    }
    for (bearing in 0 until OUTLINE_BEARINGS) {
        val angle = (2.0 * Math.PI * bearing / OUTLINE_BEARINGS).toFloat()
        canvas.drawBitmap(glyph, outline * cos(angle), outline * sin(angle), silhouette)
    }
    canvas.drawBitmap(glyph, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    return sticker.asImageBitmap()
}

/** The bundled Twemoji, loaded once per process; the system emoji font is the fallback. */
private var cachedTwemojiTypeface: Typeface? = null

private fun twemojiStickerTypeface(context: Context): Typeface? {
    cachedTwemojiTypeface?.let { return it }
    val loaded = ResourcesCompat.getFont(context, R.font.twemoji)
    cachedTwemojiTypeface = loaded
    return loaded
}

/** The sticker glyph's share of its box — a touch tighter than the flags, so the white
 *  outline fits inside the box without touching its edges. */
private const val STICKER_GLYPH_SCALE = 0.80f

/** The white outline's width, as a share of the sticker's box. */
private const val STICKER_OUTLINE_SCALE = 0.07f

/** How many bearings the white silhouette is drawn at; sixteen makes a smooth ring. */
private const val OUTLINE_BEARINGS = 16

