package slop.miho.chumpy.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import slop.miho.chumpy.R
import slop.miho.chumpy.core.TunnelState
import slop.miho.chumpy.data.FlagStyle
import slop.miho.chumpy.data.SettingsStore
import slop.miho.chumpy.ui.theme.OrbWidgetColors
import kotlin.math.max

/**
 * What the orb says: the flag the panel put on the name (or the globe when there
 * is none) alone in its middle, the region's own name bent along the rim, and the
 * subscription's — a touch smaller, climbing the other side.
 */
data class WidgetFace(
    val flag: String?,
    val region: String,
    val subscription: String?,
)

/**
 * One pinned tile: a flag alone in a circle riding the orb's top edge. [active] marks the
 * pin the running (or selected) server is, so the fill tells the picked one from the rest.
 */
data class WidgetPin(
    val id: String,
    val flag: String?,
    val label: String,
    val active: Boolean,
)

/**
 * The whole launcher widget, drawn into one bitmap. RemoteViews can do neither the
 * geometry nor the type: the connect button sits over the orb's bottom-right corner,
 * the pin circles ride the top edge the same way, and the flags want the
 * bundled Twemoji font — none of which an ImageView-with-text arrives at. Drawing it
 * all ourselves also keeps the interactive zones honest: the provider frames its
 * invisible zone views to the very pixel boxes these paints draw.
 *
 * Nothing is measured off the widget's box — that box is never quite square, and
 * anything that follows it stretches. Everything is measured off the orb alone
 * ([orbBox]): the circle fills the box's short side, the connect button's side
 * is [BUTTON_SHARE] of that side with its box flush in the orb square's bottom-right
 * corner, and the pin
 * tiles ride the orb's top-left corner flush, three slots wide — squares stay
 * squares at any stretch.
 */
object WidgetRenderer {

    /** How many pin tiles the layout carves room for; the provider may fill fewer. */
    const val PIN_SLOTS = 3

    /**
     * The rim texts' anchors and reach, in screen degrees off the orb's middle
     * (0 = right, 90 bottom). The region ends just left of the bottom and
     * bends up the left side — never crossing under the connect button, and
     * stopping short of the pin row above; the subscription starts just above
     * the button's shoulder and climbs the right side until the pins crowd it
     * from the top-left.
     */
    private const val REGION_ANCHOR = 96f
    private const val REGION_SWEEP = 110f
    private const val SUB_ANCHOR = 14f
    private const val SUB_SWEEP = 56f

    @Volatile
    private var inter: Typeface? = null

    @Volatile
    private var twemoji: Typeface? = null

    fun render(
        context: Context,
        width: Int,
        height: Int,
        state: TunnelState,
        face: WidgetFace,
        pins: List<WidgetPin>,
        colors: OrbWidgetColors,
    ): Bitmap {
        if (width <= 0 || height <= 0) return createBitmap(1, 1)
        // A fresh bitmap every paint: a repaint is a state change, and caching would let
        // one paint erase the canvas under another's RemoteViews hand-off.
        val bitmap = createBitmap(width, height)
        paint(context, Canvas(bitmap), width, height, state, face, pins, colors)
        return bitmap
    }

    private fun paint(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        state: TunnelState,
        face: WidgetFace,
        pins: List<WidgetPin>,
        colors: OrbWidgetColors,
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val short = max(1f, minOf(w, h))
        val radius = short / 2f
        val cx = w / 2f
        val cy = h / 2f
        val up = state == TunnelState.UP
        // The orb is the widget's background made round: one color, whatever the tunnel
        // does — the connect button alone carries the state. No ring, no loading show.
        val fill = colors.background
        val content = colors.onBackground
        val flagStyle = SettingsStore.settings(context).value.flagStyle

        // ---- the orb ------------------------------------------------------------
        canvas.drawCircle(cx, cy, radius, fillPaint(fill))

        // ---- the middle: the flag alone, a touch bigger than the stack it
        //      used to share --------------------------------------------------------
        val flagBox = short * 0.33f
        val flagLayout = flagLayout(context, face.flag, flagStyle, flagBox * 0.85f)
        if (flagLayout != null) {
            canvas.withTranslation(cx - flagLayout.width / 2f, cy - flagLayout.height / 2f) {
                flagLayout.draw(this)
            }
        } else {
            drawVector(context, R.drawable.ic_public, content, canvas, cx, cy, flagBox)
        }

        // ---- the rim: the region bends along the circle's edge down the
        //      bottom-left, curving with it so long names fit inside; the
        //      subscription climbs the right side from just above the
        //      button's shoulder, where the button leaves it less room ------
        val rim = radius * 0.92f
        drawRimText(
            canvas, context, face.region, cx, cy, rim,
            size = short * 0.10f,
            color = content,
            bold = true,
            anchorDeg = REGION_ANCHOR,
            maxSweepDeg = REGION_SWEEP,
            anchorEnd = true,
        )
        face.subscription?.let { sub ->
            drawRimText(
                canvas, context, sub, cx, cy, rim,
                size = short * 0.074f,
                color = ColorUtils.setAlphaComponent(content, 185),
                bold = false,
                anchorDeg = SUB_ANCHOR,
                maxSweepDeg = SUB_SWEEP,
                anchorEnd = false,
            )
        }

        // ---- the connect button: size tied to the orb alone, box flush in the orb
        //      square's bottom-right corner ---------------------------------------
        val button = buttonBox(w, h)
        val d = button.width()
        // Connected wears the accent; idle is the quiet inverted tone — the same pair
        // the app's own button keeps, just the other way around.
        val buttonFill = if (up) colors.primary else colors.onPrimary
        val buttonGlyph = if (up) colors.onPrimary else colors.primary
        canvas.drawRoundRect(
            RectF(button.left, button.top, button.right, button.bottom),
            d * 0.30f,
            d * 0.30f,
            fillPaint(buttonFill),
        )
        drawVector(
            context, R.drawable.ic_power, buttonGlyph, canvas,
            button.centerX(), button.centerY(), d * 0.46f,
        )

        // ---- the pinned circles --------------------------------------------------
        val count = pins.size.coerceAtMost(PIN_SLOTS)
        pins.take(count).forEachIndexed { index, pin ->
            val tile = pinRect(w, h, count, index) ?: return@forEachIndexed
            val side = tile.width()
            // No rims anywhere on this widget: the picked pin tells itself apart
            // by its fill alone, the way the connect button's color tells the tunnel.
            val tileFill = if (pin.active) colors.primary else colors.surfaceContainerHighest
            canvas.drawCircle(tile.centerX(), tile.centerY(), side / 2f, fillPaint(tileFill))
            val glyphBox = side * 0.70f
            val glyph = flagLayout(context, pin.flag, flagStyle, glyphBox * 0.85f)
            if (glyph != null) {
                canvas.withTranslation(
                    tile.left + (tile.width() - glyph.width) / 2f,
                    tile.top + (tile.height() - glyph.height) / 2f,
                ) {
                    glyph.draw(this)
                }
            } else {
                drawVector(
                    context,
                    R.drawable.ic_public,
                    if (pin.active) colors.onPrimary else colors.onSurfaceVariant,
                    canvas,
                    tile.centerX(),
                    tile.centerY(),
                    glyphBox,
                )
            }
        }
    }

    /**
     * A plain frame in float coordinates — spelled here rather than borrowed from
     * android.graphics.RectF, which the JVM tests meet only as a stub returning
     * zeroes. The painter and the provider both read their geometry through it.
     */
    internal data class FrameF(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun width() = right - left

        fun height() = bottom - top

        fun centerX() = (left + right) / 2f

        fun centerY() = (top + bottom) / 2f
    }

    /**
     * The whole widget's geometry, measured off the orb alone — never off the box the
     * launcher hands over, which is never quite square. Everything squares against
     * [orbBox]: the orb's own bounding square, centered in the box with the box's
     * short side. The painter draws by these and the provider frames the touch zones
     * by the same numbers, so squares stay squares and circles stay circles at any
     * stretch the widget is resized to.
     */
    internal fun orbBox(w: Float, h: Float): FrameF {
        val side = max(1f, minOf(w, h))
        val left = (w - side) / 2f
        val top = (h - side) / 2f
        return FrameF(left, top, left + side, top + side)
    }

    /** The button's side against the orb's side. */
    internal val BUTTON_SHARE = 0.34f

    /**
     * The connect button: its side is a share of the orb and nothing else — resize
     * the widget and the button scales with the circle, never with the box — and its
     * box sits flush in the orb square's bottom-right corner. That corner is fixed
     * against the circle, so a stretch never slides the button around; the larger
     * side simply moves its center a little further inside the rim.
     */
    internal fun buttonBox(w: Float, h: Float): FrameF {
        val orb = orbBox(w, h)
        val d = BUTTON_SHARE * orb.width()
        return FrameF(orb.right - d, orb.bottom - d, orb.right, orb.bottom)
    }

    /**
     * One pinned slot's square: the row rides the orb box's top-left corner flush,
     * one tile after another with a gap between. All
     * of it in shares of the orb's side, so the tiles are square whatever the box.
     * Null when the row holds no such slot.
     */
    internal fun pinRect(w: Float, h: Float, count: Int, index: Int): FrameF? {
        if (count !in 1..PIN_SLOTS || index !in 0 until count) return null
        val orb = orbBox(w, h)
        val side = orb.width()
        val tile = PinRow.side(count) * side
        val gap = PinRow.GAP * side
        val left = orb.left + index * (tile + gap)
        val top = orb.top
        return FrameF(left, top, left + tile, top + tile)
    }

    /**
     * The pinned row's own measure, in shares of the orb's side — spelled once so the
     * painter and the touch zones agree on it. The tiles sit flush with the corner and
     * keep [MAX_SIDE] across the row's three slots.
     */
    internal object PinRow {
        /** The breathing room between pin and pin. */
        const val GAP = 0.015f

        /** The pin size every one of the row's three slots keeps. */
        const val MAX_SIDE = 0.26f

        fun side(count: Int): Float {
            val n = count.coerceIn(1, PIN_SLOTS)
            return minOf(MAX_SIDE, (1f - (n - 1) * GAP) / n)
        }
    }

    // --------------------------------------------------------------- flag glyph

    /**
     * The flag as laid-out text — StaticLayout, so the Twemoji font's GSUB turns the
     * regional-indicator pair into the flag glyph the same way the in-app text does.
     * GLOBE style answers null (the globe vector takes the slot); so does a blank flag.
     * The line is centered inside the box the painter then centers on the orb, so a
     * wide layout never drags the glyph off the middle.
     */
    private fun flagLayout(context: Context, flag: String?, style: FlagStyle, size: Float): StaticLayout? {
        if (flag.isNullOrBlank() || style == FlagStyle.GLOBE || size <= 0f) return null
        val text = flag.trim()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = size
        if (style == FlagStyle.TWEMOJI) paint.typeface = twemojiTypeface(context)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, (size * 4f).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
    }

    // --------------------------------------------------------------- rim text

    /**
     * One line bent along the orb's rim — the badge way to fit words beside the
     * circle's own roundness. The glyphs stand with their tops toward the middle
     * and the baseline riding [radius]; the line grows from [anchorDeg] (screen
     * degrees: 0 right, 90 bottom) and bends no further than [maxSweepDeg],
     * ellipsizing past that. [anchorEnd] pins the line's last glyph at the
     * anchor — the region, reading down into the bottom; otherwise the first
     * glyph sits there and the line climbs away from it — the subscription,
     * dodging the connect button.
     */
    private fun drawRimText(
        canvas: Canvas,
        context: Context,
        text: String,
        cx: Float,
        cy: Float,
        radius: Float,
        size: Float,
        color: Int,
        bold: Boolean,
        anchorDeg: Float,
        maxSweepDeg: Float,
        anchorEnd: Boolean,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || radius <= 0f || maxSweepDeg <= 0f) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        paint.textSize = size
        interTypeface(context)?.let { typeface ->
            paint.typeface = typeface
            if (bold) runCatching { paint.fontVariationSettings = "'wght' 650" }
        }
        if (bold && paint.typeface == null) paint.isFakeBoldText = true
        val avail = Math.toRadians(maxSweepDeg.toDouble()).toFloat() * radius
        val shown = TextUtils.ellipsize(trimmed, paint, avail, TextUtils.TruncateAt.END) as String
        if (shown.isEmpty()) return
        val sweepDeg = Math.toDegrees((paint.measureText(shown) / radius).toDouble()).toFloat()
        // The path runs the reading direction: toward the anchor for the region,
        // away upward for the subscription — a negative sweep walks the rim
        // counterclockwise, the way both lines read.
        val startDeg = if (anchorEnd) anchorDeg + sweepDeg else anchorDeg
        val path = Path()
        path.addArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), startDeg, -sweepDeg)
        canvas.drawTextOnPath(shown, path, 0f, 0f, paint)
    }

    // --------------------------------------------------------------- plumbing

    private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color }

    private fun drawVector(
        context: Context,
        res: Int,
        tint: Int,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
    ) {
        val drawable = context.getDrawable(res)?.mutate() ?: return
        drawable.setTint(tint)
        val half = size / 2f
        drawable.setBounds(
            (cx - half).toInt(),
            (cy - half).toInt(),
            (cx + half).toInt(),
            (cy + half).toInt(),
        )
        drawable.draw(canvas)
    }

    /** The bundled Inter, loaded once per process; a failed load falls back to the default. */
    private fun interTypeface(context: Context): Typeface? {
        inter?.let { return it }
        val loaded = ResourcesCompat.getFont(context, R.font.inter_variable)
        inter = loaded
        return loaded
    }

    /** The bundled Twemoji, loaded once per process; the system emoji font is the fallback. */
    private fun twemojiTypeface(context: Context): Typeface? {
        twemoji?.let { return it }
        val loaded = ResourcesCompat.getFont(context, R.font.twemoji)
        twemoji = loaded
        return loaded
    }
}
