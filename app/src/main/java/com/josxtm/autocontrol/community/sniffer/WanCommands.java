package com.josxtm.autocontrol.community.sniffer;

/** Fixed scripts + validated scalars only; never execute text from a Grid cell or local JSON. */
final class WanCommands {
    static final String DIR="/tmp/autocontrol-mango-wan";
    static final String DISCOVER="set -eu\n. /lib/functions/network.sh\nnetwork_flush_cache\n"+
        "network_get_device wd wan || network_get_physdev wd wan\nnetwork_get_device ld lan\n"+
        "test -n \"$wd\" && test -n \"$ld\" && test \"$wd\" != \"$ld\"\n"+
        "printf 'WAN_DEVICE=%s\\nLAN_DEVICE=%s\\n' \"$wd\" \"$ld\"\n"+
        "printf 'CARRIER='; cat \"/sys/class/net/$wd/carrier\" 2>/dev/null || echo 0\n"+
        "echo WAN_ADDRESSES; ip -4 addr show dev \"$wd\"\necho LAN_ADDRESSES; ip -4 addr show dev \"$ld\"\n"+
        "echo WAN_ROUTES; ip -4 route show dev \"$wd\"\necho LEASE\n"+
        "if test -d "+DIR+"; then cat "+DIR+"/owner; cat "+DIR+"/address; cat "+DIR+"/device; fi\n";
    static String device(String dev){if(dev==null||!dev.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,31}"))throw new IllegalArgumentException("Interfaz WAN no válida");return "'"+dev+"'";}
    static String address(String ip,int prefix){AddressPlan.number(ip);if(prefix!=28&&prefix!=26)throw new IllegalArgumentException("Máscara no válida");return "'"+ip+"/"+prefix+"'";}
    static String lease(String id){if(id==null||!id.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Sesión WAN no válida");return "'"+id+"'";}
    static String probe(String dev,String ip) {
        AddressPlan.number(ip);String d=device(dev);
        return "d="+d+"\na='"+ip+"'\n"+
            "if ip -4 neigh show dev \"$d\" | grep -F \"$a \" | grep -q lladdr; then echo PROBE=conflict; exit 0; fi\n"+
            "if ping -c 1 -W 1 \"$a\" >/dev/null 2>&1; then echo PROBE=conflict; exit 0; fi\n"+
            "if command -v arping >/dev/null 2>&1; then\n"+
            " arping -D -c 2 -w 3 -I \"$d\" \"$a\" >/dev/null 2>&1; r=$?\n"+
            " case \"$r\" in 0) echo PROBE=clear-arp;; 1) echo PROBE=conflict;; *) echo PROBE=unknown;; esac\n"+
            "else echo PROBE=unknown; fi\n";
    }
    private static String mutex() {
        return "n=0\nwhile ! mkdir " + DIR + "-mutex 2>/dev/null; do n=$((n+1)); test \"$n\" -lt 50 || exit 2; sleep 0.1; done\n" +
            "trap 'rmdir " + DIR + "-mutex' EXIT\n";
    }
    static String restore(String dev,String ip,int prefix,String id) {
        String d=device(dev),a=address(ip,prefix),owner=lease(id);
        return "set -eu\n"+mutex()+"touch "+DIR+"-cancel-"+id+"\n"+"test -d "+DIR+" || exit 0\n"+
            "test \"$(cat "+DIR+"/owner)\" = "+owner+"\n"+
            "test \"$(cat "+DIR+"/address)\" = "+a+"\n"+
            "test \"$(cat "+DIR+"/device)\" = "+d+"\n"+
            "if ip -4 addr show dev "+d+" | grep -Fq 'inet "+ip+"/"+prefix+" '; then ip -4 addr del "+a+" dev "+d+"; fi\nrm -f "+DIR+"/owner "+DIR+"/address "+DIR+"/device\nrmdir "+DIR+"\n";
    }
    static String apply(String dev,String lan,String ip,int prefix,String id) {
        return apply(dev,lan,ip,prefix,id,java.util.Collections.emptyList());
    }
    static String apply(String dev,String lan,String ip,int prefix,String id,java.util.List<String> management) {
        StringBuilder guard=new StringBuilder();
        java.util.List<String> sorted=new java.util.ArrayList<>(management);java.util.Collections.sort(sorted);
        for(String entry:sorted) {
            String[] pair=entry.split("/");if(pair.length!=2)throw new IllegalArgumentException("Estado de gestión no válido");
            AddressPlan.number(pair[0]);int p=Integer.parseInt(pair[1]);if(p<1||p>32)throw new IllegalArgumentException("Máscara no válida");
        }
        if(!sorted.isEmpty()) guard.append("test \"$(ip -4 addr show dev ").append(device(lan)).append(" | awk '$1==\"inet\" {print $2}' | sort)\" = '").append(String.join("\n",sorted)).append("'\n");
        String d=device(dev),a=address(ip,prefix),owner=lease(id);device(lan);
        if(dev.equals(lan))throw new IllegalArgumentException("WAN coincide con gestión");
        String undo=restore(dev,ip,prefix,id);
        // Lease guard prevents an older watchdog removing an alias from a later session.
        String watchdog="sleep 600\n"+undo;
        return "set -eu\n"+mutex()+"test ! -e "+DIR+"-cancel-"+id+"\n. /lib/functions/network.sh\nnetwork_flush_cache\nnetwork_get_device actual_wan wan || network_get_physdev actual_wan wan\nnetwork_get_device actual_lan lan\n"+
            "test \"$actual_wan\" = "+d+" && test \"$actual_lan\" = "+device(lan)+" && test \"$actual_wan\" != \"$actual_lan\"\n"+
            "test \"$(cat /sys/class/net/\"$actual_wan\"/carrier)\" = 1\n"+
            guard.toString()+"command -v nohup >/dev/null 2>&1 || exit 127\n"+
            "if ip -4 addr show dev "+d+" | grep -Fq 'inet "+ip+"/'; then exit 2; fi\n"+
            "umask 077\nmkdir "+DIR+"\nprintf '%s\\n' "+owner+" > "+DIR+"/owner\nprintf '%s\\n' "+a+" > "+DIR+"/address\nprintf '%s\\n' "+d+" > "+DIR+"/device\n"+
            "nohup sh -c '"+watchdog.replace("'","'\\''")+"' </dev/null >/dev/null 2>&1 &\n"+
            "if ! ip -4 addr add "+a+" dev "+d+"; then rm -f "+DIR+"/owner "+DIR+"/address "+DIR+"/device; rmdir "+DIR+"; exit 2; fi\n"+
            "ip -4 route get '"+networkBase(ip,prefix)+"' | awk -v wanted="+d+" 'BEGIN {ok=0} {for(i=1;i<NF;i++) if($i==\"dev\" && $(i+1)==wanted) ok=1} END {exit !ok}'\n"+
            "echo APPLIED\n";
    }
    private static String networkBase(String ip,int prefix) {
        long n=AddressPlan.number(ip)&((0xffffffffL<<(32-prefix))&0xffffffffL);
        return ((n>>24)&255)+"."+((n>>16)&255)+"."+((n>>8)&255)+"."+(n&255);
    }
    private WanCommands(){}
}
