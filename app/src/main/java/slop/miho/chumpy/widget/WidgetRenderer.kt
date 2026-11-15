package slop.miho.chumpy.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import kotlin.math.sqrt

/**
 * What the orb says in its middle: the flag the panel put on the name (or the globe when
 * there is none), the region's own name, and the subscription's — a touch smaller.
 */
data class WidgetFace(
    val flag: String?,
    val region: String,
    val subscription: String?,
)

/**
 * One pinned tile: a flag alone in a square riding the orb's top edge. [active] marks the
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
 * geometry nor the type: the connect button sits half inside the orb with its center
 * on the rim, the pin squares ride the top edge the same way, and the flags want the
 * bundled Twemoji font — none of which an ImageView-with-text arrives at. Drawing it
 * all ourselves also keeps the interactive zones honest: the provider frames its
 * invisible zone views to the very pixel boxes these paints draw.
 *
 * Nothing is measured off the widget's box — that box is never quite square, and
 * anything that follows it stretches. Everything is measured off the orb alone
 * ([orbBox]): the circle fills the box's short side, the connect button's diameter
 * is [BUTTON_SHARE] of that side with its center riding the rim toward the box's
 * bottom-right corner (the classic corner-badge fit on a square box), and the pin
 * tiles ride the orb's top-left corner at a small margin, in a row that squeezes
 * itself to fit — squares stay squares at any stretch.
 */
object WidgetRenderer {

    /** How many pin tiles the layout carves room for; the provider may fill fewer. */
    const val PIN_SLOTS = 5

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
        if (width <= 0 || height <= 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        // A fresh bitmap every paint: a repaint is a state change, and caching would let
        // one paint erase the canvas under another's RemoteViews hand-off.
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

        // ---- the middle: flag, region, subscription -----------------------------
        val flagBox = short * 0.30f
        val regionSize = short * 0.10f
        val subSize = short * 0.074f
        val flagLayout = flagLayout(context, face.flag, flagStyle, flagBox * 0.85f)
        val regionLayout = textLayout(
            context, face.region, regionSize, content, bold = true, width = short * 0.62f,
        )
        val subLayout = face.subscription?.takeIf { it.isNotBlank() }?.let {
            textLayout(
                context,
                it,
                subSize,
                ColorUtils.setAlphaComponent(content, 185),
                bold = false,
                width = short * 0.70f,
            )
        }
        val gap = short * 0.025f
        val total = (flagLayout?.height ?: flagBox.toInt()) + gap + regionLayout.height +
            (subLayout?.let { gap * 0.7f + it.height } ?: 0f)
        // A touch below the mathematical middle: the pin row crowds the top edge, so
        // the stack — the flag first of all — settles a little lower and breathes.
        var top = cy - total / 2f + short * 0.015f
        if (flagLayout != null) {
            canvas.save()
            canvas.translate(cx - flagLayout.width / 2f, top)
            flagLayout.draw(canvas)
            canvas.restore()
            top += flagLayout.height + gap
        } else {
            drawVector(context, R.drawable.ic_public, content, canvas, cx, top + flagBox / 2f, flagBox)
            top += flagBox + gap
        }
        canvas.save()
        canvas.translate(cx - regionLayout.width / 2f, top)
        regionLayout.draw(canvas)
        canvas.restore()
        subLayout?.let {
            top += regionLayout.height + gap * 0.7f
            canvas.save()
            canvas.translate(cx - it.width / 2f, top)
            it.draw(canvas)
            canvas.restore()
        }

        // ---- the connect button: size tied to the orb alone, center riding the rim
        //      toward the hitbox's bottom-right corner ------------------------------
        val button = buttonBox(w, h)
        val d = button.width()
        // Connected wears the accent; idle is the quiet inverted tone — the same pair
        // the app's own button keeps, just the other way around.
        val buttonFill = if (up) colors.primary else colors.onPrimary
        val buttonGlyph = if (up) colors.onPrimary else colors.primary
        canvas.drawCircle(button.centerX(), button.centerY(), d / 2f, fillPaint(buttonFill))
        drawVector(
            context, R.drawable.ic_power, buttonGlyph, canvas,
            button.centerX(), button.centerY(), d * 0.46f,
        )

        // ---- the pinned squares --------------------------------------------------
        val count = pins.size.coerceAtMost(PIN_SLOTS)
        pins.take(count).forEachIndexed { index, pin ->
            val tile = pinRect(w, h, count, index) ?: return@forEachIndexed
            val side = tile.width()
            val corner = side * 0.18f
            // No rims anywhere on this widget: the picked pin tells itself apart
            // by its fill alone, the way the connect button's color tells the tunnel.
            val tileFill = if (pin.active) colors.primary else colors.surfaceContainerHighest
            canvas.drawRoundRect(
                RectF(tile.left, tile.top, tile.right, tile.bottom),
                corner,
                corner,
                fillPaint(tileFill),
            )
            val glyphBox = side * 0.70f
            val glyph = flagLayout(context, pin.flag, flagStyle, glyphBox * 0.85f)
            if (glyph != null) {
                canvas.save()
                canvas.translate(
                    tile.left + (tile.width() - glyph.width) / 2f,
                    tile.top + (tile.height() - glyph.height) / 2f,
                )
                glyph.draw(canvas)
                canvas.restore()
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

    /** The button's size against the orb's side — the square widget's corner-badge fit. */
    internal val BUTTON_SHARE = 1f - 1f / sqrt(2f)

    /**
     * The connect button: its diameter is a share of the orb and nothing else — resize
     * the widget and the button scales with the circle, never with the box — and its
     * center rides the rim along the direction of the hitbox's bottom-right corner.
     * On a square box that answers the classic corner-badge fit exactly: center on
     * the rim, box flush with the corner.
     */
    internal fun buttonBox(w: Float, h: Float): FrameF {
        val orb = orbBox(w, h)
        val side = orb.width()
        val d = BUTTON_SHARE * side
        // The unit vector from the orb's middle toward the box's bottom-right corner.
        val vx = w / 2f
        val vy = h / 2f
        val len = max(1f, sqrt(vx * vx + vy * vy))
        val cx = w / 2f + (side / 2f) * (vx / len)
        val cy = h / 2f + (side / 2f) * (vy / len)
        return FrameF(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f)
    }

    /**
     * One pinned slot's square: the row rides the orb box's top-left corner at a small
     * margin, one tile after another with a gap between; a crowded row squeezes. All
     * of it in shares of the orb's side, so the tiles are square whatever the box.
     * Null when the row holds no such slot.
     */
    internal fun pinRect(w: Float, h: Float, count: Int, index: Int): FrameF? {
        if (count !in 1..PIN_SLOTS || index !in 0 until count) return null
        val orb = orbBox(w, h)
        val side = orb.width()
        val tile = PinRow.side(count) * side
        val margin = PinRow.MARGIN * side
        val gap = PinRow.GAP * side
        val left = orb.left + margin + index * (tile + gap)
        val top = orb.top + margin
        return FrameF(left, top, left + tile, top + tile)
    }

    /**
     * The pinned row's own measure, in shares of the orb's side — spelled once so the
     * painter and the touch zones agree on it. The tiles sit a small margin off the
     * corner and grow up to [MAX_SIDE]; a crowded row squeezes them just enough to fit.
     */
    internal object PinRow {
        /** The breathing room between the corner, and between pin and pin. */
        const val MARGIN = 0.04f
        const val GAP = 0.02f

        /** The pin size the row grows to before crowding starts. */
        const val MAX_SIDE = 0.23f

        fun side(count: Int): Float {
            val n = count.coerceIn(1, PIN_SLOTS)
            return minOf(MAX_SIDE, (1f - 2f * MARGIN - (n - 1) * GAP) / n)
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

    // --------------------------------------------------------------- region text

    private fun textLayout(
        context: Context,
        text: String,
        size: Float,
        color: Int,
        bold: Boolean,
        width: Float,
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        paint.textSize = size
        interTypeface(context)?.let { typeface ->
            paint.typeface = typeface
            if (bold) runCatching { paint.fontVariationSettings = "'wght' 650" }
        }
        if (bold && paint.typeface == null) paint.isFakeBoldText = true
        val shown = TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END) as String
        return StaticLayout.Builder
            .obtain(shown, 0, shown.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
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
