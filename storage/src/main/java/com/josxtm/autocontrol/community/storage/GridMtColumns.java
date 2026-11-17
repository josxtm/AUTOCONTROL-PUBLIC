package com.josxtm.autocontrol.community.storage;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

/**
 * Minimal technical view of the user's own eSir export.
 * No operational values or actual site data are bundled with the application.
 * Unknown columns stay in the user-supplied CSV, not in source or logs.
 */
public final class GridMtColumns {
    // More than the former 15 fields, but avoid storing hundreds of sparse telemetry
    // columns on every CT. Additions must be deliberate to keep local backup manageable.
    private static final String FIELD_NAMES =
        "MATRICULA|NOMBRE|CENTRORESPONSABLE|CODIGO|NTRAFOS|NSALIDAS|TIPOCENTRO|TIPOEDIFICIO|" +
        "PROPIEDAD|BBDD|PROVINCIA|MUNICIPIO|CALLE|PORTAL|CP|X|Y|ALIMENTACION|PINSTKVA|" +
        "NCLIENTES|TELEMANDOINST|TELEMANDOACT|ESTADOTG|ESTADOTC|FECHAPESTG|FECHADC|" +
        "FECHAINSTALACIONTC|FECHAEXPLOTACIONTC|ARMARIOGCTTIPO|EQCOMFABRICANTE|EQCOMMODELO|" +
        "EQCOMNSERIE|EQCOMOPERADOR1|EQCOMNSIM1|EQCOMOPERADOR2|EQCOMNSIM2|" +
        "EQCOMTCFABRICANTE|EQCOMTCMODELO|EQCOMTCNSERIE|POTENCIAMOVISTAR|POTENCIAORANGE|" +
        "EQCOMMODELOANTENA|EQCOMTGTIPOANTENAALTAGANANCIA|EQCOMTGUBICACIONANTENA|" +
        "EQCOMTG|ARMARIOGCTUBICACION|FABRICANTEGCT|T1MODELOCC|T1NSERIECC|" +
        "T1NSERIESBT|T1MATRICULA|TI1BTRELTRANSFORMACION|T2MODELOCC|T2NSERIECC|" +
        "T2NSERIESBT|T2MATRICULA|TI2BTRELTRANSFORMACION|" +
        "FABATN|FA1FABRICANTE|FA1MODELO|FA1NSERIE|FA1VERSIONFW|" +
        "BAT1CAPACIDAD|BAT1FABRICANTE|BAT1MODELO|BAT1FECHA|" +
        "BAT2CAPACIDAD|BAT2FABRICANTE|BAT2MODELO|BAT2FECHA|" +
        "RTUN|RTU1FABRICANTE|RTU1MODELO|RTU1NSERIE|RTU1VERSIONFW|" +
        "IPLAN|IPWAN|IPTC|APNTC|TECNOLOGIATC|CRITICIDADTG|CRITICIDADTC|" +
        "GST|CPU|TIPORFIS|CAA|IPFA1|IPFA2|POTENCIANOMINALT1|POTENCIANOMINALT2|" +
        "CNCT1|CNCT2|IPT1|IPT2|ESTADOCNCT1|ESTADOCNCT2|SBTT1|SBTT2|ESTADOSBTT1|ESTADOSBTT2";
    private static final Set<String> SELECTED = new HashSet<>();
    static {
        for (String entry : FIELD_NAMES.split("\\|")) SELECTED.add(entry);
    }

    public static String base(String sourceHeader) {
        return sourceHeader.replaceFirst("\\s*\\[[^\\]]+\\]\\s*$", "").trim();
    }
    private static String normalizedBase(String sourceHeader) {
        return CsvCatalog.normalized(base(sourceHeader));
    }
    static boolean sensitive(String sourceHeader) {
        String key = CsvCatalog.normalized(sourceHeader);
        return key.contains("CONTRAS") || key.contains("PASSWORD") || key.contains("PASSWD") ||
                key.contains("CLAVE") || key.contains("CREDENTIAL") || key.contains("SECRET") ||
                key.contains("TOKEN") || key.contains("APIKEY");
    }

    public static LinkedHashMap<String, String> project(LinkedHashMap<String, String> source) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (java.util.Map.Entry<String, String> entry : source.entrySet()) {
            String rawName = entry.getKey();
            if (sensitive(rawName)) continue; // Never retain any password / secret field.
            String key = normalizedBase(rawName);
            if (!SELECTED.contains(key)) continue;
            String name;
            switch (key) {
                case "MATRICULA": name = "MATRÍCULA"; break;
                case "IPLAN": name = "IP LAN"; break;
                case "IPWAN": name = "IP WAN"; break;
                case "IPTC":
                    name = rawName.toUpperCase(Locale.ROOT).contains("[CAL]") ? "IP TC (CAL)" : "IP TC";
                    break;
                case "CODIGO": name = "CÓDIGO BDI"; break;
                case "TIPORFIS": name = "TIPO RFID"; break;
                default: name = base(rawName);
            }
            // Prefer the first occurrence for a duplicate label; never silently rename IDs.
            result.putIfAbsent(name, entry.getValue());
        }
        return result;
    }

    private GridMtColumns() { }
}
