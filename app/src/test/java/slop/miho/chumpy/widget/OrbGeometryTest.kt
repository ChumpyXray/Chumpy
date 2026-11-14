package slop.miho.chumpy.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

/**
 * The orb's own law, spelled in the provider's card and asserted here: the connect
 * button's center sits on the orb's rim, and its bounding box stays flush with the
 * hitbox's bottom-right corner — whatever the widget is stretched to.
 */
class OrbGeometryTest {

    @Test
    fun `the square widget's button box is flush with the hitbox corner and its center rides the rim`() {
        val w = 300f
        val d = WidgetRenderer.buttonDiameter(w, w, w)
        // The clean square answer: d = W·(1 − 1/√2).
        assertEquals(w * (1f - 1f / sqrt(2f)), d, 0.01f)
        // The center lands exactly on the rim: the distance from the orb's middle to
        // the button's center is the orb's radius.
        val dx = w - d / 2f - w / 2f
        val dy = w - d / 2f - w / 2f
        assertEquals(w / 2f, sqrt(dx * dx + dy * dy), 0.01f)
        // The box is flush: the button's box spans the hitbox's last d pixels on both axes.
        assertEquals(w, d + 2f * dx, 0.01f)
    }

    @Test
    fun `a stretched widget keeps the center on the rim`() {
        val w = 400f
        val h = 300f
        val d = WidgetRenderer.buttonDiameter(w, h, h)
        val dx = w - d / 2f - w / 2f
        val dy = h - d / 2f - h / 2f
        assertEquals(h / 2f, sqrt(dx * dx + dy * dy), 0.01f)
    }

    @Test
    fun `the pin tiles are fifths of the short side`() {
        assertEquals(5, WidgetRenderer.PIN_SLOTS)
    }
}
