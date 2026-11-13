package com.josxtm.autocontrol.community.sniffer;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.SocketTimeoutException;
import java.io.IOException;

public class DiagnosticResultTest {
    private String ip(){return String.join(".","192","0","2","7");}
    @Test public void directProbeSeparatesNoResponseTimeoutAndErrors(){
        assertEquals(DiagnosticStatus.VALID_RESPONSE,LocalDiagnostic.check("test",ip(),new CancellationToken(),(a,t)->true).status);
        assertEquals(DiagnosticStatus.NO_RESPONSE,LocalDiagnostic.check("test",ip(),new CancellationToken(),(a,t)->false).status);
        assertEquals(DiagnosticStatus.TIMEOUT,LocalDiagnostic.check("test",ip(),new CancellationToken(),(a,t)->{throw new SocketTimeoutException();}).status);
        assertEquals(DiagnosticStatus.LOCAL_ERROR,LocalDiagnostic.check("test",ip(),new CancellationToken(),(a,t)->{throw new IOException();}).status);
    }
    @Test public void invalidOrCancelledTargetNeverReachesNetwork(){
        assertEquals(DiagnosticStatus.INVALID_TARGET,LocalDiagnostic.check("test","synthetic.invalid",new CancellationToken(),(a,t)->{fail();return true;}).status);
        CancellationToken token=new CancellationToken();token.cancel();
        assertEquals(DiagnosticStatus.CANCELLED,LocalDiagnostic.check("test",ip(),token,(a,t)->{fail();return true;}).status);
        CancellationToken inFlight=new CancellationToken();
        assertEquals(DiagnosticStatus.CANCELLED,LocalDiagnostic.check("test",ip(),inFlight,(a,t)->{inFlight.cancel();return true;}).status);
    }
    @Test public void allStatesHaveExplicitLabelsAndPreserveSource(){
        for(DiagnosticSource source:DiagnosticSource.values())for(DiagnosticStatus status:DiagnosticStatus.values()){
            DiagnosticResult r=new DiagnosticResult("test",null,source,status,10,null).forTarget("other",ip());
            assertEquals(source,r.source);assertEquals(status,r.status);assertEquals(10,r.elapsedMillis);assertEquals("other",r.targetName);assertFalse(r.label().isBlank());
        }
        assertFalse(new DiagnosticResult("test",null,DiagnosticSource.DIRECT_ANDROID,DiagnosticStatus.NO_RESPONSE,0,null).label().toLowerCase().contains("desconectado"));
    }
}
