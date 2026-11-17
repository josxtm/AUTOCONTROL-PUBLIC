package com.josxtm.autocontrol.community.sniffer;

import static org.junit.Assert.*;
import org.junit.Test;

public final class LanTargetsTest {
    private static String ip(int a,int b,int c,int d) { return a+"."+b+"."+c+"."+d; }

    @Test public void computesRuntimeConfiguredOffsets() {
        String base=ip(198,51,100,64);
        for(int offset:new int[]{2,5,8,12}) assertEquals(ip(198,51,100,64+offset),LanTargets.at(base,28,offset));
    }
    @Test public void rejectsMalformedInput() {
        for (String address : new String[]{null, "", "hostname", ip(999,5,0,0), ip(198,51,100,2)}) {
            try { LanTargets.at(address, 28, 14); fail("Invalid base accepted"); }
            catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void restrictsToHostRangeForMask() {
        String network=ip(203,0,113,0);
        assertEquals(ip(203,0,113,1), LanTargets.at(network, 30, 1));
        assertEquals(ip(203,0,113,2), LanTargets.at(network, 30, 2));
        for (int offset : new int[]{0,3}) {
            try { LanTargets.at(network,30,offset); fail("Network/broadcast accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        assertEquals(ip(203,0,113,14), LanTargets.at(network, 27, 14));
        try { LanTargets.at(network, 27, 31); fail("Broadcast accepted"); }
        catch (IllegalArgumentException expected) {}
        try { LanTargets.at(network, 30, 14); fail("Out-of-subnet offset accepted"); }
        catch (IllegalArgumentException expected) {}
    }
}
