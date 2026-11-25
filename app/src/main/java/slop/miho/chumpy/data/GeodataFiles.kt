package slop.miho.chumpy.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import slop.miho.chumpy.R
import slop.miho.chumpy.sub.GeodataSources
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

internal object GeodataFiles {
    const val MAX_BYTES = 64L * 1024 * 1024
    /** Full catalogs are only cached for category browsing and reduced before core startup. */
    const val MAX_SOURCE_BYTES = 512L * 1024 * 1024
    const val CATALOG_GEOSITE_FILE = "catalog-geosite-full.dat"
    const val CATALOG_GEOIP_FILE = "catalog-geoip-full.dat"

    /** The user's imported databases, in the same assets directory as the bundled ones. */
    const val USER_GEOSITE_FILE = "user-geosite.dat"
    const val USER_GEOIP_FILE = "user-geoip.dat"

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private data class Reference(val value: String, val file: String, val category: String)

    fun catalogFile(context: Context, url: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "xray_assets").apply { mkdirs() }, "catalog-$hash.dat")
    }

    private fun references(config: JsonObject): List<Reference> {
        val rules = ((config["routing"] as? JsonObject)?.get("rules") as? JsonArray).orEmpty()
        return rules.filterIsInstance<JsonObject>().flatMap { rule ->
            listOf("domain", "ip", "source").flatMap { key ->
                val value = rule[key]
                val values = if (value is JsonArray) value else listOfNotNull(value)
                values.mapNotNull { item ->
                    val text = (item as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    when {
                        text.startsWith("geosite:") -> Reference(text, "geosite.dat", text.removePrefix("geosite:"))
                        text.startsWith("geoip:") -> Reference(text, "geoip.dat", text.removePrefix("geoip:"))
                        text.startsWith("ext:") -> {
                            val parts = text.split(':', limit = 3)
                            require(parts.size == 3 && GeodataSources.validFilename(parts[1])) { "Invalid geodata reference" }
                            Reference(text, parts[1], parts[2])
                        }
                        else -> null
                    }
                }
            }
        }
    }

    suspend fun prepare(
        context: Context,
        config: String,
        sources: String,
        allowStandardFallback: Boolean = false,
        fetch: suspend (String, File) -> Unit,
        overrides: Map<String, String> = emptyMap(),
        sourceOverrides: Map<String, String> = emptyMap(),
        fetchCatalog: suspend (String, File) -> Unit = fetch,
    ): String = mutex.withLock {
        val root = json.parseToJsonElement(config) as JsonObject
        val refs = references(root)
        val providerUrls = GeodataSources.parse(sources)
        val urls = providerUrls.values.associateBy { GeodataSources.filenameFor(it) } + sourceOverrides
        val directory = File(context.filesDir, "xray_assets").apply { mkdirs() }
        val replacements = mutableMapOf<String, String>()
        for ((name, requested) in refs.groupBy { it.file }) {
            val categories = requested.map { it.category.removePrefix("!").substringBefore('@').uppercase(Locale.ROOT) }.toSet()
            // A database the user imported wins over both the bundled one and any
            // provider URL: the config's references are rewritten onto its own file.
            val customName = overrides[name]
            if (customName != null && GeodataSources.validFilename(customName)) {
                val custom = File(directory, customName)
                if (!custom.isFile || custom.length() !in 1..MAX_SOURCE_BYTES) throw IOException("Custom geodata file is missing or too large")
                val identity = "$customName:${custom.length()}:${custom.lastModified()}"
                replacements[name] = selectedGeodata(custom, identity, categories, directory).name
                continue
            }
            var url = urls[name]
            val customUrl = sourceOverrides[name]
            if (customUrl != null) {
                val sourceHash = MessageDigest.getInstance("SHA-256")
                    .digest(customUrl.toByteArray()).joinToString("") { "%02x".format(it) }
                val catalog = catalogFile(context, customUrl)
                val catalogFresh = catalog.isFile && catalog.length() in 1..MAX_SOURCE_BYTES &&
                    System.currentTimeMillis() - catalog.lastModified() in 0..86_400_000L
                if (!catalogFresh) {
                    val temporary = File.createTempFile("catalog-", ".part", directory)
                    try {
                        fetchCatalog(customUrl, temporary)
                        temporary.inputStream().use { GeodataFiles.categories(it, MAX_SOURCE_BYTES) }
                        java.nio.file.Files.move(temporary.toPath(), catalog.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    } finally {
                        temporary.delete()
                    }
                }
                replacements[name] = selectedGeodata(catalog, "$sourceHash:${catalog.length()}:${catalog.lastModified()}", categories, directory).name
                continue
            }
            if (url == null) {
                val available = try {
                    if (name == "geosite.dat" || name == "geoip.dat") context.assets.open(name).use(::categories)
                    else File(directory, name).inputStream().use(::categories)
                } catch (_: IOException) { emptySet() }
                val missing = categories - available
                if (missing.isEmpty()) continue
                if (allowStandardFallback && name == "geosite.dat") {
                    url = "https://github.com/XTLS/Xray-rules-dat/releases/latest/download/geosite.dat.gz"
                } else throw IOException(context.getString(R.string.error_geodata_source_missing, name, missing.sorted().joinToString(", ")))
            }
            val pointer = File(directory, "${GeodataSources.filenameFor(url)}.cache")
            val cachedName = runCatching { pointer.readText().takeIf { it.length <= 180 && GeodataSources.validFilename(it) } }.getOrNull()
            val cached = cachedName?.let { File(directory, it) }
            val cachedValid = cached?.takeIf { file ->
                file.isFile && file.length() in 1..MAX_BYTES &&
                    System.currentTimeMillis() - file.lastModified() in 0..86_400_000 &&
                    runCatching { file.inputStream().use(::categories).containsAll(categories) }.getOrDefault(false)
            }
            val selected = cachedValid ?: run {
                val temporary = File.createTempFile("geodata-", ".part", directory)
                try {
                    fetch(url, temporary)
                    val present = temporary.inputStream().use(::categories)
                    if (!present.containsAll(categories)) throw IOException(context.getString(R.string.error_geodata_categories, categories.minus(present).sorted().joinToString(", ")))
                    val digest = MessageDigest.getInstance("SHA-256")
                    temporary.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                    }
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    val target = File(directory, "provider-$hash.dat")
                    if (target.exists()) { temporary.delete(); target.setLastModified(System.currentTimeMillis()) }
                    else if (!temporary.renameTo(target)) throw IOException("Could not store geodata")
                    AtomicSnapshot.write(pointer, target.name)
                    target
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (cached != null && cached.isFile && runCatching { cached.inputStream().use(::categories).containsAll(categories) }.getOrDefault(false)) cached
                    else throw IOException(context.getString(R.string.error_geodata_download, e.message ?: "unknown"), e)
                } finally { temporary.delete() }
            }
            replacements[name] = selected.name
        }
        fun replace(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.mapValues { replace(it.value) })
            is JsonArray -> JsonArray(value.map(::replace))
            is JsonPrimitive -> {
                val text = value.contentOrNull.orEmpty()
                val parts = if (value.isString && text.startsWith("ext:")) text.split(':', limit = 3) else emptyList()
                when {
                    parts.size == 3 && replacements.containsKey(parts[1]) -> JsonPrimitive("ext:${replacements.getValue(parts[1])}:${parts[2]}")
                    value.isString && text.startsWith("geosite:") && replacements.containsKey("geosite.dat") -> JsonPrimitive("ext:${replacements.getValue("geosite.dat")}:${text.removePrefix("geosite:")}")
                    value.isString && text.startsWith("geoip:") && replacements.containsKey("geoip.dat") -> JsonPrimitive("ext:${replacements.getValue("geoip.dat")}:${text.removePrefix("geoip:")}")
                    else -> value
                }
            }
        }
        val routing = root["routing"] as? JsonObject
        val prepared = if (routing == null) root.toString() else JsonObject(root + ("routing" to JsonObject(routing.mapValues { (key, value) ->
            if (key != "rules" || value !is JsonArray) value else JsonArray(value.map { rule ->
                if (rule !is JsonObject) rule else JsonObject(rule.mapValues { (field, item) ->
                    if (field in setOf("domain", "ip", "source")) replace(item) else item
                })
            })
        }))).toString()
        val protected = references(json.parseToJsonElement(prepared) as JsonObject).map { it.file }.toSet() +
            runCatching { references(json.parseToJsonElement(slop.miho.chumpy.core.CoreConfigFile.readConfig(context).orEmpty()) as JsonObject).map { it.file } }.getOrDefault(emptyList())
        val cutoff = System.currentTimeMillis() - 7 * 86_400_000L
        directory.listFiles()?.filter { file ->
            (file.name.startsWith("provider-") || file.name.startsWith("selected-")) &&
                file.extension == "dat" && file.name !in protected && file.lastModified() < cutoff
        }?.forEach { it.delete() }
        prepared
    }

    fun categories(source: InputStream, maxBytes: Long = MAX_BYTES): Set<String> {
        val input = source.buffered()
        val result = mutableSetOf<String>()
        var total = 0L
        var entries = 0
        while (true) {
            val tag = varint(input, eof = true) ?: break
            require(++entries <= 100_000) { "Too many geodata categories" }
            require(tag == 10L) { "Invalid geodata container" }
            val length = varint(input)!!
            require(length in 1..maxBytes && total + length <= maxBytes) { "Geodata size limit" }
            total += length
            val entry = LimitedInput(input, length)
            var name: String? = null
            while (entry.remaining > 0) {
                val field = varint(entry)!!
                require(field shr 3 > 0) { "Invalid geodata field" }
                when ((field and 7).toInt()) {
                    0 -> varint(entry)
                    1 -> skip(entry, 8)
                    2 -> {
                        val size = varint(entry)!!
                        require(size <= entry.remaining) { "Truncated geodata" }
                        if (field shr 3 == 1L) {
                            require(size in 1..256) { "Invalid category" }
                            val bytes = ByteArray(size.toInt())
                            for (index in bytes.indices) bytes[index] = entry.read().also { require(it >= 0) }.toByte()
                            name = bytes.toString(Charsets.UTF_8).uppercase(Locale.ROOT)
                        } else skip(entry, size)
                    }
                    5 -> skip(entry, 4)
                    else -> throw IOException("Invalid geodata wire type")
                }
            }
            require(!name.isNullOrBlank()) { "Missing geodata category" }
            result.add(name)
        }
        require(result.isNotEmpty()) { "Empty geodata" }
        return result
    }

    private fun selectedGeodata(
        catalog: File,
        identity: String,
        requested: Collection<String>,
        directory: File,
    ): File {
        val selectedCategories = requested.map {
            it.removePrefix("!").substringBefore('@').uppercase(Locale.ROOT)
        }.toSet()
        require(selectedCategories.isNotEmpty()) { "No geodata categories selected" }
        val identityHash = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        val selectedHash = MessageDigest.getInstance("SHA-256")
            .digest((identityHash + selectedCategories.sorted().joinToString(",")).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val selectedFile = File(directory, "selected-$selectedHash.dat")
        val cached = selectedFile.isFile && selectedFile.length() in 1..MAX_BYTES &&
            runCatching { selectedFile.inputStream().use { categories(it).containsAll(selectedCategories) } }.getOrDefault(false)
        if (cached) {
            selectedFile.setLastModified(System.currentTimeMillis())
            return selectedFile
        }

        val temporary = File.createTempFile("selected-", ".part", directory)
        try {
            val present = catalog.inputStream().use { input ->
                temporary.outputStream().use { output -> filterCategories(input, output, selectedCategories) }
            }
            val missing = selectedCategories - present
            if (missing.isNotEmpty()) throw IOException("Geodata categories missing: ${missing.sorted().joinToString(", ")}")
            if (temporary.length() > MAX_BYTES) throw IOException("Selected geodata exceeds 64 MiB")
            selectedFile.delete()
            if (!temporary.renameTo(selectedFile)) throw IOException("Could not store selected geodata")
        } finally {
            temporary.delete()
        }
        return selectedFile
    }

    /** Copies only selected GeoSite/GeoIP records, retaining the Xray protobuf envelope. */
    fun filterCategories(source: InputStream, target: java.io.OutputStream, selected: Set<String>): Set<String> {
        val input = source.buffered()
        val present = mutableSetOf<String>()
        var total = 0L
        var entries = 0
        var outputBytes = 0L
        while (true) {
            val tag = varint(input, eof = true) ?: break
            require(tag == 10L) { "Invalid geodata container" }
            val length = varint(input) ?: throw IOException("Truncated geodata")
            require(length in 1..MAX_BYTES && total + length <= MAX_SOURCE_BYTES) { "Geodata record or database size limit" }
            total += length
            require(++entries <= 1_000_000) { "Too many geodata categories" }
            val payload = ByteArray(length.toInt())
            var offset = 0
            while (offset < payload.size) {
                val count = input.read(payload, offset, payload.size - offset)
                if (count < 0) throw IOException("Truncated geodata")
                offset += count
            }
            val name = categoryName(payload)
            if (name in selected) {
                outputBytes += 1L + varintSize(length).toLong() + length
                require(outputBytes <= MAX_BYTES) { "Selected geodata exceeds 64 MiB" }
                writeVarint(target, 10L)
                writeVarint(target, length)
                target.write(payload)
                present += name
            }
        }
        target.flush()
        return present
    }

    private fun categoryName(payload: ByteArray): String {
        val entry = java.io.ByteArrayInputStream(payload)
        var name: String? = null
        while (entry.available() > 0) {
            val field = varint(entry) ?: throw IOException("Truncated geodata category")
            require(field shr 3 > 0) { "Invalid geodata field" }
            when ((field and 7).toInt()) {
                0 -> varint(entry)
                1 -> skip(entry, 8)
                2 -> {
                    val size = varint(entry) ?: throw IOException("Truncated geodata category")
                    require(size <= entry.available().toLong()) { "Truncated geodata category" }
                    if (field shr 3 == 1L) {
                        require(size in 1..256) { "Invalid category" }
                        val bytes = ByteArray(size.toInt())
                        require(entry.read(bytes) == bytes.size) { "Truncated geodata category" }
                        name = bytes.toString(Charsets.UTF_8).uppercase(Locale.ROOT)
                    } else skip(entry, size)
                }
                5 -> skip(entry, 4)
                else -> throw IOException("Invalid geodata wire type")
            }
        }
        return name?.takeIf(String::isNotBlank) ?: throw IOException("Missing geodata category")
    }

    private fun writeVarint(output: java.io.OutputStream, input: Long) {
        require(input >= 0)
        var value = input
        while (value >= 0x80) {
            output.write(((value and 0x7f) or 0x80).toInt())
            value = value shr 7
        }
        output.write(value.toInt())
    }

    private fun varintSize(input: Long): Int {
        var value = input
        var size = 1
        while (value >= 0x80) { value = value shr 7; size++ }
        return size
    }

    private fun varint(input: InputStream, eof: Boolean = false): Long? {
        var value = 0L
        for (shift in 0..63 step 7) {
            val next = input.read()
            if (next < 0) { if (shift == 0 && eof) return null; throw IOException("Truncated geodata") }
            if (shift == 63 && next > 1) throw IOException("Invalid varint")
            value = value or ((next and 127).toLong() shl shift)
            if (next and 128 == 0) return value.also { require(it >= 0) }
        }
        throw IOException("Invalid varint")
    }

    private fun skip(input: InputStream, length: Long) {
        require(length >= 0)
        var remaining = length
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) remaining -= skipped
            else { if (input.read() < 0) throw IOException("Truncated geodata"); remaining-- }
        }
    }

    private class LimitedInput(private val source: InputStream, var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining == 0L) return -1
            return source.read().also { if (it < 0) throw IOException("Truncated geodata"); remaining-- }
        }
        override fun skip(n: Long): Long = source.skip(minOf(n, remaining)).also { remaining -= it }
    }
}
