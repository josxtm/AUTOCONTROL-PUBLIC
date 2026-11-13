package com.josxtm.autocontrol.community.sniffer;

import org.apache.sshd.server.*;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real JSch authentication/channels against an in-process loopback server; no external devices. */
public class SshTransportIntegrationTest {
    private SshServer server;private SshTransport client;private final PinnedHostsTest.Store pins=new PinnedHostsTest.Store();
    private final AtomicInteger asks=new AtomicInteger();
    private final BlockingQueue<CountDownLatch> destroyed=new LinkedBlockingQueue<>();
    private String host;
    private final class SyntheticCommand implements Command {
        final String name;OutputStream out;ExitCallback exit;final CountDownLatch closed=new CountDownLatch(1);
        SyntheticCommand(String name){this.name=name;}
        public void setInputStream(InputStream in){}
        public void setOutputStream(OutputStream out){this.out=out;}
        public void setErrorStream(OutputStream err){}
        public void setExitCallback(ExitCallback callback){exit=callback;}
        public void start(ChannelSession channel,Environment environment)throws IOException{
            destroyed.add(closed);
            out.write((name.equals("whoami")?"synthetic-user\n":"partial\n").getBytes(StandardCharsets.UTF_8));out.flush();
            if(name.equals("whoami"))exit.onExit(0);
            // Other allowlisted commands intentionally produce no EOF.
        }
        public void destroy(ChannelSession channel){closed.countDown();}
    }
    @Before public void start()throws Exception{
        host=InetAddress.getLoopbackAddress().getHostAddress();
        server=SshServer.setUpDefaultServer();server.setHost(host);server.setPort(0);
        SimpleGeneratorHostKeyProvider keys=new SimpleGeneratorHostKeyProvider();keys.setAlgorithm("RSA");keys.setKeySize(2048);server.setKeyPairProvider(keys);
        server.setPasswordAuthenticator((u,p,s)->u.equals("synthetic")&&p.equals("synthetic-value"));
        server.setCommandFactory((channel,command)->new SyntheticCommand(command));
        server.setShellFactory(channel->new SyntheticCommand("shell"));server.start();
        client=new SshTransport(JschConnection::new,pins);
    }
    private void connect()throws Exception{
        char[] value="synthetic-value".toCharArray();
        client.connect(host,server.getPort(),"synthetic",value,(h,a,f)->{asks.incrementAndGet();return true;});
        for(char c:value)assertEquals(0,c);assertTrue(client.connected());
    }
    @After public void stop()throws Exception{if(client!=null)client.disconnect();if(server!=null)server.stop(true);}
    @Test(timeout=20000) public void realChannelTimesOutThenReusesVerifiedSession()throws Exception{
        connect();StringBuilder output=new StringBuilder();
        DiagnosticResult timeout=client.diagnostic("pwd",200,new CancellationToken(),output::append);
        assertEquals(DiagnosticStatus.TIMEOUT,timeout.status);assertTrue(output.toString().contains("partial"));
        assertTrue(destroyed.poll(2,TimeUnit.SECONDS).await(2,TimeUnit.SECONDS));assertTrue(client.connected());
        assertEquals(DiagnosticStatus.VALID_RESPONSE,client.diagnostic("whoami",3000,new CancellationToken(),null).status);
        client.disconnect();connect();assertEquals(1,asks.get());
    }
    @Test(timeout=20000) public void cancellationAndDisconnectReleaseActualChannel()throws Exception{
        connect();ExecutorService worker=Executors.newSingleThreadExecutor();
        try{
            CancellationToken token=new CancellationToken();Future<DiagnosticResult> operation=worker.submit(()->client.diagnostic("pwd",10000,token,null));
            CountDownLatch channelClosed=destroyed.poll(3,TimeUnit.SECONDS);assertNotNull(channelClosed);token.cancel();
            assertEquals(DiagnosticStatus.CANCELLED,operation.get(2,TimeUnit.SECONDS).status);assertTrue(channelClosed.await(2,TimeUnit.SECONDS));assertTrue(client.connected());
            operation=worker.submit(()->client.diagnostic("pwd",10000,new CancellationToken(),null));
            channelClosed=destroyed.poll(3,TimeUnit.SECONDS);assertNotNull(channelClosed);client.disconnect();
            assertEquals(DiagnosticStatus.CANCELLED,operation.get(2,TimeUnit.SECONDS).status);assertFalse(client.connected());
        }finally{worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test(timeout=20000) public void strictPinChangeRejectsBeforeNewApproval()throws Exception{
        connect();client.disconnect();
        pins.values.replaceAll((k,v)->"synthetic-changed-fingerprint");char[] value="synthetic-value".toCharArray();
        try{client.connect(host,server.getPort(),"synthetic",value,(h,a,f)->{fail("Changed key must not ask for trust");return true;});fail("Changed key accepted");}
        catch(com.jcraft.jsch.JSchException expected){}
        assertFalse(client.connected());for(char c:value)assertEquals(0,c);assertEquals(1,asks.get());
    }
    @Test(timeout=20000) public void shellReaderTerminatesOnDisconnect()throws Exception{
        connect();CountDownLatch text=new CountDownLatch(1);client.openShell(chunk->text.countDown());assertTrue(text.await(3,TimeUnit.SECONDS));assertTrue(client.shellConnected());
        CountDownLatch shellClosed=destroyed.poll(3,TimeUnit.SECONDS);client.disconnect();assertFalse(client.shellConnected());assertTrue(shellClosed.await(3,TimeUnit.SECONDS));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(readerAlive()&&System.nanoTime()<deadline)Thread.sleep(10);
        assertFalse("Terminal reader left running",readerAlive());
    }
    private boolean readerAlive(){return Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.isAlive()&&t.getName().equals("public-ssh-terminal-reader"));}
}
