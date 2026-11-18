package com.josxtm.autocontrol.community.sniffer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** CI calls seed/verify in separate instrumentation processes around adb install -r. */
@RunWith(AndroidJUnit4::class)
class ProfileCompatibilityTest {
    @Test fun privateProfileSurvivesCompatiblePackageReplacement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val phase = InstrumentationRegistry.getArguments().getString("privateProfilePhase")
        val store = AddressProfileStore(context, if (phase == null) "synthetic-compatible-profile" else "sniffer-private-address-profiles")
        val record = mapOf("COUNT_A" to "1")
        if (phase != "verify") {
            val installed = store.install(SyntheticPrivateProfile.text())
            store.saveConfirmation("SYN-COMPATIBLE-UPDATE", SyntheticPrivateProfile.confirmation(installed, record))
        }
        val loaded = requireNotNull(store.installed())
        assertEquals(SyntheticPrivateProfile.text(), loaded.text)
        assertEquals("synthetic-persist-v1", loaded.profiles.normal.revision)
        assertNotNull(store.confirmation("SYN-COMPATIBLE-UPDATE", loaded.digest,
            AddressProfileStore.source(loaded.profiles, record, SyntheticPrivateProfile.base)))
        if (phase == null) context.getSharedPreferences("synthetic-compatible-profile", 0).edit().clear().commit()
    }
}
