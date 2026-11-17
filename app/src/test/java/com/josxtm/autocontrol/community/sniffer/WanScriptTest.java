package com.josxtm.autocontrol.community.sniffer;

import org.junit.*;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Executes the actual fixed scripts against fake executables/files; never invokes host network tools. */
public class WanScriptTest {
    private Path root;private String management;
    @Before public void fixture()throws Exception {
        root=Files.createTempDirectory("synthetic-wan-script-");management=WanManagerTest.management();
        Files.createDirectories(root.resolve("bin"));Files.createDirectories(root.resolve("net/synthetic-wan"));
        write(root.resolve("net/synthetic-wan/carrier"),"1\n");
        write(root.resolve("network.sh"),"network_flush_cache() { :; }\nnetwork_get_device() { if test \"$2\" = wan; then eval \"$1=synthetic-wan\"; else eval \"$1=synthetic-lan\"; fi; }\n");
        program("ip", "case \"$*\" in\n"+
            " '-4 addr show dev synthetic-wan') echo ' inet "+String.join(".","203","0","113","2")+"/24 scope global'; if test -f \"$FIXTURE/alias\"; then printf ' inet %s scope global\\n' \"$(cat \"$FIXTURE/alias\")\"; fi;;\n"+
            " '-4 addr show dev synthetic-lan') echo ' inet "+management+"/24 scope global';;\n"+
            " '-4 addr add '*) if test -f \"$FIXTURE/delay\"; then sleep 0.2; fi; printf '%s\\n' \"$4\" > \"$FIXTURE/alias\";;\n"+
            " '-4 addr del '*) test \"$(cat \"$FIXTURE/alias\")\" = \"$4\" || exit 2; rm \"$FIXTURE/alias\";;\n"+
            " '-4 route get '*) echo 'unicast synthetic dev synthetic-wan scope link';;\n"+
            " '-4 route show dev synthetic-wan') echo synthetic-original-route;;\n"+
            " '-4 neigh show dev synthetic-wan') :;;\n"+
            " *) exit 2;; esac\n");
        program("nohup", "printf '%s' \"$3\" > \"$FIXTURE/watchdog\"\n");
        program("ping", "exit 1\n");program("arping", "exit 0\n");
    }
    private static void write(Path path,String text)throws Exception{Files.write(path,text.getBytes(StandardCharsets.UTF_8));}
    private static String read(Path path)throws Exception{return new String(Files.readAllBytes(path),StandardCharsets.UTF_8);}
    private void program(String name,String body)throws Exception {
        Path path=root.resolve("bin/"+name);write(path,"#!/bin/sh\nset -eu\n"+body);assertTrue(path.toFile().setExecutable(true));
    }
    private int run(String script)throws Exception {
        String local=script.replace("/lib/functions/network.sh",root.resolve("network.sh").toString())
            .replace("/sys/class/net",root.resolve("net").toString()).replace(WanCommands.DIR,root.resolve("lease").toString());
        ProcessBuilder builder=new ProcessBuilder("sh");builder.environment().put("PATH",root.resolve("bin")+":"+System.getenv("PATH"));builder.environment().put("FIXTURE",root.toString());
        Process process=builder.redirectError(ProcessBuilder.Redirect.to(root.resolve("process-"+UUID.randomUUID()).toFile())).redirectOutput(ProcessBuilder.Redirect.to(root.resolve("process-"+UUID.randomUUID()).toFile())).start();
        process.getOutputStream().write(local.getBytes(StandardCharsets.UTF_8));process.getOutputStream().close();
        assertTrue("Synthetic script stalled",process.waitFor(8,TimeUnit.SECONDS));return process.exitValue();
    }
    private String apply(String id){return WanCommands.apply("synthetic-wan","synthetic-lan",AddressPlanTest.ip(70),28,id,Collections.singletonList(management+"/24"));}
    private String restore(String id){return WanCommands.restore("synthetic-wan",AddressPlanTest.ip(70),28,id);}
    @After public void clean()throws Exception {
        try(java.util.stream.Stream<Path> files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception e){throw new RuntimeException(e);}});}
    }
    @Test public void actualScriptsApplyRestoreAndOldWatchdogCannotRemoveNewLease()throws Exception {
        String first="a".repeat(32),next="b".repeat(32);
        assertEquals(0,run(apply(first)));assertEquals(AddressPlanTest.ip(70)+"/28",read(root.resolve("alias")).trim());
        String oldWatchdog=read(root.resolve("watchdog")).replace("sleep 600",":");
        assertEquals(0,run(restore(first)));assertFalse(Files.exists(root.resolve("alias")));
        assertEquals(0,run(apply(next)));
        assertNotEquals(0,run(oldWatchdog));assertTrue(Files.exists(root.resolve("alias")));
        assertEquals(next,read(root.resolve("lease/owner")).trim());
        assertEquals(0,run(restore(next)));assertFalse(Files.exists(root.resolve("alias")));
    }
    @Test public void restorationWinsBothBeforeAndDuringApply()throws Exception {
        String early="c".repeat(32),during="d".repeat(32);
        assertEquals(0,run(restore(early)));assertNotEquals(0,run(apply(early)));assertFalse(Files.exists(root.resolve("alias")));
        write(root.resolve("delay"),"synthetic");
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            Future<Integer> applying=worker.submit(()->run(apply(during)));
            long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(!Files.exists(root.resolve("lease"))&&System.nanoTime()<limit)Thread.sleep(5);
            assertTrue(Files.exists(root.resolve("lease")));
            assertEquals(0,run(restore(during)));assertEquals(0,(int)applying.get(2,TimeUnit.SECONDS));
            assertFalse(Files.exists(root.resolve("alias")));
        } finally {worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
    }
}
