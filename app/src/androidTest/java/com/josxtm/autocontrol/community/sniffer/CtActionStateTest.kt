package com.josxtm.autocontrol.community.sniffer

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CtActionStateTest {
    @Test fun privateProfilesCountsCtChangesAndWebTargetsStayIsolated() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("sniffer-private-address-profiles", 0)
        val original = preferences.getString("profiles", null)
        preferences.edit().remove("profiles").commit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var state: CtActionState
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await() {
            val until = System.nanoTime() + 10_000_000_000
            var busy = true
            while (busy && System.nanoTime() < until) { main { busy = state.busy }; if (busy) Thread.sleep(10) }
            main { assertFalse(state.busy) }
        }
        val file = File(context.cacheDir, "synthetic-address-profile.tmp")
        try {
            fun profile(prefix: Int, offset: Int): JSONObject = JSONObject().put("revision", "synthetic-v1").put("prefix", prefix)
                .put("services", JSONArray().put(JSONObject().put("id", "synthetic-device").put("label", "Synthetic device")
                    .put("offset", offset).put("inventory", "manual").put("index", 0).put("scheme", "https").put("port", 8443)))
                .put("maintenanceCandidates", JSONArray().put(6)).put("reserved", JSONArray().put(7))
            val json = JSONObject().put("schema", 1).put("normal", profile(28, 4)).put("extended", profile(26, 27))
                .put("transformerFields", JSONArray().put("COUNT_A")).put("remoteFields", JSONArray().put("COUNT_B"))
            file.writeText(json.toString())
            main {
                state = CtActionState(context, SshTransport(context.getSharedPreferences("synthetic-action-hosts", 0)), scope)
                state.changeCt("SYN-A", mapOf("COUNT_A" to "1", "COUNT_B" to "1"), listOf("192", "0", "2", "64").joinToString("."))
                assertNull(state.plan())
                state.importProfiles(Uri.fromFile(file))
            }
            await()
            main {
                val plan = requireNotNull(state.plan())
                assertEquals(28, plan.profile.prefix)
                val service = plan.profile.services.single()
                assertEquals(AddressPlan.Presence.UNKNOWN, plan.presence(service))
                state.presence(service.id, AddressPlan.Presence.INSTALLED)
                state.configureWeb(service, "https", "8443")
                val updated = requireNotNull(state.plan())
                assertEquals(listOf("192", "0", "2", "68").joinToString("."), updated.web(requireNotNull(state.webService(service))).host)
                state.authorized = true; state.physicallyAbsent = true
                state.changeCt("SYN-B", mapOf("COUNT_A" to "1", "COUNT_B" to "3"), listOf("192", "0", "2", "128").joinToString("."))
                assertEquals(26, state.plan()?.profile?.prefix)
                assertEquals("SYN-B", state.ct); assertTrue(state.results.isEmpty()); assertTrue(state.webOverrides.isEmpty())
                assertNull(state.proposal); assertFalse(state.authorized); assertFalse(state.physicallyAbsent)
                state.changeCt("SYN-C", mapOf("COUNT_A" to "1"), listOf("192", "0", "2", "64").joinToString("."))
                assertNull(state.plan()) // no inferred /24 or missing remote count
                state.changeCt("SYN-D", mapOf("COUNT_A" to "3", "COUNT_B" to "1"), listOf("192", "0", "2", "65").joinToString("."))
                assertNull(state.plan()) // unaligned extended base
            }
            val invalid = json.toString().replace("synthetic-device", "bad;reboot")
            try { LocalAddressProfiles.decode(invalid); fail("Untrusted identifier accepted") } catch (_: IllegalArgumentException) { }
        } finally {
            main { state.cancel(); scope.cancel() }
            preferences.edit().putString("profiles", original).commit()
            file.delete()
        }
    }
}
