package com.josxtm.autocontrol.community

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.josxtm.autocontrol.community.storage.*
import com.josxtm.autocontrol.community.sniffer.LanTargets
import org.junit.*
import org.junit.Assert.*
import java.io.StringReader
import java.io.File

/** Only documentation-reserved addresses, assembled to distinguish fixtures from operational data. */
class CatalogSnifferTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val base=listOf(192,0,2,0).joinToString(".")
    private fun catalog():String {
        val fields=listOf("MATRICULA [BDI]","NOMBRE [BDI]","IP LAN [EDIT]")+(0..359).map{"Synthetic column $it"}
        val prefix=(0..16).map{when(it){0->"\"28SYN001\"";1->"\"Synthetic CT\"";15,16->"0";else->"\"\""}}
        val row=prefix+listOf("\"28SYN001\"","\"Synthetic CT\"","\"$base/24\"")+(0..359).map{"\"\""}
        return "{\"format\":\"TELECONTROL_CT_V1\",\"version\":1,\"fieldNames\":[${fields.joinToString(","){"\"$it\""}}],\"records\":{\"28SYN001\":[${row.joinToString(",")}]}}"
    }
    @Test fun importReopenSearchAndSevenTargetsThroughCompose(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        WorkspaceStore(context).use{store->assertEquals(1,store.importCatalog(StringReader(catalog()),"id"));store.saveNote("28SYN001","Synthetic note");assertEquals(1,store.importCatalog(StringReader(catalog()),"id"));assertEquals("Synthetic note",store.note("28SYN001"))}
        WorkspaceStore(context).use{store->val record=store.find("28 syn001");assertEquals("Synthetic CT",CtFields.name(record));assertEquals("$base/24",CtFields.lan(record));assertEquals(380,record.size)
            listOf(1,2,3,9,11,13,14).forEach{assertEquals(listOf(192,0,2,it).joinToString("."),LanTargets.at(base,24,it))}}
        ui.activityRule.scenario.recreate()
        // Only this test window may capture its synthetic fixture; release FLAG_SECURE stays intact.
        ui.activityRule.scenario.onActivity { it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        ui.onAllNodesWithText("SNIFFER LAN",substring=false)[0].performClick()
        ui.onNodeWithText("Matrícula CT").performTextInput("28 syn001")
        ui.onNodeWithText("Buscar",substring=false).performClick()
        ui.waitUntil(20000){ui.onAllNodesWithText("Synthetic CT").fetchSemanticsNodes().isNotEmpty()}
        listOf(1,2,3,9,11,13,14).forEach{ui.onNodeWithText(listOf(192,0,2,it).joinToString(".")).assertExists()}
        ui.onAllNodesWithText("MangoSSH",substring=false)[0].performClick();ui.onNodeWithText("MangoSSH — Terminal").assertExists()
        ui.onNodeWithText("Escaneo",substring=false).performClick()
        // Capture the actual emulated tablet screen to a test-only shared path.
        // The instrumentation process may be removed by Gradle before the CI artifact stage.
        val handle = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/autocontrol-tablet-sniffer.png")
        handle.close()

    }
    @Test fun aliasesAmbiguityAndFailuresDoNotReplaceCatalogue(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        WorkspaceStore(context,"alias-synthetic.db").use{store->
            val json="[{\"MATRÍCULA\":\"45 syn1\",\"NOMBRE\":\"Synthetic alias\",\"IP_LAN\":\"$base/24\"}]"
            assertEquals(1,store.importCatalog(StringReader("\uFEFF" + json),"id"));assertEquals("Synthetic alias",CtFields.name(store.find("45SYN1")))
            for(bad in listOf("[{\"MATRÍCULA\":\"45SYN1\",\"MATRICULA\":\"45OTHER\"}]","[{\"MATRICULA\":\"45syn1\"},{\"MATRICULA\":\"45 SYN1\"}]","{}")){
                try{store.importCatalog(StringReader(bad),"id");fail("Expected rejection")}catch(_:Exception){}
                assertEquals("Synthetic alias",CtFields.name(store.find("45SYN1")))
            }
        };context.deleteDatabase("alias-synthetic.db")
    }
}
