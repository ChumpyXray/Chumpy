package slop.miho.chumpy.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], application = android.app.Application::class)
class GeodataFilesTest {
    @Test
    fun `provider databases do not replace bundled private network rules`() = kotlinx.coroutines.runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val config = """{"routing":{"rules":[{"ip":["geoip:private"],"outboundTag":"direct"}]}}"""
        val sources = """{"geoip.dat":"https://provider.example/geoip.dat"}"""
        val result = GeodataFiles.prepare(context, config, sources, fetch = { _, _ ->
            error("Built-in private networks must not be requested from the provider")
        })
        org.junit.Assert.assertTrue(result.contains("geoip:private"))
    }

    @Test
    fun `geodata filtering keeps only selected category records`() {
        val source = ByteArrayOutputStream().apply {
            writeRecord("CATEGORY-ADS")
            writeRecord("CATEGORY-RU")
            writeRecord("PRIVATE")
        }.toByteArray()
        val output = ByteArrayOutputStream()
        val present = GeodataFiles.filterCategories(
            ByteArrayInputStream(source),
            output,
            setOf("CATEGORY-RU", "PRIVATE"),
        )

        assertEquals(setOf("CATEGORY-RU", "PRIVATE"), present)
        assertEquals(setOf("CATEGORY-RU", "PRIVATE"), GeodataFiles.categories(ByteArrayInputStream(output.toByteArray())))
        assertEquals(2, GeodataFiles.categories(ByteArrayInputStream(output.toByteArray())).size)
    }

    private fun ByteArrayOutputStream.writeRecord(name: String) {
        val bytes = name.toByteArray(Charsets.UTF_8)
        val record = ByteArrayOutputStream().apply {
            write(10)
            writeVarint(bytes.size.toLong())
            write(bytes)
        }.toByteArray()
        write(10)
        writeVarint(record.size.toLong())
        write(record)
    }

    private fun ByteArrayOutputStream.writeVarint(input: Long) {
        var value = input
        while (value >= 0x80) {
            write(((value and 0x7f) or 0x80).toInt())
            value = value shr 7
        }
        write(value.toInt())
    }
}
