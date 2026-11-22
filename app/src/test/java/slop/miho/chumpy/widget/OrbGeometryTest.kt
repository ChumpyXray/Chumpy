package slop.miho.chumpy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The orb's own laws, spelled in the renderer's geometry helpers and asserted here:
 * nothing is measured off the widget's box — that box is never quite square — but off
 * the orb alone, so circles stay circles and squares stay squares at any stretch the
 * widget is resized to.
 */
class OrbGeometryTest {

    @Test
    fun `the button keeps its size and corner through any stretch`() {
        for ((w, h) in listOf(300f to 300f, 250f to 170f, 150f to 200f)) {
            val orb = WidgetRenderer.orbBox(w, h)
            val box = WidgetRenderer.buttonBox(w, h)
            val side = minOf(w, h)
            // The side is a share of the orb alone — resize the box and the button
            // scales with the circle, never with the stretch.
            assertEquals(WidgetRenderer.BUTTON_SHARE * side, box.width(), 0.01f)
            assertEquals(WidgetRenderer.BUTTON_SHARE * side, box.height(), 0.01f)
            // The box stays flush in the orb square's bottom-right corner, and its
            // center stays on the same diagonal — a stretch never slides it around.
            assertEquals(orb.right, box.right, 0.01f)
            assertEquals(orb.bottom, box.bottom, 0.01f)
            val dx = box.centerX() - orb.centerX()
            val dy = box.centerY() - orb.centerY()
            assertEquals(dx, dy, 0.01f)
        }
    }

    @Test
    fun `the pin boxes stay square whatever the widget is stretched to`() {
        for ((w, h) in listOf(300f to 300f, 250f to 170f, 150f to 200f)) {
            val orb = WidgetRenderer.orbBox(w, h)
            for (count in 1..WidgetRenderer.PIN_SLOTS) {
                for (slot in 0 until count) {
                    val rect = WidgetRenderer.pinRect(w, h, count, slot)!!
                    // A square in pixels, both axes — never a rectangle of the stretch.
                    assertEquals(rect.width(), rect.height(), 0.01f)
                    // The row rides the orb's own top-left corner, not the box's.
                    assertTrue(rect.left >= orb.left - 0.01f)
                    assertTrue(rect.top >= orb.top - 0.01f)
                    assertTrue(rect.right <= orb.right + 0.01f)
                }
                // The row starts flush with the corner — no margin off it.
                val first = WidgetRenderer.pinRect(w, h, count, 0)!!
                assertEquals(orb.left, first.left, 0.01f)
                assertEquals(orb.top, first.top, 0.01f)
            }
        }
    }

    @Test
    fun `three pin slots keep their full size and fit the orb`() {
        assertEquals(3, WidgetRenderer.PIN_SLOTS)
        val side = { n: Int -> WidgetRenderer.PinRow.side(n) }
        // Three slots are all the row holds, and every one of them keeps the full size.
        for (n in 1..WidgetRenderer.PIN_SLOTS) {
            assertEquals(0.26f, side(n), 0.0001f)
            val right = n * side(n) + (n - 1) * WidgetRenderer.PinRow.GAP
            assertTrue("row of $n runs past the edge", right <= 1.000001f)
        }
    }
}
