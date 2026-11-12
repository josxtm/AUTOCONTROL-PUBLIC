package com.josxtm.autocontrol.community.storage;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.io.IOException;

public class CtFieldsTest {
    @Test public void normalizesWithoutRemovingInvalidSymbols() {
        assertEquals("28CM036",CtFields.normalize("28 cm 036"));
        assertEquals("45CQW9",CtFields.normalize("45\u00a0cqw9"));
        assertTrue(CtFields.valid("28SGTM"));
        for(String value:Arrays.asList("28","45","29ABC","28-ABC","28.ABC","28ÁBC"))assertFalse(value,CtFields.valid(value));
    }
    @Test public void retainsOriginalKeysAndValues() throws Exception {
        Map<String,String> row=new LinkedHashMap<>();row.put("MATRÍCULA","28 syn001");row.put("NOMBRE","Synthetic center");row.put("IP_LAN","synthetic LAN value");
        assertEquals("28SYN001",CtFields.registration(row));assertEquals("Synthetic center",CtFields.name(row));assertEquals("synthetic LAN value",CtFields.lan(row));
        assertEquals("28 syn001",row.get("MATRÍCULA"));assertEquals(3,row.size());
    }
    @Test public void aliasesMustAgreeAndUnknownIdentifiersAreNotGuessed() throws Exception {
        Map<String,String> row=new LinkedHashMap<>();row.put("id","28SYN001");assertNull(CtFields.registration(row));
        row.put("MATRICULA","28 syn001");row.put("CÓDIGO BDI","28SYN001");assertEquals("28SYN001",CtFields.registration(row));
        row.put("CÓDIGO BDI","45SYN002");try{CtFields.registration(row);fail("Ambiguous aliases accepted");}catch(IOException expected){}
    }
    @Test public void officialColumnAliasesAreExplicit() throws Exception {
        Map<String,String> row=new LinkedHashMap<>();row.put("MATRICULA [BDI]","45SYN001");row.put("NOMBRE [BDI]","Synthetic");row.put("IP LAN [EDIT]","original");
        assertEquals("45SYN001",CtFields.registration(row));assertEquals("Synthetic",CtFields.name(row));assertEquals("original",CtFields.lan(row));
        row.put("IP LAN","different");try{CtFields.lan(row);fail("Ambiguous LAN accepted");}catch(IOException expected){}
    }
}
