package com.josxtm.autocontrol.community.sniffer;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class WanManagerTest {
    static String management(){return String.join(".","198","51","100","1");}
    static class Remote implements WanManager.Remote {
        String dev="synthetic-wan",lan="synthetic-lan",probe="clear-arp",owner="",address="";
        int mutations,restores; boolean carrier=true,cancelApply,failRestore;final List<String> scripts=new ArrayList<>();
        public boolean connected(){return true;}
        public WanManager.Reply run(String script,CancellationToken token){
            scripts.add(script);String out="";DiagnosticStatus status=DiagnosticStatus.VALID_RESPONSE;
            if(script.equals(WanCommands.DISCOVER)) out="WAN_DEVICE="+dev+"\nLAN_DEVICE="+lan+"\nCARRIER="+(carrier?1:0)+"\nWAN_ADDRESSES\n"+(owner.isEmpty()?"":" inet "+address+" scope global\n")+"LAN_ADDRESSES\n inet "+management()+"/24 scope global\nWAN_ROUTES\nLEASE\n"+
                (owner.isEmpty()?"":owner+"\n"+address+"\n"+dev+"\n");
            else if(script.contains("echo APPLIED")){
                mutations++;out="APPLIED\n";java.util.regex.Matcher m=java.util.regex.Pattern.compile("printf '%s\\\\n' '([a-f0-9]{32})'").matcher(script);
                assertTrue(m.find());owner=m.group(1);address=AddressPlanTest.ip(70)+"/28";
                if(cancelApply){token.cancel();status=DiagnosticStatus.CANCELLED;}
            } else if(script.contains("PROBE="))out="PROBE="+probe+"\n";
            else {restores++;if(failRestore)status=DiagnosticStatus.SSH_ERROR;else {owner="";address="";}}
            return new WanManager.Reply(new DiagnosticResult("",null,DiagnosticSource.MANGO_SSH,status,0,0),out);
        }
    }
    @Test public void confirmationReprobeConflictAndRestoration()throws Exception {
        Remote remote=new Remote();WanManager manager=new WanManager(remote);
        WanManager.Prepared p=manager.prepare(AddressPlanTest.plan(1,1),AddressPlanTest.ip(70),management(),new CancellationToken());
        try{manager.apply(p,management(),false,true,new CancellationToken());fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,remote.mutations);
        remote.probe="conflict";
        try{manager.apply(p,management(),true,true,new CancellationToken());fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,remote.mutations);
        remote.probe="unknown"; // No-response alone never grants permission: physical authorization was required above.
        manager.apply(p,management(),true,true,new CancellationToken());assertTrue(manager.hasLease());assertEquals("SYN-A",manager.leaseCt());
        manager.restore(new CancellationToken());assertFalse(manager.hasLease());assertEquals(1,remote.restores);
        String apply=remote.scripts.stream().filter(s->s.contains("echo APPLIED")).findFirst().get();
        assertTrue(apply.contains("sleep 600"));assertTrue(apply.contains("-mutex"));assertTrue(apply.contains("-cancel-"));
        assertFalse(apply.contains("uci "));assertFalse(apply.contains("addr flush"));assertFalse(apply.contains("route add"));
    }
    @Test public void cancellationRollsBackAndFailedRollbackKeepsRecovery()throws Exception {
        Remote remote=new Remote();WanManager manager=new WanManager(remote);
        WanManager.Prepared p=manager.prepare(AddressPlanTest.plan(1,1),AddressPlanTest.ip(70),management(),new CancellationToken());
        remote.cancelApply=true;remote.failRestore=true;
        try{manager.apply(p,management(),true,true,new CancellationToken());fail();}catch(WanManager.Failure e){assertEquals(DiagnosticStatus.CANCELLED,e.status);}
        assertTrue(manager.hasLease());
        remote.failRestore=false;manager.restore(new CancellationToken());assertFalse(manager.hasLease());
    }
    @Test public void newSessionRecoversRemoteLeaseAndProtectsManagement()throws Exception {
        Remote remote=new Remote();WanManager manager=new WanManager(remote);
        remote.owner="a".repeat(32);remote.address=AddressPlanTest.ip(70)+"/28";
        manager.inspect(new CancellationToken());assertTrue(manager.hasLease());assertTrue(manager.leaseCt().contains("Recuperación"));
        manager.restore(new CancellationToken());assertFalse(manager.hasLease());
        remote.lan=remote.dev;
        try{manager.inspect(new CancellationToken());fail();}catch(IllegalArgumentException expected){}
        remote.lan="synthetic-lan";remote.carrier=false;
        try{manager.prepare(AddressPlanTest.plan(1,1),AddressPlanTest.ip(70),management(),new CancellationToken());fail();}catch(IllegalArgumentException expected){}
        remote.carrier=true;
        try{manager.prepare(AddressPlanTest.plan(1,1),AddressPlanTest.ip(70),AddressPlanTest.ip(65),new CancellationToken());fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,remote.mutations);
    }
    @Test public void noShellInjectionAndScriptsParse()throws Exception {
        for(String bad:Arrays.asList("x;reboot","x\nreboot","$(reboot)","x'")){
            try{WanCommands.probe(bad,AddressPlanTest.ip(70));fail();}catch(IllegalArgumentException expected){}
            try{WanCommands.probe("synthetic-wan",bad);fail();}catch(IllegalArgumentException expected){}
        }
        for(String script:Arrays.asList(WanCommands.DISCOVER,WanCommands.probe("synthetic-wan",AddressPlanTest.ip(70)),
            WanCommands.apply("synthetic-wan","synthetic-lan",AddressPlanTest.ip(70),28,"a".repeat(32)),
            WanCommands.restore("synthetic-wan",AddressPlanTest.ip(70),28,"a".repeat(32)))){
            Process parser=new ProcessBuilder("sh","-n").start();parser.getOutputStream().write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));parser.getOutputStream().close();assertEquals(0,parser.waitFor());
        }
    }
}
