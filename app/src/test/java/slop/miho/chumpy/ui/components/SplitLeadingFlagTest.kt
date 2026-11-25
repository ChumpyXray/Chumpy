package slop.miho.chumpy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The leading-flag split: panels prefix profile names with their country's flag, and the
 * list moves it into the icon slot, out of the title.
 */
class SplitLeadingFlagTest {
    @Test
    fun `flag with title`() {
        val (flag, title) = splitLeadingFlag("🇩🇪 Германия · DE 1")
        assertEquals("🇩🇪", flag)
        assertEquals("Германия · DE 1", title)
    }

    @Test
    fun `two leading flags travel together`() {
        val (flag, title) = splitLeadingFlag("🇺🇸🇬🇧 US-UK mixed")
        assertEquals("🇺🇸🇬🇧", flag)
        assertEquals("US-UK mixed", title)
    }

    @Test
    fun `no flag comes back whole`() {
        val (flag, title) = splitLeadingFlag("Frankfurt 1")
        assertNull(flag)
        assertEquals("Frankfurt 1", title)
    }

    @Test
    fun `leading whitespace before the flag is skipped`() {
        val (flag, title) = splitLeadingFlag("  🇫🇮 Helsinki")
        assertEquals("🇫🇮", flag)
        assertEquals("Helsinki", title)
    }

    @Test
    fun `non-flag emoji is not a flag`() {
        // Lightning bolts and lock emoji are not regional indicators: the name stays whole.
        val (flag, title) = splitLeadingFlag("⚡ Fast exit")
        assertNull(flag)
        assertEquals("⚡ Fast exit", title)
    }


    @Test
    fun `rainbow flag splits off`() {
        val (pride, title) = splitLeadingPrideFlag("🏳️‍🌈 Pride node")
        assertEquals(PrideFlag.RAINBOW, pride)
        assertEquals("Pride node", title)
    }

    @Test
    fun `trans flag splits off`() {
        val (pride, title) = splitLeadingPrideFlag("🏳️‍⚧️ Trans node")
        assertEquals(PrideFlag.TRANS, pride)
        assertEquals("Trans node", title)
    }

    @Test
    fun `pride before a country flag leaves the country one behind`() {
        val (pride, rest) = splitLeadingPrideFlag("🏳️‍🌈🇩🇪 Berlin")
        assertEquals(PrideFlag.RAINBOW, pride)
        assertEquals("🇩🇪 Berlin", rest)
    }

    @Test
    fun `plain name has no pride flag`() {
        val (pride, title) = splitLeadingPrideFlag("🇩🇪 Германия")
        assertNull(pride)
        assertEquals("🇩🇪 Германия", title)
    }
}


/**
 * The leading-emoji split: with the sticker setting on, the emoji panels pack in front
 * of a name (past its flag) come off the title and ride the region icon as stickers.
 */
class SplitLeadingEmojiTest {
    @Test
    fun `leading emoji become stickers`() {
        val (stickers, title) = splitLeadingEmoji("⚡ Fast exit")
        assertEquals(listOf("⚡"), stickers)
        assertEquals("Fast exit", title)
    }

    @Test
    fun `a run of emoji splits whole clusters`() {
        val (stickers, title) = splitLeadingEmoji("🔥🚀 Premium line")
        assertEquals(listOf("🔥", "🚀"), stickers)
        assertEquals("Premium line", title)
    }

    @Test
    fun `variation selector and zwj stay with their cluster`() {
        val (stickers, title) = splitLeadingEmoji("❤️🏳️‍🌈 Party")
        assertEquals(listOf("❤️", "🏳️‍🌈"), stickers)
        assertEquals("Party", title)
    }

    @Test
    fun `emoji in the middle and at the end stay in the title`() {
        val (stickers, title) = splitLeadingEmoji("Frankfurt ⚡ fast ⚡")
        assertEquals(emptyList<String>(), stickers)
        assertEquals("Frankfurt ⚡ fast ⚡", title)
    }

    @Test
    fun `no emoji comes back whole`() {
        val (stickers, title) = splitLeadingEmoji("Frankfurt 1")
        assertEquals(emptyList<String>(), stickers)
        assertEquals("Frankfurt 1", title)
    }

    @Test
    fun `a leading pride flag is not a sticker`() {
        val (stickers, title) = splitLeadingEmoji("🏳️‍🌈 Pride node")
        assertEquals(emptyList<String>(), stickers)
        assertEquals("🏳️‍🌈 Pride node", title)
    }

    @Test
    fun `a leading country flag is not a sticker`() {
        // The flag split runs first; a stray flag reaching the emoji split stays put.
        val (stickers, title) = splitLeadingEmoji("🇩🇪 Германия")
        assertEquals(emptyList<String>(), stickers)
        assertEquals("🇩🇪 Германия", title)
    }

    @Test
    fun `whitespace between leading emoji is consumed`() {
        val (stickers, title) = splitLeadingEmoji("🔥 🚀 Premium")
        assertEquals(listOf("🔥", "🚀"), stickers)
        assertEquals("Premium", title)
    }

    @Test
    fun `a name of only emoji keeps an empty title`() {
        val (stickers, title) = splitLeadingEmoji("🔥🚀")
        assertEquals(listOf("🔥", "🚀"), stickers)
        assertEquals("", title)
    }
}
