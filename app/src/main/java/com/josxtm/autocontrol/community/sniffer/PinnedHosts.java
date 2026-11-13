package com.josxtm.autocontrol.community.sniffer;

import com.jcraft.jsch.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Explicit endpoint binding, with exact compatibility for JSch's old host/[host]:port keys. */
final class PinnedHosts implements HostKeyRepository {
    interface Store { String get(String key); boolean put(String key, String value); }
    private final Store store;
    private final SshTransport.Approval approval;
    private final String endpoint, key, legacy;
    PinnedHosts(Store store, SshTransport.Approval approval, String host, int port) {
        this.store=store; this.approval=approval; endpoint=endpoint(host,port);
        key="pin.v2."+sha256(endpoint.getBytes(StandardCharsets.UTF_8));
        legacy="pin."+sha256((port==22?host:"["+host+"]:"+port).getBytes(StandardCharsets.UTF_8));
    }
    static String endpoint(String host,int port) {
        String normalized=host.trim().toLowerCase(Locale.ROOT);
        if(normalized.startsWith("[")&&normalized.endsWith("]"))normalized=normalized.substring(1,normalized.length()-1);
        return "["+normalized+"]:"+port;
    }
    static String sha256(byte[] value) {
        try { return "SHA256:"+Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Override public int check(String host,byte[] bytes) {
        if(!endpoint.equals(host))return NOT_INCLUDED;
        final String received=sha256(bytes);
        boolean needsApproval;
        synchronized(store) {
            String current=store.get(key),old=store.get(legacy);
            if(EndpointPolicy.changed(current,received)||EndpointPolicy.changed(old,received))return CHANGED;
            if(current!=null)return OK;
            needsApproval=old==null;
        }
        if(needsApproval) {
            String algorithm;
            try{algorithm=new HostKey(host,bytes).getType();}catch(JSchException e){return NOT_INCLUDED;}
            if(!approval.accept(endpoint,algorithm,received))return NOT_INCLUDED;
        }
        synchronized(store) {
            if(EndpointPolicy.changed(store.get(key),received)||EndpointPolicy.changed(store.get(legacy),received))return CHANGED;
            return store.put(key,received)?OK:NOT_INCLUDED;
        }
    }
    @Override public void add(HostKey key,UserInfo userInfo) { }
    @Override public void remove(String host,String type) { }
    @Override public void remove(String host,String type,byte[] key) { }
    @Override public String getKnownHostsRepositoryID(){return "Endpoint SHA256 pins";}
    @Override public HostKey[] getHostKey(){return new HostKey[0];}
    @Override public HostKey[] getHostKey(String host,String type){return new HostKey[0];}
}
