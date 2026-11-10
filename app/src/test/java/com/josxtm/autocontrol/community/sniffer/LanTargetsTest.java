package com.josxtm.autocontrol.community.sniffer;

import static org.junit.Assert.*;
import org.junit.Test;

public final class LanTargetsTest {
    private static String ip(int a,int b,int c,int d) { return a+"."+b+"."+c+"."+d; }

    @Test public void computesAllSevenOffsets() {
        String base=ip(198,51,100,0);
        assertEquals(7, LanTargets.OFFSETS.size());
        assertEquals(ip(198,51,100,1), LanTargets.at(base, LanTargets.OFFSETS.get("Router")));
        assertEquals(ip(198,51,100,2), LanTargets.at(base, LanTargets.OFFSETS.get("CC T1")));
        assertEquals(ip(198,51,100,3), LanTargets.at(base, LanTargets.OFFSETS.get("CC T2")));
        assertEquals(ip(198,51,100,9), LanTargets.at(base, LanTargets.OFFSETS.get("SABT T2")));
        assertEquals(ip(198,51,100,11), LanTargets.at(base, LanTargets.OFFSETS.get("SABT T1")));
        assertEquals(ip(198,51,100,13), LanTargets.at(base, LanTargets.OFFSETS.get("FFAA1")));
        assertEquals(ip(198,51,100,14), LanTargets.at(base, LanTargets.OFFSETS.get("Remota")));
    }
    @Test public void rejectsMalformedInput() {
        for (String address : new String[]{null, "", "hostname", ip(999,5,0,0), ip(198,51,100,2)}) {
            try { LanTargets.at(address, 14); fail("Invalid base accepted"); }
            catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void restrictsToHostRangeForMask() {
        String network=ip(203,0,113,0);
        assertEquals(ip(203,0,113,14), LanTargets.at(network, 27, 14));
        try { LanTargets.at(network, 27, 31); fail("Broadcast accepted"); }
        catch (IllegalArgumentException expected) {}
        try { LanTargets.at(network, 30, 14); fail("Out-of-subnet offset accepted"); }
        catch (IllegalArgumentException expected) {}
    }
}
