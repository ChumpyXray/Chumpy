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
 * pin the running (or selected) server is, so the rim tells the picked one from the rest.
 */
data class WidgetPin(
    val id: String,
    val flag: String?,
    val label: String,
    val active: Boolean,
)

/**
 * The whole launcher widget, drawn into one bitmap. RemoteViews can do neither the
 * geometry nor the type: the connect button sits half inside the orb with its center on
 * the rim and its box flush with the hitbox's corner, the pin squares ride the top edge
 * the same way, and the flags want the bundled Twemoji font — none of which an
 * ImageView-with-text arrives at. Drawing it all ourselves also keeps the interactive
 * zones honest: the provider overlays invisible views whose layout weights carve the
 * widget into the same proportions these paints draw, so the two never drift apart.
 *
 * The proportions, spelled once so the painter and the touch zones agree: the orb fills
 * the box's short side exactly; the connect button's diameter is the distance that puts
 * its center on the orb's rim while its bounding box stays flush with the hitbox's
 * bottom-right corner (W = H: d = W·(1 − 1/√2), the classic corner-badge fit); the pin
 * tiles are squares of a fifth of the short side, flush with the top and, in a row from
 * the left, straddling the orb's edge exactly the way the button does.
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
        val busy = state == TunnelState.CONNECTING || state == TunnelState.DISCONNECTING
        val fill = if (up) colors.primary else colors.secondaryContainer
        val content = if (up) colors.onPrimary else colors.onSecondaryContainer
        val flagStyle = SettingsStore.settings(context).value.flagStyle

        // ---- the orb ------------------------------------------------------------
        canvas.drawCircle(cx, cy, radius, fillPaint(fill))
        val rimWidth = max(2f, short * 0.014f)
        val rim = fillPaint(color = ColorUtils.setAlphaComponent(content, if (up) 60 else 40))
        rim.style = Paint.Style.STROKE
        rim.strokeWidth = rimWidth
        if (busy) {
            // The traveling wave cannot travel in a still bitmap; a ring with a gap is
            // what it settles into — the busy state's own mark on the rim.
            rim.color = colors.primary
            val box = RectF(
                cx - radius + rimWidth,
                cy - radius + rimWidth,
                cx + radius - rimWidth,
                cy + radius - rimWidth,
            )
            canvas.drawArc(
                box,
                if (state == TunnelState.CONNECTING) 250f else 130f,
                300f,
                false,
                rim,
            )
        } else {
            canvas.drawCircle(cx, cy, radius - rimWidth / 2f, rim)
        }

        // ---- the middle: flag, region, subscription -----------------------------
        val flagBox = short * 0.30f
        val regionSize = short * 0.11f
        val subSize = short * 0.082f
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
        var top = cy - total / 2f
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

        // ---- the connect button: box flush with the hitbox's bottom-right corner,
        //      its center landing on the orb's rim --------------------------------
        val d = buttonDiameter(w, h, short)
        val bx = w - d / 2f
        val by = h - d / 2f
        val buttonFill = if (up) colors.onPrimary else colors.primary
        val buttonGlyph = if (up) colors.primary else colors.onPrimary
        canvas.drawCircle(bx, by, d / 2f, fillPaint(buttonFill))
        drawVector(context, R.drawable.ic_power, buttonGlyph, canvas, bx, by, d * 0.46f)

        // ---- the pinned squares: a fifth of the short side each, flush with the top -
        val side = short / PIN_SLOTS
        pins.take(PIN_SLOTS).forEachIndexed { index, pin ->
            val left = index * side
            val tileBox = RectF(left, 0f, left + side, side)
            val corner = side * 0.16f
            canvas.drawRoundRect(tileBox, corner, corner, fillPaint(colors.surfaceContainerHighest))
            val edge = fillPaint(
                if (pin.active) colors.primary else ColorUtils.setAlphaComponent(colors.outline, 115),
            )
            edge.style = Paint.Style.STROKE
            edge.strokeWidth = if (pin.active) max(2f, side * 0.055f) else max(1.25f, side * 0.035f)
            val inset = edge.strokeWidth / 2f
            canvas.drawRoundRect(
                RectF(tileBox).apply { inset(inset, inset) },
                corner,
                corner,
                edge,
            )
            val glyphBox = side * 0.68f
            val glyph = flagLayout(context, pin.flag, flagStyle, glyphBox * 0.85f)
            if (glyph != null) {
                canvas.save()
                canvas.translate(left + (side - glyph.width) / 2f, (side - glyph.height) / 2f)
                glyph.draw(canvas)
                canvas.restore()
            } else {
                drawVector(
                    context,
                    R.drawable.ic_public,
                    colors.onSurfaceVariant,
                    canvas,
                    left + side / 2f,
                    side / 2f,
                    glyphBox,
                )
            }
        }
    }

    /**
     * The corner-badge fit: d is chosen so the button's center sits exactly on the orb's
     * rim while its bounding box stays flush with the hitbox's bottom-right corner —
     * √(((W−d)/2)² + ((H−d)/2)²) = min(W,H)/2. The square widget answers with the clean
     * d = W·(1 − 1/√2) ≈ 0.29·W; the quadratic covers the stretched cases.
     */
    internal fun buttonDiameter(w: Float, h: Float, short: Float): Float {
        val sum = w + h
        val discriminant = sum * sum - 2f * (w * w + h * h - short * short)
        if (discriminant <= 0f) return short * 0.29f
        return (sum - sqrt(discriminant)) / 2f
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
