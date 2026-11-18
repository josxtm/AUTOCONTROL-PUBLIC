package com.josxtm.autocontrol.community.sniffer

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import java.io.Reader
import java.io.Writer
import java.security.MessageDigest
import org.json.JSONObject

/** Same private preferences as comp. 255; independent of catalog replacement and APK code. */
internal class AddressProfileStore(context: Context, name: String = "sniffer-private-address-profiles") {
    private val preferences = context.getSharedPreferences(name, 0)
    data class Installed(val text: String, val profiles: LocalAddressProfiles, val digest: String, val installedAt: Long)
    companion object {
        private val lock = Any()
        private const val CT_PREFIX = "ct-v1:"
        private const val FORMAT = "autocontrol-private-ip"
        fun digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        fun source(profiles: LocalAddressProfiles, record: Map<String, String?>, lan: String?): String {
            val aliases = (profiles.transformerFields + profiles.remoteFields).map(LocalAddressProfiles::key).toSet()
            val relevant = record.filterKeys { LocalAddressProfiles.key(it) in aliases }.toSortedMap()
            return digest(JSONObject().put("base", lan?.trim().orEmpty()).put("inventory", JSONObject(relevant)).toString())
        }
        private fun ctKey(ct: String): String { require(ct.isNotBlank() && ct.length <= 256); return CT_PREFIX + digest(ct) }
        private fun validateConfirmation(text: String, profileDigest: String) {
            require(text.length <= 16000)
            val value = JSONObject(text)
            require(value.getInt("schema") == 1 && value.getString("profile") == profileDigest)
            require(value.getString("source").matches(Regex("[a-f0-9]{64}")))
            listOf("transformers", "remotes").forEach { key ->
                val entry = value.getString(key)
                require(entry.isEmpty() || (entry.toIntOrNull()?.let { it in (if (key == "transformers") 1 else 0)..100 } == true))
            }
            val base = value.getString("base")
            require(base.length <= 18)
            AddressPlan.number(base.substringBefore('/'))
            val presence = value.getJSONObject("presence")
            require(presence.length() <= 100)
            presence.keys().forEach { key -> require(key.matches(Regex("[A-Za-z0-9_-]{1,40}"))); AddressPlan.Presence.valueOf(presence.getString(key)) }
            val web = value.getJSONObject("web")
            require(web.length() <= 100)
            web.keys().forEach { key ->
                require(key.matches(Regex("[A-Za-z0-9_-]{1,40}")))
                val spec = web.getJSONObject(key)
                require(spec.getString("scheme") in listOf("http", "https") && spec.getInt("port") in 1..65535)
            }
        }
    }
    fun installed(): Installed? = synchronized(lock) {
        val text = preferences.getString("profiles", null) ?: return@synchronized null
        Installed(text, LocalAddressProfiles.decode(text), digest(text), preferences.getLong("installed_at", 0))
    }
    /** Validate before editing; commit completes in the caller's IO worker. Legacy profiles remain usable. */
    fun install(text: String): Installed = synchronized(lock) {
        val parsed = LocalAddressProfiles.decode(text)
        val old = preferences.getString("profiles", null)
        val edit = preferences.edit()
        if (old != text) preferences.all.keys.filter { it.startsWith(CT_PREFIX) }.forEach(edit::remove)
        val now = if (old == text) preferences.getLong("installed_at", 0) else System.currentTimeMillis()
        check(edit.putString("profiles", text).putLong("installed_at", now).commit()) { "No se pudo guardar el perfil privado" }
        Installed(text, parsed, digest(text), now)
    }
    fun confirmation(ct: String, profileDigest: String, source: String): JSONObject? = synchronized(lock) {
        val key = ctKey(ct)
        val text = preferences.getString(key, null) ?: return@synchronized null
        val value = runCatching { validateConfirmation(text, profileDigest); JSONObject(text) }.getOrNull()
        if (value == null || value.optString("source") != source) {
            // apply() changes memory atomically and queues disk IO; never block CT selection on fsync.
            preferences.edit().remove(key).apply()
            null
        } else value
    }
    fun saveConfirmation(ct: String, value: JSONObject) = synchronized(lock) {
        val installed = requireNotNull(installed())
        val text = value.toString()
        validateConfirmation(text, installed.digest)
        check(preferences.edit().putString(ctKey(ct), text).commit()) { "No se pudo guardar la confirmación del CT" }
    }
    fun writeBackup(output: Writer) {
        val snapshot = synchronized(lock) {
            val installed = requireNotNull(installed()) { "No hay perfil privado instalado" }
            installed to preferences.all.filterKeys { it.startsWith(CT_PREFIX) }.toSortedMap()
        }
        val installed = snapshot.first
        // Never hold the store lock while a SAF provider is writing or waiting for IO.
        JsonWriter(output).apply {
            beginObject(); name("format").value(FORMAT); name("version").value(1L)
            name("profiles").value(installed.text); name("installedAt").value(installed.installedAt)
            name("confirmations"); beginObject()
            snapshot.second.forEach { (key, value) ->
                require(key.matches(Regex("ct-v1:[a-f0-9]{64}")))
                val text = value as String; validateConfirmation(text, installed.digest)
                name(key).value(text)
            }
            endObject(); endObject(); flush()
        }
    }
    /** Called only after PortableBackup has authenticated the complete encrypted stream. */
    fun readBackup(input: Reader) {
        val entries = linkedMapOf<String, String>()
        var text: String? = null
        var installedAt = 0L
        JsonReader(input).use { reader ->
            reader.beginObject()
            require(reader.nextName() == "format" && reader.nextString() == FORMAT)
            require(reader.nextName() == "version" && reader.nextInt() == 1)
            require(reader.nextName() == "profiles")
            text = reader.nextString(); require(text!!.length <= 131072)
            val parsed = LocalAddressProfiles.decode(text!!)
            val hash = digest(text!!)
            require(parsed.normal.prefix == 28 && parsed.extended.prefix == 26)
            require(reader.nextName() == "installedAt"); installedAt = reader.nextLong(); require(installedAt >= 0)
            require(reader.nextName() == "confirmations"); reader.beginObject()
            while (reader.hasNext()) {
                require(entries.size < 1_000_000)
                val key = reader.nextName()
                require(key.matches(Regex("ct-v1:[a-f0-9]{64}")) && key !in entries)
                val value = reader.nextString(); validateConfirmation(value, hash); entries[key] = value
            }
            reader.endObject(); reader.endObject()
            require(reader.peek() == android.util.JsonToken.END_DOCUMENT)
        }
        synchronized(lock) {
            val edit = preferences.edit()
            preferences.all.keys.filter { it.startsWith(CT_PREFIX) }.forEach(edit::remove)
            edit.putString("profiles", requireNotNull(text)).putLong("installed_at", installedAt)
            entries.forEach { (key, value) -> edit.putString(key, value) }
            check(edit.commit()) { "No se pudo restaurar el perfil privado" }
        }
    }
}
