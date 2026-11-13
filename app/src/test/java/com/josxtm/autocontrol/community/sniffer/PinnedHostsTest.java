package com.josxtm.autocontrol.community.sniffer;

import com.jcraft.jsch.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;

public class PinnedHostsTest {
    static class Store implements PinnedHosts.Store {
        final Map<String,String> values=new HashMap<>();boolean fail;
        public String get(String key){return values.get(key);}
        public boolean put(String key,String value){if(fail)return false;values.put(key,value);return true;}
    }
    static byte[] key()throws Exception{return KeyPair.genKeyPair(new JSch(),KeyPair.RSA,2048).getPublicKeyBlob();}
    @Test public void firstUseRequiresApprovalChangedKeysNeverPrompt()throws Exception{
        Store store=new Store();byte[] key=key();AtomicInteger asks=new AtomicInteger();
        PinnedHosts pin=new PinnedHosts(store,(h,a,f)->{asks.incrementAndGet();return false;},"synthetic.invalid",22);
        String endpoint=PinnedHosts.endpoint("synthetic.invalid",22);
        assertEquals(HostKeyRepository.NOT_INCLUDED,pin.check(endpoint,key));assertTrue(store.values.isEmpty());
        pin=new PinnedHosts(store,(h,a,f)->{asks.incrementAndGet();return true;},"synthetic.invalid",22);
        assertEquals(HostKeyRepository.OK,pin.check(endpoint,key));assertEquals(HostKeyRepository.OK,pin.check(endpoint,key));
        assertEquals(HostKeyRepository.CHANGED,pin.check(endpoint,key()));assertEquals(2,asks.get());
    }
    @Test public void exactLegacyPortPinMigratesWithoutFreshApproval()throws Exception{
        for(int port:new int[]{22,2200}){
            Store store=new Store();byte[] key=key();String host="synthetic.invalid";
            store.put("pin."+PinnedHosts.sha256((port==22?host:"["+host+"]:"+port).getBytes(StandardCharsets.UTF_8)),PinnedHosts.sha256(key));
            PinnedHosts pin=new PinnedHosts(store,(h,a,f)->{fail("Known pin must not prompt");return false;},host,port);
            assertEquals(HostKeyRepository.OK,pin.check(PinnedHosts.endpoint(host,port),key));assertEquals(2,store.values.size());
            assertEquals(HostKeyRepository.CHANGED,pin.check(PinnedHosts.endpoint(host,port),key()));
        }
    }
    @Test public void newPortAndMissingIdentityRequireExplicitValidation()throws Exception{
        Store store=new Store();byte[] key=key();AtomicInteger asks=new AtomicInteger();
        for(int port:new int[]{22,2200}){
            PinnedHosts pin=new PinnedHosts(store,(h,a,f)->{asks.incrementAndGet();return true;},"synthetic.invalid",port);
            assertEquals(HostKeyRepository.OK,pin.check(PinnedHosts.endpoint("synthetic.invalid",port),key));
            assertEquals(HostKeyRepository.NOT_INCLUDED,pin.check("wrong-endpoint",key));
        }assertEquals(2,asks.get());
    }
    @Test public void persistenceFailureAndConflictingLegacyAreRejected()throws Exception{
        Store store=new Store();store.fail=true;byte[] key=key();String host="synthetic.invalid",endpoint=PinnedHosts.endpoint(host,22);
        PinnedHosts pin=new PinnedHosts(store,(h,a,f)->true,host,22);
        assertEquals(HostKeyRepository.NOT_INCLUDED,pin.check(endpoint,key));
        store.fail=false;assertEquals(HostKeyRepository.OK,pin.check(endpoint,key));
        store.put("pin."+PinnedHosts.sha256(host.getBytes(StandardCharsets.UTF_8)),"different");
        assertEquals(HostKeyRepository.CHANGED,pin.check(endpoint,key));
    }
}
