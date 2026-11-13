package com.josxtm.autocontrol.community.sniffer;

import java.net.*;
import java.util.concurrent.TimeUnit;

public final class LocalDiagnostic {
    interface Probe { boolean reachable(String address,int timeout) throws Exception; }
    public static DiagnosticResult check(String name,String address,CancellationToken cancellation) {
        return check(name,address,cancellation,(ip,timeout)->InetAddress.getByName(ip).isReachable(timeout));
    }
    static DiagnosticResult check(String name,String address,CancellationToken cancellation,Probe probe) {
        long start=System.nanoTime();DiagnosticStatus status;
        if(cancellation.isCancelled())status=DiagnosticStatus.CANCELLED;
        else if(address==null||!EndpointPolicy.diagnostic("ping -c 1 -W 2 "+address))status=DiagnosticStatus.INVALID_TARGET;
        else try{status=probe.reachable(address,900)?DiagnosticStatus.VALID_RESPONSE:DiagnosticStatus.NO_RESPONSE;}
        catch(SocketTimeoutException e){status=DiagnosticStatus.TIMEOUT;}
        catch(Exception e){status=DiagnosticStatus.LOCAL_ERROR;}
        if(cancellation.isCancelled())status=DiagnosticStatus.CANCELLED;
        return new DiagnosticResult(name,address,DiagnosticSource.DIRECT_ANDROID,status,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),null);
    }
    private LocalDiagnostic() { }
}
