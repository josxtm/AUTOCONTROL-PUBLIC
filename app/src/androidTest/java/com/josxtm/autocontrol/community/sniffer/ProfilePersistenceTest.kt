package com.josxtm.autocontrol.community.sniffer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.josxtm.autocontrol.community.storage.PortableBackup
import com.josxtm.autocontrol.community.storage.WorkspaceStore
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*

/** Generated TEST-NET addresses and fictitious offsets only; no operational profiles. */
internal object SyntheticPrivateProfile {
    val base = listOf("192", "0", "2", "64").joinToString(".")
    fun text(): String {
        fun profile(prefix: Int, offset: Int) = JSONObject().put("revision", "synthetic-persist-v1").put("prefix", prefix)
            .put("services", JSONArray().put(JSONObject().put("id", "synthetic-device").put("label", "Synthetic device")
                .put("offset", offset).put("inventory", "manual").put("index", 0).put("scheme", "https").put("port", 8443)))
            .put("maintenanceCandidates", JSONArray().put(6)).put("reserved", JSONArray().put(10))
        return JSONObject().put("schema", 1).put("normal", profile(28, 4)).put("extended", profile(26, 27))
            .put("transformerFields", JSONArray().put("COUNT_A").put("NTRAFOS")).put("remoteFields", JSONArray().put("COUNT_B").put("RTUN")).toString()
    }
    fun confirmation(installed: AddressProfileStore.Installed, record: Map<String, String?>) = JSONObject()
        .put("schema", 1).put("profile", installed.digest).put("source", AddressProfileStore.source(installed.profiles, record, base))
        .put("transformers", "1").put("remotes", "2").put("base", base)
        .put("presence", JSONObject().put("synthetic-device", "INSTALLED")).put("web", JSONObject())
}

@RunWith(AndroidJUnit4::class)
class ProfilePersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "synthetic-profile-persistence"
    private fun store() = AddressProfileStore(context, name)
    private fun reset() { context.getSharedPreferences(name, 0).edit().clear().commit() }
    private fun backupText(store: AddressProfileStore): String = StringWriter().also { store.writeBackup(it) }.toString()
    private fun rejected(action: () -> Unit) { try { action(); fail("Invalid backup accepted") } catch (e: Exception) { /* no mutation */ } }

    @Test fun legacyPreferencesReopenAndGridUpdatePreserveProfileAndConfirmations() {
        reset()
        val prefs = context.getSharedPreferences(name, 0)
        // Comp. 255 stored this exact key without installed_at or a new schema.
        prefs.edit().putString("profiles", SyntheticPrivateProfile.text()).commit()
        val initial = requireNotNull(store().installed())
        assertEquals(0L, initial.installedAt)
        val record = mapOf("NTRAFOS" to "1", "RTUN" to "2")
        store().saveConfirmation("28-SYN-PERSIST", SyntheticPrivateProfile.confirmation(initial, record))
        val before = backupText(store())
        val dbName = "synthetic-profile-grid.db"
        context.deleteDatabase(dbName)
        val db = WorkspaceStore(context, dbName)
        try {
            val csv = "MATRICULA;IP LAN;NTRAFOS;RTUN;CONTRASEÑAS GCT\n28-SYN-PERSIST;${SyntheticPrivateProfile.base};1;2;synthetic-excluded\n"
            db.updateCsvCatalog(StringReader(csv), "id", true)
            db.saveNote("28-SYN-PERSIST", "Synthetic field note")
            db.addJournal("Synthetic historical diary")
            db.updateCsvCatalog(StringReader(csv), "id", false)
            db.updateCsvCatalog(StringReader(csv), "id", true)
            assertEquals("Synthetic field note", db.note("28-SYN-PERSIST"))
            assertEquals(1, db.journal(Long.MAX_VALUE).size)
            assertFalse(db.find("28-SYN-PERSIST").keys.any { it.contains("CONTRASE", true) })
            assertEquals(before, backupText(store()))
            val imported = db.find("28-SYN-PERSIST")
            assertEquals(1, LocalAddressProfiles.count(imported, initial.profiles.transformerFields))
            assertEquals(2, LocalAddressProfiles.count(imported, initial.profiles.remoteFields))
            assertEquals(AddressPlan.Kind.NORMAL, AddressPlan.kind(
                LocalAddressProfiles.count(imported, initial.profiles.transformerFields),
                LocalAddressProfiles.count(imported, initial.profiles.remoteFields)))
            assertNotNull(store().confirmation("28-SYN-PERSIST", initial.digest,
                AddressProfileStore.source(initial.profiles, imported, imported["IP LAN"])))
            val reopened = requireNotNull(store().installed())
            assertEquals(initial.digest, reopened.digest)
            assertNotNull(store().confirmation("28-SYN-PERSIST", reopened.digest, AddressProfileStore.source(reopened.profiles, record, SyntheticPrivateProfile.base)))
            // Harmless monthly name/manufacturer changes do not discard a manual confirmation.
            assertEquals(AddressProfileStore.source(reopened.profiles, record, SyntheticPrivateProfile.base),
                AddressProfileStore.source(reopened.profiles, record + mapOf("FABRICANTE" to "Synthetic B", "NOMBRE" to "New label"), SyntheticPrivateProfile.base))
            store().install(initial.text)
            assertEquals(before, backupText(store()))
            store().install(initial.text.replace("synthetic-persist-v1", "synthetic-persist-v2"))
            assertNull(store().confirmation("28-SYN-PERSIST", initial.digest, AddressProfileStore.source(initial.profiles, record, SyntheticPrivateProfile.base)))
        } finally { db.close(); context.deleteDatabase(dbName); reset() }
    }

    @Test fun encryptedBackupRestoresAtomicallyAndRejectsWrongPasswordCorruptionAndWrongPayload() {
        reset()
        val original = store().install(SyntheticPrivateProfile.text())
        val record = mapOf("COUNT_A" to "1")
        store().saveConfirmation("SYN-BACKUP", SyntheticPrivateProfile.confirmation(original, record))
        val before = backupText(store())
        val encrypted = ByteArrayOutputStream().also { PortableBackup.export(it, "synthetic-backup-passphrase".toCharArray()) { out -> store().writeBackup(out) } }.toByteArray()
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("synthetic-persist-v1"))
        fun restore(bytes: ByteArray, pass: String = "synthetic-backup-passphrase") {
            PortableBackup.restore(ByteArrayInputStream(bytes), pass.toCharArray(), context.cacheDir) { store().readBackup(it) }
        }
        try {
            rejected { restore(encrypted, "synthetic-wrong-passphrase") }; assertEquals(before, backupText(store()))
            rejected { restore(encrypted.copyOf(encrypted.size - 1)) }; assertEquals(before, backupText(store()))
            val corrupt = encrypted.clone(); corrupt[corrupt.lastIndex] = (corrupt.last().toInt() xor 1).toByte()
            rejected { restore(corrupt) }; assertEquals(before, backupText(store()))
            rejected { store().readBackup(StringReader(before + " {}")) }; assertEquals(before, backupText(store()))
            rejected { store().readBackup(StringReader(before.replace("ct-v1:", "bad-key:"))) }; assertEquals(before, backupText(store()))
            val wrongPayload = ByteArrayOutputStream().also { PortableBackup.export(it, "synthetic-backup-passphrase".toCharArray()) { out -> out.write("{\"format\":\"catalog\"}") } }.toByteArray()
            rejected { restore(wrongPayload) }; assertEquals(before, backupText(store()))
            reset(); assertNull(store().installed()); restore(encrypted)
            assertEquals(before, backupText(store()))
            assertNotNull(store().confirmation("SYN-BACKUP", original.digest, AddressProfileStore.source(original.profiles, record, SyntheticPrivateProfile.base)))
        } finally { reset() }
    }

    @Test fun stateReusesMissingInventoryByCtAndInvalidatesChangedGridSource() {
        reset(); store().install(SyntheticPrivateProfile.text())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var state: CtActionState
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun newState() = CtActionState(context, SshTransport(context.getSharedPreferences("synthetic-persistence-hosts", 0)), scope, store())
        try {
            main {
                state = newState(); state.changeCt("SYN-CT", mapOf("COUNT_A" to "1", "FABRICANTE" to "Synthetic A"), SyntheticPrivateProfile.base)
                assertTrue(state.transformersAutomatic); assertFalse(state.remotesAutomatic); assertNull(state.plan())
                state.edit { state.remotes = "2" }; assertEquals(28, state.plan()?.profile?.prefix)
                state.saveConfirmation()
            }
            val until = System.nanoTime() + 10_000_000_000
            var busy = true
            while (busy && System.nanoTime() < until) { main { busy = state.busy }; if (busy) Thread.sleep(10) }
            main {
                assertFalse(state.busy); assertTrue(state.confirmationSaved)
                state = newState(); state.changeCt("SYN-CT", mapOf("COUNT_A" to "1", "FABRICANTE" to "Synthetic B"), SyntheticPrivateProfile.base)
                assertTrue(state.confirmationSaved); assertEquals("2", state.remotes); assertEquals(28, state.plan()?.profile?.prefix)
                assertFalse(state.authorized); assertFalse(state.physicallyAbsent); assertNull(state.proposal)
                state.changeCt("SYN-OTHER", mapOf("COUNT_A" to "1"), SyntheticPrivateProfile.base)
                assertNull(state.plan()); assertFalse(state.confirmationSaved)
                state.changeCt("SYN-CT", mapOf("COUNT_A" to "3"), SyntheticPrivateProfile.base)
                assertFalse(state.confirmationSaved); assertEquals(26, state.plan()?.profile?.prefix)
                state.changeCt("SYN-CT", mapOf("COUNT_A" to "3"), listOf("192", "0", "2", "65").joinToString("."))
                assertNull(state.plan()) // changed base for same record must be revalidated
                state.changeCt("SYN-ZERO", mapOf("COUNT_A" to "0", "COUNT_B" to "2"), SyntheticPrivateProfile.base)
                assertFalse(state.transformersAutomatic); assertNull(state.plan())
            }
        } finally { main { state.cancel(); scope.cancel() }; reset() }
    }
}
