package com.josxtm.autocontrol.community.sniffer;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;

public class PasswordBytesTest {
    @Test public void utf8BuffersAreClearedOnSuccessAndFailure()throws Exception{
        for(boolean fail:new boolean[]{false,true}){
            char[] value="synthetic-á-123".toCharArray();AtomicReference<byte[]> bytes=new AtomicReference<>();
            try{PasswordBytes.use(value,b->{bytes.set(b);assertEquals("synthetic-á-123",new String(b,StandardCharsets.UTF_8));if(fail)throw new IllegalStateException();});if(fail)fail("Expected error");}
            catch(IllegalStateException expected){assertTrue(fail);}
            for(char c:value)assertEquals(0,c);for(byte b:bytes.get())assertEquals(0,b);
        }
    }
    @Test public void invalidProfileStillWipesCallerBuffer()throws Exception{
        SshTransport transport=new SshTransport((h,p,u,k)->{fail();return null;},new PinnedHostsTest.Store());char[] value="synthetic".toCharArray();
        try{transport.connect("",22,"user",value,(h,a,f)->true);fail();}catch(IllegalArgumentException expected){}
        for(char c:value)assertEquals(0,c);
    }
}
