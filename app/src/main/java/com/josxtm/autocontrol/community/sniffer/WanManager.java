package com.josxtm.autocontrol.community.sniffer;

import java.util.*;
import java.util.regex.*;

/** Owns one temporary IPv4 alias. No UCI, address flush, LAN/Wi-Fi, gateway or firmware changes. */
public final class WanManager {
    interface Remote { Reply run(String script,CancellationToken token); boolean connected(); }
    static final class Reply {
        final DiagnosticResult result;final String output;
        Reply(DiagnosticResult result,String output){this.result=result;this.output=output;}
    }
    public static final class Failure extends Exception {
        public final DiagnosticStatus status;
        Failure(DiagnosticStatus status){super(new DiagnosticResult("",null,DiagnosticSource.MANGO_SSH,status,0,null).label());this.status=status;}
    }
    public static final class Snapshot {
        public final String device,lan,raw,routes,leaseOwner,leaseAddress,leaseDevice;
        public final boolean carrier;
        public final List<String> wanAddresses,lanAddresses;
        Snapshot(String output) {
            if(output.length()>32768)throw new IllegalArgumentException("Estado WAN excesivo");
            raw=output;
            device=value(output,"WAN_DEVICE=");lan=value(output,"LAN_DEVICE=");
            WanCommands.device(device);WanCommands.device(lan);
            if(device.equals(lan))throw new IllegalArgumentException("WAN coincide con gestión");
            carrier=value(output,"CARRIER=").equals("1");
            String[] parts=output.split("WAN_ADDRESSES\\n|LAN_ADDRESSES\\n|WAN_ROUTES\\n|LEASE\\n",-1);
            if(parts.length!=5)throw new IllegalArgumentException("Estado WAN no reconocido");
            wanAddresses=addresses(parts[1]);lanAddresses=addresses(parts[2]);routes=parts[3].trim();
            String[] lease=parts[4].trim().split("\\s+");
            if(parts[4].isBlank()){leaseOwner=leaseAddress=leaseDevice="";}
            else {
                if(lease.length!=3)throw new IllegalArgumentException("Recuperación WAN pendiente; revisar sesión");
                WanCommands.lease(lease[0]);WanCommands.device(lease[2]);
                String[] a=lease[1].split("/");if(a.length!=2)throw new IllegalArgumentException("Sesión WAN incompleta");
                WanCommands.address(a[0],Integer.parseInt(a[1]));
                if(!lease[2].equals(device))throw new IllegalArgumentException("La WAN cambió; requiere recuperación verificada");
                leaseOwner=lease[0];leaseAddress=lease[1];leaseDevice=lease[2];
            }
        }
        private static String value(String text,String key) {
            String found=null;for(String line:text.split("\\n"))if(line.startsWith(key)){if(found!=null)throw new IllegalArgumentException("Estado ambiguo");found=line.substring(key.length()).trim();}
            if(found==null)throw new IllegalArgumentException("Estado incompleto");return found;
        }
        private static List<String> addresses(String text) {
            List<String> values=new ArrayList<>();Matcher matcher=Pattern.compile("\\binet ([0-9.]+/[0-9]{1,2})\\b").matcher(text);
            while(matcher.find()){
                String a=matcher.group(1);String[] pair=a.split("/");AddressPlan.number(pair[0]);int p=Integer.parseInt(pair[1]);
                if(p<1||p>32)throw new IllegalArgumentException("Máscara remota inválida");values.add(a);
            }return Collections.unmodifiableList(values);
        }
    }
    public enum Probe { CLEAR_ARP, CONFLICT, UNKNOWN }
    public static final class Prepared {
        public final AddressPlan plan;public final String ip;public final Snapshot snapshot;public final Probe probe;
        Prepared(AddressPlan p,String ip,Snapshot s,Probe probe){plan=p;this.ip=ip;snapshot=s;this.probe=probe;}
    }
    private final Remote remote;
    private volatile String owner="",ip="",device="",ct="";private volatile int prefix;
    public WanManager(SshTransport transport){this(new Remote(){
        public Reply run(String script,CancellationToken token){StringBuilder out=new StringBuilder();DiagnosticResult result=transport.executeControl(script,15000,token,out::append);return new Reply(result,out.toString());}
        public boolean connected(){return transport.connected();}
    });}
    WanManager(Remote remote){this.remote=remote;}
    private String execute(String command,CancellationToken token)throws Failure {
        if(token.isCancelled())throw new Failure(DiagnosticStatus.CANCELLED);
        Reply r=remote.run(command,token);
        if(r.result.status!=DiagnosticStatus.VALID_RESPONSE)throw new Failure(r.result.status);
        return r.output;
    }
    public boolean hasLease(){return !owner.isEmpty();}
    public String leaseCt(){return ct;}
    public synchronized Snapshot inspect(CancellationToken token)throws Failure {
        Snapshot s=new Snapshot(execute(WanCommands.DISCOVER,token));
        if(!s.leaseOwner.isEmpty()){
            if(owner.isEmpty() || !owner.equals(s.leaseOwner))ct="Recuperación de sesión anterior";
            owner=s.leaseOwner;String[] a=s.leaseAddress.split("/");ip=a[0];prefix=Integer.parseInt(a[1]);device=s.leaseDevice;
        } else if(!owner.isEmpty()){owner=ip=device=ct="";}
        return s;
    }
    private static void validate(AddressPlan plan,String ip,Snapshot s,String managementHost) {
        plan.validateMaintenance(ip);
        if(!s.carrier)throw new IllegalArgumentException("WAN sin enlace físico");
        if(!s.leaseOwner.isEmpty())throw new IllegalArgumentException("Restaura primero la WAN temporal anterior");
        AddressPlan.number(managementHost);
        if(AddressPlan.overlaps(plan.base,plan.profile.prefix,managementHost,32))throw new IllegalArgumentException("La subred coincide con el acceso SSH de gestión");
        if(s.lanAddresses.isEmpty())throw new IllegalArgumentException("No se ha podido verificar la LAN de gestión");
        for(String a:s.lanAddresses){String[] p=a.split("/");if(AddressPlan.overlaps(plan.base,plan.profile.prefix,p[0],Integer.parseInt(p[1])))throw new IllegalArgumentException("La subred se solapa con LAN/Wi-Fi de gestión");}
        for(String a:s.wanAddresses)if(a.split("/")[0].equals(ip))throw new IllegalArgumentException("La IP ya pertenece al Mango; no se modifica");
    }
    private Probe probe(String device,String ip,CancellationToken token)throws Failure {
        String out=execute(WanCommands.probe(device,ip),token).trim();
        if(out.equals("PROBE=conflict"))return Probe.CONFLICT;
        if(out.equals("PROBE=clear-arp"))return Probe.CLEAR_ARP;
        if(out.equals("PROBE=unknown"))return Probe.UNKNOWN;
        throw new IllegalArgumentException("Comprobación de ocupación no válida");
    }
    public synchronized Prepared prepare(AddressPlan plan,String ip,String managementHost,CancellationToken token)throws Failure {
        Snapshot s=inspect(token);validate(plan,ip,s,managementHost);
        return new Prepared(plan,ip,s,probe(s.device,ip,token));
    }
    public synchronized void apply(Prepared proposal,String managementHost,boolean authorized,boolean physicallyAbsent,CancellationToken token)throws Failure {
        if(!authorized||!physicallyAbsent)throw new IllegalArgumentException("Confirma autorización y ausencia física de otro equipo con esa IP");
        Prepared fresh=prepare(proposal.plan,proposal.ip,managementHost,token);
        if(fresh.probe==Probe.CONFLICT)throw new IllegalArgumentException("Conflicto conocido: la IP está ocupada");
        owner=UUID.randomUUID().toString().replace("-","");ip=fresh.ip;device=fresh.snapshot.device;prefix=fresh.plan.profile.prefix;ct=fresh.plan.ct;
        try {
            String applied=execute(WanCommands.apply(device,fresh.snapshot.lan,ip,prefix,owner,fresh.snapshot.lanAddresses),token);
            if(!applied.trim().equals("APPLIED"))throw new Failure(DiagnosticStatus.COMMAND_ERROR);
            Snapshot verified=inspect(token);
            if(!verified.leaseOwner.equals(owner)||!verified.wanAddresses.contains(ip+"/"+prefix))
                throw new Failure(DiagnosticStatus.COMMAND_ERROR);
            if(token.isCancelled())throw new Failure(DiagnosticStatus.CANCELLED);
        } catch(Failure|RuntimeException error) {
            // A closed channel does not prove the remote mutation did not execute. Keep recovery state on failure.
            try{restore(new CancellationToken());}catch(Exception ignored){/* Remote watchdog/reconnect retain recovery path. */}
            throw error;
        }
    }
    public synchronized void restore(CancellationToken token)throws Failure {
        if(owner.isEmpty())inspect(token);
        if(owner.isEmpty())return;
        execute(WanCommands.restore(device,ip,prefix,owner),token);
        owner=ip=device=ct="";
    }
    public boolean connected(){return remote.connected();}
}
