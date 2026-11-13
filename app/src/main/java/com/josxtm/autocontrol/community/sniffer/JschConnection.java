package com.josxtm.autocontrol.community.sniffer;

import com.jcraft.jsch.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** The existing JSch engine, with owned socket and bounded channel cleanup. */
final class JschConnection implements SshTransport.Connection {
    private final Session session;
    private final AtomicReference<Socket> socket=new AtomicReference<>();
    private final AtomicBoolean closed=new AtomicBoolean();
    // Only closes sockets, never waits for channel IO. One shared daemon, cancelled tasks removed.
    private static final ScheduledThreadPoolExecutor ABORT=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"ssh-close-failsafe");t.setDaemon(true);return t;});
    static {ABORT.setRemoveOnCancelPolicy(true);}
    JschConnection(String host,int port,String user,HostKeyRepository pins)throws JSchException {
        JSch jsch=new JSch();jsch.setHostKeyRepository(pins);
        session=jsch.getSession(user,host,port);
        session.setConfig("StrictHostKeyChecking","yes");
        session.setConfig("PreferredAuthentications","password");
        session.setHostKeyAlias(PinnedHosts.endpoint(host,port));
        session.setTimeout(12000);
        session.setSocketFactory(new SocketFactory(){
            public Socket createSocket(String host,int port)throws IOException{
                Socket next=new Socket();socket.set(next);
                try {
                    if(closed.get())throw new SocketException("Conexión cancelada");
                    next.connect(new InetSocketAddress(host,port),12000);
                    if(closed.get())throw new SocketException("Conexión cancelada");
                    return next;
                }catch(IOException|RuntimeException e){CommandRunner.closeQuietly(next);throw e;}
            }
            public InputStream getInputStream(Socket s)throws IOException{return s.getInputStream();}
            public OutputStream getOutputStream(Socket s)throws IOException{return s.getOutputStream();}
        });
    }
    public void password(byte[] password){session.setPassword(password);}
    // Even a cancelled attempt enters Session.connect's finally, which wipes its password copy.
    public void connect(int timeout)throws JSchException{session.connect(timeout);}
    public boolean connected(){return !closed.get()&&session.isConnected();}
    public CommandRunner.Channel exec(String command)throws Exception{
        ChannelExec channel=(ChannelExec)session.openChannel("exec");channel.setCommand(command);channel.setInputStream(null);
        AtomicBoolean released=new AtomicBoolean();
        return new CommandRunner.Channel(){
            public InputStream stdout()throws IOException{return channel.getInputStream();}
            public void stderr(OutputStream sink){channel.setErrStream(sink);}
            public void connect(int timeout)throws JSchException{
                if(released.get())throw new JSchException("Canal cancelado");
                try{channel.connect(timeout);}finally{if(released.get())closeChannel(channel);}
            }
            public boolean closed(){return channel.isClosed();}
            public int exitCode(){return channel.getExitStatus();}
            public void close(){released.set(true);closeChannel(channel);}
        };
    }
    public SshTransport.Shell shell()throws Exception{
        ChannelShell channel=(ChannelShell)session.openChannel("shell");channel.setPty(true);channel.setPtyType("xterm");
        AtomicBoolean released=new AtomicBoolean();
        return new SshTransport.Shell(){
            public InputStream stdout()throws IOException{return channel.getInputStream();}
            public OutputStream stdin()throws IOException{return channel.getOutputStream();}
            public void connect(int timeout)throws JSchException{
                if(released.get())throw new JSchException("Canal cancelado");
                try{channel.connect(timeout);}finally{if(released.get())closeChannel(channel);}
            }
            public boolean connected(){return channel.isConnected();}
            public void close(){released.set(true);closeChannel(channel);}
        };
    }
    private void closeChannel(Channel channel){
        // A stalled close packet must not block cleanup forever. Abort the socket only as fallback.
        ScheduledFuture<?> failsafe=ABORT.schedule(()->CommandRunner.closeQuietly(socket.get()),250,TimeUnit.MILLISECONDS);
        try{channel.disconnect();}finally{failsafe.cancel(false);}
    }
    public void close(){
        closed.set(true);CommandRunner.closeQuietly(socket.get());
        session.disconnect();
    }
}
