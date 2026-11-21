package slop.miho.chumpy.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class GeodataFilesTest {
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
