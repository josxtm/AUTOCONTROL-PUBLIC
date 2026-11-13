package com.josxtm.autocontrol.community.sniffer;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class SshCancellationTest {
    @Test(timeout=4000) public void disconnectClosesConnectingSessionWithoutWaitingForItsLock()throws Exception{
        CountDownLatch connecting=new CountDownLatch(1),closed=new CountDownLatch(1);AtomicReference<byte[]> secretCopy=new AtomicReference<>();
        SshTransport.Connection pending=new SshTransport.Connection(){
            public void password(byte[] b){secretCopy.set(b);}
            public void connect(int t)throws Exception{connecting.countDown();closed.await();throw new java.io.IOException();}
            public boolean connected(){return false;}
            public CommandRunner.Channel exec(String c){throw new AssertionError();}
            public SshTransport.Shell shell(){throw new AssertionError();}
            public void close(){closed.countDown();}
        };
        SshTransport client=new SshTransport((h,p,u,k)->pending,new PinnedHostsTest.Store());ExecutorService worker=Executors.newSingleThreadExecutor();char[] value="synthetic-value".toCharArray();
        try{
            Future<?> task=worker.submit(()->{try{client.connect("synthetic.invalid",22,"user",value,(h,a,f)->true);fail();}catch(Exception expected){}});
            assertTrue(connecting.await(1,TimeUnit.SECONDS));client.disconnect();task.get(1,TimeUnit.SECONDS);client.disconnect();
            for(char c:value)assertEquals(0,c);for(byte b:secretCopy.get())assertEquals(0,b);
        }finally{worker.shutdownNow();assertTrue(worker.awaitTermination(1,TimeUnit.SECONDS));}
    }
    @Test public void cancelledConnectionDoesNotCreateSessionAndClearsPassword()throws Exception{
        SshTransport client=new SshTransport((h,p,u,k)->{fail();return null;},new PinnedHostsTest.Store());CancellationToken token=new CancellationToken();token.cancel();char[] value="synthetic-value".toCharArray();
        try{client.connect("synthetic.invalid",22,"user",value,(h,a,f)->true,token);fail();}catch(CancellationException expected){}
        for(char c:value)assertEquals(0,c);
    }
}
