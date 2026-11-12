package com.josxtm.autocontrol.community.storage;

import java.io.IOException;
import java.util.*;

/** Explicit aliases only: original keys/values remain in the stored payload. */
public final class CtFields {
    private static final Set<String> IDS=new HashSet<>(Arrays.asList("MATRÍCULA","MATRICULA","MATRICULA [BDI]"));
    private static final Set<String> NAMES=new HashSet<>(Arrays.asList("NOMBRE","NOMBRE [BDI]"));
    private static final Set<String> LANS=new HashSet<>(Arrays.asList("IP LAN","IP_LAN","IP LAN [EDIT]"));
    public static String normalize(String text){StringBuilder b=new StringBuilder();for(char c:Objects.toString(text,"").toCharArray())if(!Character.isWhitespace(c)&&!Character.isSpaceChar(c))b.append(c>='a'&&c<='z'?(char)(c-32):c);return b.toString();}
    public static boolean valid(String text){return normalize(text).matches("(28|45)[A-Z0-9]+");}
    public static String registration(Map<String,String> record)throws IOException{return alias(record,IDS,true);}
    public static String name(Map<String,String> record)throws IOException{return alias(record,NAMES,false);}
    public static String lan(Map<String,String> record)throws IOException{return alias(record,LANS,false);}
    private static String alias(Map<String,String> record,Set<String> keys,boolean id)throws IOException{
        String result=null;
        for(Map.Entry<String,String> entry:record.entrySet())if(keys.contains(entry.getKey().trim().toUpperCase(Locale.ROOT))&&entry.getValue()!=null&&!entry.getValue().trim().isEmpty()){
            String value=id?normalize(entry.getValue()):entry.getValue().trim();
            if(result!=null&&!result.equals(value))throw new IOException("Alias de catálogo ambiguos");result=value;
        }return result;
    }
    private CtFields(){}
}
