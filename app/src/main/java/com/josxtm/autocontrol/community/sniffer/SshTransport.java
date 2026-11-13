package com.josxtm.autocontrol.community.sniffer;

import android.content.SharedPreferences;
import com.jcraft.jsch.HostKeyRepository;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;

/** State locks protect references only. No network or stream I/O runs while holding them. */
public final class SshTransport {
    public interface Approval { boolean accept(String host,String algorithm,String sha256); }
    public interface Output { void onText(String text); }
    interface Connection extends AutoCloseable {
        void password(byte[] password) throws Exception;
        void connect(int timeoutMillis) throws Exception;
        boolean connected();
        CommandRunner.Channel exec(String command) throws Exception;
        Shell shell() throws Exception;
        @Override void close();
    }
    interface Shell extends AutoCloseable {
        InputStream stdout() throws Exception;
        OutputStream stdin() throws Exception;
        void connect(int timeoutMillis) throws Exception;
        boolean connected();
        @Override void close();
    }
    interface Factory { Connection create(String host,int port,String user,HostKeyRepository pins) throws Exception; }
    private final Object state = new Object();
    private final Object writes = new Object();
    private final Factory factory;
    private final PinnedHosts.Store pins;
    private Connection connection;
    private CancellationToken lifetime;
    private Shell shell;
    private InputStream shellOutput;
    private OutputStream shellInput;
    private final Set<CancellationToken> operations = new HashSet<>();

    public SshTransport(SharedPreferences preferences) {
        this(JschConnection::new,new PinnedHosts.Store() {
            public String get(String key){return preferences.getString(key,null);}
            public boolean put(String key,String value){return preferences.edit().putString(key,value).commit();}
        });
    }
    SshTransport(Factory factory,PinnedHosts.Store pins){this.factory=factory;this.pins=pins;}

    public void connect(String host,int port,String user,char[] password,Approval approval) throws Exception {
        connect(host,port,user,password,approval,new CancellationToken());
    }
    public void connect(String host,int port,String user,char[] password,Approval approval,CancellationToken cancellation) throws Exception {
        Connection next=null;
        boolean keep=false;
        try {
            if(!EndpointPolicy.valid(host,port,user))throw new IllegalArgumentException("Perfil no válido");
            disconnect();
            if(cancellation.isCancelled())throw new CancellationException();
            next=factory.create(host,port,user,new PinnedHosts(pins,approval,host,port));
            final Connection pending=next;
            synchronized(state){connection=next;lifetime=cancellation;}
            // Register before password/authentication. disconnect() sees even a connecting session.
            try(CancellationToken.Registration ignored=cancellation.onCancel(() -> closeResource(pending))) {
                if(cancellation.isCancelled())throw new CancellationException();
                PasswordBytes.use(password,pending::password);
                pending.connect(12000);
                synchronized(state){
                    keep=connection==pending&&!cancellation.isCancelled()&&pending.connected();
                }
                if(!keep)throw new CancellationException();
            }
        } finally {
            Arrays.fill(password,'\0');
            if(!keep&&next!=null){
                synchronized(state){if(connection==next){connection=null;lifetime=null;}}
                closeResource(next);
            }
        }
    }
    public boolean connected(){Connection current; synchronized(state){current=connection;}return current!=null&&current.connected();}
    public boolean shellConnected(){Shell current; synchronized(state){current=shell;}return current!=null&&current.connected();}

    /** Diagnostics remain allowlisted. Output is optional and separate from metadata. */
    public DiagnosticResult diagnostic(String command,long timeoutMillis,CancellationToken cancellation,Output output) {
        if(!EndpointPolicy.diagnostic(command))return result(DiagnosticStatus.INVALID_TARGET);
        if(timeoutMillis<1||timeoutMillis>60000)throw new IllegalArgumentException("Límite no válido");
        Connection owner;
        synchronized(state){owner=connection;operations.add(cancellation);}
        try {
            if(cancellation.isCancelled())return result(DiagnosticStatus.CANCELLED);
            if(owner==null||!owner.connected())return result(DiagnosticStatus.SSH_ERROR);
            CommandRunner.Channel channel=owner.exec(command);
            boolean changed;
            synchronized(state){changed=connection!=owner;}
            if(changed)cancellation.cancel();
            DiagnosticResult result=CommandRunner.run(channel,command.startsWith("ping "),timeoutMillis,cancellation,output);
            synchronized(state){changed=connection!=owner;}
            return changed?new DiagnosticResult("",null,DiagnosticSource.MANGO_SSH,DiagnosticStatus.CANCELLED,result.elapsedMillis,result.exitCode):result;
        } catch(Exception ignored) {
            return result(cancellation.isCancelled()?DiagnosticStatus.CANCELLED:DiagnosticStatus.SSH_ERROR);
        } finally {synchronized(state){operations.remove(cancellation);}}
    }
    private static DiagnosticResult result(DiagnosticStatus status){return new DiagnosticResult("",null,DiagnosticSource.MANGO_SSH,status,0,null);}

    /** Compatibility wrapper is bounded too; new callers use the typed overload. */
    public String diagnostic(String command) throws Exception {
        StringBuilder output=new StringBuilder();
        DiagnosticResult result=diagnostic(command,5000,new CancellationToken(),output::append);
        return output+"\n["+result.label()+"]";
    }
    public void openShell(Output output) throws Exception {
        Connection owner; Shell previous;
        synchronized(state){owner=connection;previous=shell;shell=null;}
        if(previous!=null)closeResource(previous);
        if(owner==null||!owner.connected())throw new IllegalStateException("Sin conexión SSH");
        Shell next=owner.shell();
        InputStream input=null; OutputStream commands=null;
        boolean keep=false;
        try {
            input=next.stdout();commands=next.stdin();
            synchronized(state){
                if(owner!=connection)throw new CancellationException();
                shell=next;shellOutput=input;shellInput=commands;
            }
            next.connect(10000);
            synchronized(state){keep=owner==connection&&shell==next&&next.connected();}
            if(!keep)throw new CancellationException();
            final InputStream stream=input;
            Thread reader=new Thread(()->{
                // InputStreamReader preserves multibyte text split across SSH packets.
                try(Reader text=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                    char[] buffer=new char[2048];int n;
                    while(next.connected()&&(n=text.read(buffer))>=0){
                        synchronized(state){if(shell!=next)return;}
                        output.onText(new String(buffer,0,n).replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]",""));
                    }
                }catch(IOException ignored){/* Socket/pipe closure is normal cancellation. */}
                finally{
                    synchronized(state){if(shell==next){shell=null;shellInput=null;shellOutput=null;}}
                    closeResource(next);
                }
            },"public-ssh-terminal-reader");
            reader.setDaemon(true);reader.start();
        }finally{
            if(!keep){
                closeResource(next);CommandRunner.closeQuietly(input);CommandRunner.closeQuietly(commands);
                synchronized(state){if(shell==next){shell=null;shellInput=null;shellOutput=null;}}
            }
        }
    }
    public void send(String command) throws Exception {
        if(command.length()>2048)throw new IllegalArgumentException("Comando demasiado largo");
        Shell current;OutputStream input;
        synchronized(state){current=shell;input=shellInput;}
        if(current==null||input==null||!current.connected())throw new IllegalStateException("Terminal sin abrir");
        byte[] bytes=(command+"\n").getBytes(StandardCharsets.UTF_8);
        try{synchronized(writes){input.write(bytes);input.flush();}}finally{Arrays.fill(bytes,(byte)0);}
    }
    private static void closeResource(AutoCloseable resource) {
        try { resource.close(); } catch (Exception ignored) { /* Release remaining resources too. */ }
    }
    public void disconnect() {
        Connection old;Shell oldShell;InputStream output;OutputStream input;CancellationToken pending;List<CancellationToken> running;
        synchronized(state){
            old=connection;connection=null;oldShell=shell;shell=null;output=shellOutput;shellOutput=null;input=shellInput;shellInput=null;
            pending=lifetime;lifetime=null;running=new ArrayList<>(operations);operations.clear();
        }
        // Socket first: wakes stalled SSH writes/reads without acquiring a command lock.
        if(old!=null)closeResource(old);
        if(pending!=null)pending.cancel();
        for(CancellationToken operation:running)operation.cancel();
        if(oldShell!=null)closeResource(oldShell);
        CommandRunner.closeQuietly(output);CommandRunner.closeQuietly(input);
    }
}
