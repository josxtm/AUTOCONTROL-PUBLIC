package com.josxtm.autocontrol.community.sniffer;

import static org.junit.Assert.*;
import org.junit.Test;

public final class EndpointPolicyIntegrationTest {
    private String ip() { return String.join(".", "198","51","100","7"); }

    @Test public void acceptsOnlyReadOnlyCommands() {
        assertTrue(EndpointPolicy.diagnostic("whoami"));
        assertTrue(EndpointPolicy.diagnostic("ping -c 1 -W 2 " + ip()));
        assertFalse(EndpointPolicy.diagnostic("ping " + ip()));
        assertFalse(EndpointPolicy.diagnostic("ping -c 1 -W 2 " + ip() + "; reboot"));
        assertFalse(EndpointPolicy.diagnostic("ping -c 1 -W 2 host.example"));
        assertFalse(EndpointPolicy.diagnostic("reboot"));
    }
    @Test public void hostKeyPinningNeverTrustsChangedFingerprint() {
        assertTrue(EndpointPolicy.trusted("first", "first"));
        assertFalse(EndpointPolicy.trusted(null, "first"));
        assertTrue(EndpointPolicy.changed("first", "second"));
        assertFalse(EndpointPolicy.changed("first", "first"));
    }
}
