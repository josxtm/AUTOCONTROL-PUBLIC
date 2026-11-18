package com.josxtm.autocontrol.community.sniffer

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.josxtm.autocontrol.community.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/** Simulates loss of SSH immediately after inspecting a live lease; no sockets or hardware. */
class ConnectionRecoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun reconnectIsEnabledButEndpointStaysLockedForRecovery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = context.getSharedPreferences("sniffer-ssh-profile", 0)
        val previous = listOf("host", "port", "user").associateWith { profile.getString(it, null) }
        val host = listOf("198", "51", "100", "1").joinToString(".")
        val address = listOf("192", "0", "2", "70").joinToString(".")
        profile.edit().putString("host", host).putString("port", "22").putString("user", "synthetic").commit()
        val online = java.util.concurrent.atomic.AtomicBoolean(false)
        val connection = object : SshTransport.Connection {
            override fun password(value: ByteArray) {}
            override fun connect(timeoutMillis: Int) { online.set(true) }
            override fun connected() = online.get()
            override fun close() { online.set(false) }
            override fun shell(): SshTransport.Shell = throw UnsupportedOperationException()
            override fun exec(command: String): CommandRunner.Channel {
                val text = if (command == WanCommands.DISCOVER)
                    "WAN_DEVICE=synthetic-wan\nLAN_DEVICE=synthetic-lan\nCARRIER=1\nWAN_ADDRESSES\n inet $address/28 scope global\nLAN_ADDRESSES\n inet $host/24 scope global\nWAN_ROUTES\nLEASE\n${"a".repeat(32)}\n$address/28\nsynthetic-wan\n" else ""
                return object : CommandRunner.Channel {
                    override fun stdout(): InputStream = ByteArrayInputStream(text.toByteArray())
                    override fun stderr(sink: OutputStream) {}
                    override fun connect(timeoutMillis: Int) {}
                    override fun closed() = true
                    override fun exitCode() = 0
                    override fun close() { online.set(false) }
                }
            }
        }
        val pins = object : PinnedHosts.Store {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String) = true
        }
        val client = SshTransport({ _, _, _, _ -> connection }, pins)
        try {
            client.connect(host, 22, "synthetic", "synthetic".toCharArray(), { _, _, _ -> true })
            compose.runOnUiThread { compose.activity.setContent { SnifferPanel(suppliedTransport = client) } }
            compose.onNodeWithText("Comprobar WAN").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("WAN comprobada; configuración permanente intacta.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("IPv4 Mango (gestión SSH)").assertIsNotEnabled()
            compose.onNodeWithText("Transformadores confirmados").assertIsNotEnabled()
            compose.onNodeWithText("IP LAN base del CT").assertIsNotEnabled()
            compose.onNodeWithText("Contraseña de sesión").assertIsEnabled()
            compose.onNodeWithText("Contraseña de sesión").performScrollTo().performTextInput("synthetic")
            compose.runOnUiThread {
                compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
            }
            compose.onNodeWithText("Conectar Mango").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(10000) { client.connected() }
            compose.onNodeWithText("IPv4 Mango (gestión SSH)").assertIsNotEnabled()
            assertTrue(client.connected())
        } finally {
            client.disconnect()
            val edit = profile.edit()
            previous.forEach { (key, value) -> if (value == null) edit.remove(key) else edit.putString(key, value) }
            edit.commit()
        }
    }
}
