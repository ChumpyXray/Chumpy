package slop.miho.chumpy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The orb's own laws, spelled in the renderer's geometry helpers and asserted here:
 * nothing is measured off the widget's box — that box is never quite square — but off
 * the orb alone, so circles stay circles and squares stay squares at any stretch the
 * widget is resized to.
 */
class OrbGeometryTest {

    @Test
    fun `the button keeps its size against the orb and rides the rim`() {
        for ((w, h) in listOf(300f to 300f, 250f to 170f, 150f to 200f)) {
            val box = WidgetRenderer.buttonBox(w, h)
            val side = minOf(w, h)
            // The diameter is a share of the orb alone — resize the box and the button
            // scales with the circle, never with the stretch.
            assertEquals(WidgetRenderer.BUTTON_SHARE * side, box.width(), 0.01f)
            assertEquals(WidgetRenderer.BUTTON_SHARE * side, box.height(), 0.01f)
            // The center rides the rim: its distance from the orb's middle is the radius.
            val dx = box.centerX() - w / 2f
            val dy = box.centerY() - h / 2f
            assertEquals(side / 2f, hypot(dx, dy), 0.01f)
        }
    }

    @Test
    fun `a square widget keeps the classic corner-badge fit`() {
        val w = 300f
        val box = WidgetRenderer.buttonBox(w, w)
        // The clean square answer: d = W·(1 − 1/√2), the center on the rim and the
        // box flush with the hitbox's bottom-right corner.
        assertEquals(w * (1f - 1f / sqrt(2f)), box.width(), 0.01f)
        assertEquals(w, box.right + 0f, 0.01f)
        assertEquals(w, box.bottom + 0f, 0.01f)
    }

    @Test
    fun `the pins stay square whatever the widget is stretched to`() {
        for ((w, h) in listOf(300f to 300f, 250f to 170f, 150f to 200f)) {
            val orb = WidgetRenderer.orbBox(w, h)
            for (count in 1..5) {
                for (slot in 0 until count) {
                    val rect = WidgetRenderer.pinRect(w, h, count, slot)!!
                    // A square in pixels, both axes — never a rectangle of the stretch.
                    assertEquals(rect.width(), rect.height(), 0.01f)
                    // The row rides the orb's own top-left corner, not the box's.
                    assertTrue(rect.left >= orb.left - 0.01f)
                    assertTrue(rect.top >= orb.top - 0.01f)
                    assertTrue(rect.right <= orb.right + 0.01f)
                }
            }
        }
    }

    @Test
    fun `the pin row grows to its cap and squeezes just enough to fit`() {
        assertEquals(5, WidgetRenderer.PIN_SLOTS)
        val side = { n: Int -> WidgetRenderer.PinRow.side(n) }
        // Up to three pins the tiles keep their full size, a little bigger than the
        // old fifth-of-the-widget; past that the row squeezes to stay inside.
        assertEquals(0.23f, side(1), 0.0001f)
        assertEquals(0.23f, side(2), 0.0001f)
        assertEquals(0.23f, side(3), 0.0001f)
        assertEquals(0.215f, side(4), 0.0001f)
        assertEquals(0.168f, side(5), 0.0001f)
        // Whatever the count, the row fits: margin, tiles, gaps, and the far margin
        // never run past the orb's side.
        for (n in 1..5) {
            val right = 2 * WidgetRenderer.PinRow.MARGIN +
                n * side(n) +
                (n - 1) * WidgetRenderer.PinRow.GAP
            assertTrue("row of $n runs past the edge", right <= 1.000001f)
        }
    }
}
