package slop.miho.chumpy.sub

import java.net.URI
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import slop.miho.chumpy.db.ProfileEntity
import slop.miho.chumpy.db.ProfileType

internal object NaiveProxyImport {
    fun parse(config: JsonObject): ProfileEntity? {
        val listen = (config["listen"] as? JsonPrimitive)?.contentOrNull ?: return null
        if (!listen.startsWith("socks://", ignoreCase = true)) return null
        val endpoint = (config["proxy"] as? JsonPrimitive)?.contentOrNull ?: return null
        val uri = runCatching { URI(endpoint) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "quic") return null
        if (uri.rawQuery != null || uri.rawFragment != null || uri.rawPath !in listOf("", "/")) return null
        val server = uri.host?.removeSurrounding("[", "]") ?: return null
        val port = if (uri.port == -1) 443 else uri.port
        if (port !in 1..65535) return null
        val credentials = uri.rawUserInfo?.split(':', limit = 2) ?: return null
        if (credentials.size != 2 || credentials.any(String::isEmpty)) return null
        return ProfileEntity(
            id = "",
            subscriptionId = null,
            type = ProfileType.NAIVE,
            remarks = (config["remarks"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
            server = server,
            serverPort = port,
            username = LinkUtils.decodeURIComponent(credentials[0]),
            password = LinkUtils.decodeURIComponent(credentials[1]),
            network = if (scheme == "quic") "quic" else "tcp",
            security = "tls",
        )
    }
}
