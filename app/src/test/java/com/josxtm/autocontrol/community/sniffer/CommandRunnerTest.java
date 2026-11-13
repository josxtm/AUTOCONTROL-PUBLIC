package com.josxtm.autocontrol.community.sniffer;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class CommandRunnerTest {
    static class Channel implements CommandRunner.Channel {
        final CountDownLatch entered=new CountDownLatch(1),released=new CountDownLatch(1);
        volatile boolean disposed,finished,failConnect;
        int code;InputStream input;OutputStream errors;
        Channel(InputStream input){this.input=input;}
        public InputStream stdout(){return input;}
        public void stderr(OutputStream sink){errors=sink;}
        public void connect(int timeout)throws Exception{entered.countDown();if(failConnect)throw new IOException("synthetic");}
        public boolean closed(){return finished||disposed;}
        public int exitCode(){return code;}
        public void close(){disposed=true;released.countDown();CommandRunner.closeQuietly(input);}
    }
    private Channel hanging(){return new Channel(new ByteArrayInputStream(new byte[0]));}
    @Test(timeout=3000) public void timeoutWithoutEofClosesChannelAndKeepsPartialOutput(){
        Channel c=new Channel(new ByteArrayInputStream("partial".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        StringBuilder text=new StringBuilder();
        DiagnosticResult r=CommandRunner.run(c,false,60,new CancellationToken(),text::append);
        assertEquals(DiagnosticStatus.TIMEOUT,r.status);assertTrue(c.disposed);assertEquals("partial",text.toString());assertTrue(r.elapsedMillis>=40);
    }
    @Test(timeout=3000) public void cancellationWakesBlockedReadAndReleasesWorker()throws Exception{
        CountDownLatch reading=new CountDownLatch(1);
        InputStream blocked=new InputStream(){boolean closed;
            public int available(){return 1;}
            public synchronized int read()throws IOException{reading.countDown();while(!closed)try{wait();}catch(InterruptedException ignored){}return -1;}
            public synchronized void close(){closed=true;notifyAll();}
        };
        Channel c=new Channel(blocked);CancellationToken token=new CancellationToken();ExecutorService worker=Executors.newSingleThreadExecutor();
        try{
            Future<DiagnosticResult> task=worker.submit(()->CommandRunner.run(c,false,2000,token,null));
            assertTrue(reading.await(1,TimeUnit.SECONDS));token.cancel();
            assertEquals(DiagnosticStatus.CANCELLED,task.get(1,TimeUnit.SECONDS).status);assertTrue(c.disposed);
        }finally{worker.shutdownNow();assertTrue(worker.awaitTermination(1,TimeUnit.SECONDS));}
    }
    @Test(timeout=3000) public void timeoutDuringChannelOpenActivelyClosesIt()throws Exception{
        Channel c=new Channel(new ByteArrayInputStream(new byte[0])){
            public void connect(int timeout)throws Exception{entered.countDown();released.await();throw new IOException();}
        };
        assertEquals(DiagnosticStatus.TIMEOUT,CommandRunner.run(c,false,50,new CancellationToken(),null).status);assertTrue(c.disposed);
    }
    @Test public void successErrorsAndTruncationAlwaysCloseResources(){
        Channel ok=hanging();ok.finished=true;
        assertEquals(DiagnosticStatus.VALID_RESPONSE,CommandRunner.run(ok,false,1000,new CancellationToken(),null).status);assertTrue(ok.disposed);
        Channel fail=hanging();fail.failConnect=true;
        assertEquals(DiagnosticStatus.SSH_ERROR,CommandRunner.run(fail,false,1000,new CancellationToken(),null).status);assertTrue(fail.disposed);
        Channel large=new Channel(new ByteArrayInputStream(new byte[CommandRunner.MAX_OUTPUT+1]));large.finished=true;
        assertEquals(DiagnosticStatus.SSH_ERROR,CommandRunner.run(large,false,1000,new CancellationToken(),null).status);assertTrue(large.disposed);
    }
    @Test public void cancellationBeforeStartNeverConnects(){
        Channel c=hanging();CancellationToken token=new CancellationToken();token.cancel();
        assertEquals(DiagnosticStatus.CANCELLED,CommandRunner.run(c,false,1000,token,null).status);assertEquals(1,c.entered.getCount());assertTrue(c.disposed);
    }
    @Test public void classificationUsesExitStatusNotLocalizedPingText(){
        for(int code:new int[]{0,1,2,-1}){
            Channel c=new Channel(new ByteArrayInputStream("100% packet loss".getBytes(java.nio.charset.StandardCharsets.UTF_8)));c.finished=true;c.code=code;
            assertEquals(code==0?DiagnosticStatus.VALID_RESPONSE:code==1?DiagnosticStatus.NO_RESPONSE:DiagnosticStatus.SSH_ERROR,
                CommandRunner.run(c,true,1000,new CancellationToken(),null).status);
        }
        assertEquals(DiagnosticStatus.SSH_ERROR,DiagnosticResult.exitStatus(true,1,true));
        assertEquals(DiagnosticStatus.SSH_ERROR,DiagnosticResult.exitStatus(false,1,false));
    }
    @Test public void cancellationRunsEveryResourceOnceEvenIfOneFails(){
        CancellationToken token=new CancellationToken();AtomicInteger count=new AtomicInteger();
        token.onCancel(()->{throw new IllegalStateException();});token.onCancel(count::incrementAndGet);
        CancellationToken.Registration removed=token.onCancel(()->count.addAndGet(100));removed.close();
        token.cancel();token.cancel();token.onCancel(count::incrementAndGet);assertEquals(2,count.get());
    }
}
