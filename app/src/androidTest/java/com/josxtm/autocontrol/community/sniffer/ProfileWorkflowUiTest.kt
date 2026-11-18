package com.josxtm.autocontrol.community.sniffer

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.josxtm.autocontrol.community.MainActivity
import kotlinx.coroutines.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileWorkflowUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun missingProfileOpensMaintenanceAndInstalledProfileNeedsNoMonthlyImport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("synthetic-profile-ui", 0)
        prefs.edit().clear().commit()
        val store = AddressProfileStore(context, "synthetic-profile-ui")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var openedSettings = false
        try {
            compose.runOnUiThread {
                val state = CtActionState(context, SshTransport(context.getSharedPreferences("synthetic-ui-hosts", 0)), scope, store)
                compose.activity.setContent { MaterialTheme { CtActionsPanel(state, "", false, { openedSettings = true }) {} } }
            }
            compose.onNodeWithText("Configurar perfil una sola vez").performScrollTo().performClick()
            assertTrue(openedSettings)
            store.install(SyntheticPrivateProfile.text())
            compose.runOnUiThread {
                val state = CtActionState(context, SshTransport(context.getSharedPreferences("synthetic-ui-hosts", 0)), scope, store)
                state.changeCt("SYN-UI", mapOf("COUNT_A" to "1", "COUNT_B" to "2"), SyntheticPrivateProfile.base)
                compose.activity.setContent { MaterialTheme { CtActionsPanel(state, "", false) {} } }
            }
            compose.onNodeWithText("Configurar perfil una sola vez").assertDoesNotExist()
            compose.onNodeWithText("Guardar confirmación de este CT").performScrollTo().assertIsEnabled()
            compose.onNodeWithText("Transformadores confirmados").assertIsNotEnabled()
            compose.onNodeWithText("Remotas aplicables confirmadas").assertIsNotEnabled()
            compose.onNodeWithText("Preparar WAN de mantenimiento").performScrollTo().assertIsNotEnabled() // no SSH
            compose.onNodeWithText("MangoSSH avanzado").assertDoesNotExist() // CT actions don't require terminal
        } finally { compose.runOnUiThread { scope.cancel() }; prefs.edit().clear().commit() }
    }
}
