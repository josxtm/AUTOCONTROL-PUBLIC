package com.josxtm.autocontrol.community.sniffer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure IPv4 helper. Addresses are supplied at runtime, never embedded in the APK. */
public final class LanTargets {
    private LanTargets() {}
    public static final Map<String, Integer> OFFSETS;
    static {
        LinkedHashMap<String,Integer> offsets = new LinkedHashMap<>();
        offsets.put("Router", 1);
        offsets.put("CC T1", 2);
        offsets.put("CC T2", 3);
        offsets.put("SABT T2", 9);
        offsets.put("SABT T1", 11);
        offsets.put("FFAA1", 13);
        offsets.put("Remota", 14);
        OFFSETS = java.util.Collections.unmodifiableMap(offsets);
    }
    private static long parse(String ip) {
        if (ip == null || !ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) throw new IllegalArgumentException("IPv4 inválida");
        String[] parts = ip.split("\\.");
        long n = 0;
        for (String part : parts) {
            int p = Integer.parseInt(part);
            if (p > 255) throw new IllegalArgumentException("Octeto fuera de rango");
            n = (n << 8) | p;
        }
        return n;
    }
    private static String format(long n) {
        return ((n >> 24) & 255) + "." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
    }
    /** Interprets the input as the network's base, never as a device address. */
    public static String at(String base, int prefix, int offset) {
        if (prefix < 1 || prefix > 30 || offset < 1 || offset > 254) throw new IllegalArgumentException("Prefijo o offset inválido");
        long network = parse(base);
        long mask = (0xffffffffL << (32-prefix)) & 0xffffffffL;
        if ((network & mask) != network) throw new IllegalArgumentException("La IP no es la base de la subred");
        long max = (~mask) & 0xffffffffL;
        if (offset >= max) throw new IllegalArgumentException("Equipo fuera del rango de hosts");
        return format(network + offset);
    }
    public static String at(String base, int offset) { return at(base, 24, offset); }
}
