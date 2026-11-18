package com.josxtm.autocontrol.community.sniffer;

import java.net.URI;
import java.util.*;

/** Pure engine. Device assignments come exclusively from an explicitly reviewed local profile. */
public final class AddressPlan {
    public enum Kind { NORMAL, EXTENDED, UNKNOWN }
    public enum Presence { INSTALLED, ABSENT, UNKNOWN }
    public static final class Service {
        public final String id, label, inventory, scheme;
        public final int offset, index, port;
        public Service(String id, String label, int offset, String inventory, int index, String scheme, int port) {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,40}") || label == null || label.isBlank() || label.length()>80 ||
                    offset<1 || index<0 || !(inventory.equals("transformers") || inventory.equals("remotes") || inventory.equals("manual")) ||
                    !(scheme.isEmpty() || scheme.equals("http") || scheme.equals("https")) || port<1 || port>65535)
                throw new IllegalArgumentException("Servicio local no válido");
            this.id=id;this.label=label;this.offset=offset;this.inventory=inventory;this.index=index;this.scheme=scheme;this.port=port;
        }
        public Presence presence(Integer transformers, Integer remotes) {
            Integer count=inventory.equals("transformers")?transformers:inventory.equals("remotes")?remotes:null;
            return count==null||index<1?Presence.UNKNOWN:index<=count?Presence.INSTALLED:Presence.ABSENT;
        }
    }
    public static final class Profile {
        public final String revision;
        public final int prefix;
        public final List<Service> services;
        public final Set<Integer> maintenanceCandidates, reserved;
        public Profile(String revision,int prefix,List<Service> services,Set<Integer> candidates,Set<Integer> reserved) {
            if(revision==null||revision.isBlank()||revision.length()>80 || (prefix!=28&&prefix!=26) || services.size()>100)
                throw new IllegalArgumentException("Perfil local no válido");
            Set<String> ids=new HashSet<>();
            for(Service s:services) if((prefix==28 && !s.inventory.equals("manual") && s.index>2) || s.offset>=((1L<<(32-prefix))-1)||!ids.add(s.id))throw new IllegalArgumentException("Asignación duplicada o fuera de subred");
            for(int n:candidates) if(n<1||n>=((1L<<(32-prefix))-1))throw new IllegalArgumentException("Candidato fuera de subred");
            for(int n:reserved) if(n<1||n>=((1L<<(32-prefix))-1))throw new IllegalArgumentException("Reserva fuera de subred");
            this.revision=revision;this.prefix=prefix;this.services=Collections.unmodifiableList(new ArrayList<>(services));
            maintenanceCandidates=Collections.unmodifiableSet(new LinkedHashSet<>(candidates));this.reserved=Collections.unmodifiableSet(new HashSet<>(reserved));
        }
    }
    public final String ct, base;
    public final Profile profile;
    public final Integer transformers, remotes;
    public final Map<String,Presence> overrides;
    public AddressPlan(String ct,String input,Profile profile,Integer transformers,Integer remotes,Map<String,Presence> overrides) {
        if(ct==null||ct.isBlank()||profile==null)throw new IllegalArgumentException("Selecciona CT y perfil local revisado");
        Kind kind=kind(transformers,remotes);
        if(kind==Kind.UNKNOWN || (kind==Kind.NORMAL?28:26)!=profile.prefix)throw new IllegalArgumentException("Inventario o perfil sin confirmar");
        if(input==null)throw new IllegalArgumentException("Falta IP LAN base");
        String[] parts=input.trim().split("/",-1);
        if(parts.length>2 || (parts.length==2&&!parts[1].equals(Integer.toString(profile.prefix))))
            throw new IllegalArgumentException("La máscara del CT no coincide con el perfil");
        base=parts[0];long numeric=number(base);long first=numeric>>24;
        if(first==0||first==127||first>=224)throw new IllegalArgumentException("La IP LAN debe pertenecer a una red unicast válida");
        LanTargets.at(base,profile.prefix,1);
        this.ct=ct;this.profile=profile;this.transformers=transformers;this.remotes=remotes;
        this.overrides=Collections.unmodifiableMap(new HashMap<>(overrides));
    }
    public static Kind kind(Integer transformers,Integer remotes) {
        if((transformers!=null&&(transformers<1||transformers>100)) || (remotes!=null&&(remotes<0||remotes>100)))return Kind.UNKNOWN;
        if((transformers!=null&&transformers>2)||(remotes!=null&&remotes>2))return Kind.EXTENDED;
        return transformers==null||remotes==null?Kind.UNKNOWN:Kind.NORMAL;
    }
    public Presence presence(Service s) { return overrides.getOrDefault(s.id,s.presence(transformers,remotes)); }
    public String address(Service s) {return LanTargets.at(base,profile.prefix,s.offset);}
    public String mask() {return profile.prefix==28?String.join(".","255","255","255","240"):String.join(".","255","255","255","192");}
    public String candidate() {
        for(int n:profile.maintenanceCandidates)try{String ip=LanTargets.at(base,profile.prefix,n);validateMaintenance(ip);return ip;}catch(IllegalArgumentException ignored){}
        return "";
    }
    public void validateMaintenance(String ip) {
        int offset=offset(ip);
        if(profile.reserved.contains(offset))throw new IllegalArgumentException("Dirección reservada por el perfil local");
        for(Service s:profile.services) if(s.offset==offset&&presence(s)!=Presence.ABSENT)
            throw new IllegalArgumentException("La IP coincide con un servicio instalado o sin confirmar");
        // Unassigned does not mean free. Physical absence and authorized use are separately required at apply.
    }
    public int offset(String ip) {
        long delta=number(ip)-number(base);
        if(delta<1||delta>=((1L<<(32-profile.prefix))-1))throw new IllegalArgumentException("Dirección fuera de la subred o red/broadcast");
        return (int)delta;
    }
    public URI web(Service service) {
        if(presence(service)!=Presence.INSTALLED || service.scheme.isEmpty())throw new IllegalArgumentException("Servicio web o instalación sin confirmar");
        try{return new URI(service.scheme,null,address(service),service.port,"/",null,null);}catch(Exception e){throw new IllegalArgumentException("Destino web no válido");}
    }
    public static long number(String ip) {
        if(ip==null||!ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))throw new IllegalArgumentException("IPv4 no válida");
        long value=0;for(String part:ip.split("\\.")){int n=Integer.parseInt(part);if(n>255||(!part.equals("0")&&part.startsWith("0")))throw new IllegalArgumentException("IPv4 no canónica");value=(value<<8)|n;}return value;
    }
    public static boolean overlaps(String a,int ap,String b,int bp){int p=Math.min(ap,bp);if(p<1||ap>32||bp>32)throw new IllegalArgumentException("Prefijo inválido");return (number(a)>>(32-p))==(number(b)>>(32-p));}
}
