package com.josxtm.autocontrol.community.sniffer;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Invented assignments only; intentionally unrelated to any operational service table. */
public class AddressPlanTest {
    static String base(){return String.join(".","192","0","2","64");}
    static String ip(int last){return String.join(".","192","0","2",Integer.toString(last));}
    static AddressPlan.Profile profile(int prefix){
        return new AddressPlan.Profile("synthetic-v1",prefix,Arrays.asList(
            new AddressPlan.Service("a","Synthetic A",2,"transformers",1,"https",8443),
            new AddressPlan.Service("b","Synthetic B",5,"remotes",2,"",80),
            new AddressPlan.Service("c","Synthetic C",10,"manual",0,"",80)),
            new LinkedHashSet<>(Arrays.asList(10,6)),Collections.singleton(7));
    }
    static AddressPlan plan(int t,int r){return new AddressPlan("SYN-A",base(),profile(t>2||r>2?26:28),t,r,Collections.emptyMap());}
    @Test public void detectionUsesBothCountsAndBlocksAmbiguity(){
        assertEquals(AddressPlan.Kind.NORMAL,AddressPlan.kind(2,2));
        assertEquals(AddressPlan.Kind.EXTENDED,AddressPlan.kind(1,3));
        assertEquals(AddressPlan.Kind.EXTENDED,AddressPlan.kind(3,0));
        assertEquals(AddressPlan.Kind.EXTENDED,AddressPlan.kind(4,null));
        assertEquals(AddressPlan.Kind.UNKNOWN,AddressPlan.kind(1,null));
        assertEquals(AddressPlan.Kind.UNKNOWN,AddressPlan.kind(null,1));
        assertEquals(AddressPlan.Kind.UNKNOWN,AddressPlan.kind(0,1));
    }
    @Test public void offsetsUseNetworkBaseAndValidateNormalAndExtended(){
        assertEquals(ip(66),plan(1,1).address(profile(28).services.get(0)));
        assertEquals(ip(66),plan(4,3).address(profile(26).services.get(0)));
        for(String input:Arrays.asList(ip(65),base()+"/24",base()+"/26")){
            try{new AddressPlan("SYN",input,profile(28),1,1,Collections.emptyMap());fail();}catch(IllegalArgumentException expected){}
        }
        try{new AddressPlan("SYN",base(),profile(28),3,1,Collections.emptyMap());fail();}catch(IllegalArgumentException expected){}
        assertEquals(ip(126),LanTargets.at(base(),26,62));
        try{LanTargets.at(base(),26,63);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void reservedUnknownAndOccupiedSlotsAreNotMaintenanceCandidates(){
        AddressPlan p=plan(1,1);
        assertEquals(ip(70),p.candidate()); // +10 has an unknown service, so it is never assumed free.
        for(int n:new int[]{0,2,7,10,15,63}){
            try{p.validateMaintenance(ip(64+n));fail();}catch(IllegalArgumentException expected){}
        }
        Map<String,AddressPlan.Presence> released=Collections.singletonMap("c",AddressPlan.Presence.ABSENT);
        AddressPlan explicit=new AddressPlan("SYN",base(),profile(28),1,1,released);
        explicit.validateMaintenance(ip(74)); // Requires a separate physical/authorized confirmation at WAN apply.
        assertEquals(AddressPlan.Presence.ABSENT,p.presence(profile(28).services.get(1)));
    }
    @Test public void webOnlyForInstalledDevicesAndExplicitProtocol(){
        AddressPlan p=plan(1,2);
        assertEquals("https",p.web(profile(28).services.get(0)).getScheme());
        assertEquals(ip(66),p.web(profile(28).services.get(0)).getHost());
        assertEquals(8443,p.web(profile(28).services.get(0)).getPort());
        try{p.web(profile(28).services.get(1));fail();}catch(IllegalArgumentException expected){}
        try{new AddressPlan.Service("bad","x",3,"manual",0,"javascript",80);fail();}catch(IllegalArgumentException expected){}
        assertTrue(AddressPlan.overlaps(base(),28,ip(68),32));
        assertFalse(AddressPlan.overlaps(base(),28,String.join(".","198","51","100","1"),24));
    }
}
