package com.josxtm.autocontrol.community.sniffer

import org.json.JSONObject
import java.text.Normalizer

/** Configuration stays in private app storage; no operational assignments are bundled. */
internal class LocalAddressProfiles(val normal: AddressPlan.Profile, val extended: AddressPlan.Profile,
    val transformerFields: List<String>, val remoteFields: List<String>) {
    companion object {
        fun decode(text: String): LocalAddressProfiles {
            require(text.length <= 131072) { "Configuración demasiado grande" }
            val root = JSONObject(text)
            require(root.getInt("schema") == 1)
            fun strings(key: String): List<String> {
                val array = root.getJSONArray(key)
                require(array.length() in 1..20)
                return (0 until array.length()).map { array.getString(it).also { s -> require(s.length in 1..128) } }
            }
            fun profile(key: String, prefix: Int): AddressPlan.Profile {
                val obj = root.getJSONObject(key)
                require(obj.getInt("prefix") == prefix)
                val list = obj.getJSONArray("services")
                require(list.length() in 1..100)
                val services = (0 until list.length()).map { index ->
                    val entry = list.getJSONObject(index)
                    AddressPlan.Service(entry.getString("id"), entry.getString("label"), entry.getInt("offset"),
                        entry.optString("inventory", "manual"), entry.optInt("index", 0),
                        entry.optString("scheme", ""), if (entry.optString("scheme", "").isEmpty()) 80 else entry.getInt("port"))
                }
                fun offsets(name: String): Set<Int> {
                    val values = obj.getJSONArray(name)
                    require(values.length() <= 62)
                    return (0 until values.length()).map { values.getInt(it) }.toCollection(linkedSetOf())
                }
                return AddressPlan.Profile(obj.getString("revision"), prefix, services, offsets("maintenanceCandidates"), offsets("reserved"))
            }
            return LocalAddressProfiles(profile("normal", 28), profile("extended", 26), strings("transformerFields"), strings("remoteFields"))
        }
        fun key(raw: String): String = Normalizer.normalize(raw.replace(Regex("\\s*\\[[^]]+]\\s*$"), ""), Normalizer.Form.NFD)
            .uppercase(java.util.Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
        fun count(record: Map<String, String?>, aliases: List<String>): Int? {
            val keys = aliases.map(::key).toSet()
            val values = record.filterKeys { key(it) in keys }.values.mapNotNull { it?.trim() }
            if (values.isEmpty() || values.distinct().size != 1) return null
            return values.singleOrNull()?.toIntOrNull()?.takeIf { it in 0..100 }
                ?: values.first().toIntOrNull()?.takeIf { it in 0..100 }
        }
    }
}
