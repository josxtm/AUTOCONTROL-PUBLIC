package com.josxtm.autocontrol.community.storage;

import android.util.*;
import java.io.*;
import java.util.*;

/** Two verified formats: scalar object array; versioned export with 17 summary columns. */
final class CatalogReader {
    interface Sink {void accept(LinkedHashMap<String,String> row,String field)throws IOException;}
    static final class Report {int accepted,rejected;String identifier;}
    static Report read(Reader input,String requested,Sink sink)throws IOException{
        Report report=new Report();
        // Accept a UTF-8 BOM emitted by common spreadsheet/JSON export tools.
        PushbackReader cleaned=new PushbackReader(input,1);
        int first=cleaned.read();
        if(first!=-1 && first!='\uFEFF')cleaned.unread(first);
        try(JsonReader r=JsonCodec.reader(cleaned)){
            if(r.peek()==JsonToken.BEGIN_ARRAY){
                r.beginArray();while(r.hasNext()){
                    LinkedHashMap<String,String> row=JsonCodec.record(r);
                    String registration=CtFields.registration(row);String field=registration!=null?"@ct":requested;
                    if(report.identifier!=null&&!report.identifier.equals(field))throw new IOException("Identificadores incompatibles entre filas");
                    report.identifier=field;
                    if(registration!=null&&!CtFields.valid(registration)){report.rejected++;continue;}
                    CtFields.name(row);CtFields.lan(row);sink.accept(row,field);report.accepted++;
                    if(report.accepted+report.rejected>WorkspaceStore.MAX_ROWS)throw JsonCodec.invalid();
                }r.endArray();
            }else{
                r.beginObject();Set<String> root=new HashSet<>();List<String> labels=new ArrayList<>();boolean format=false,version=false,rows=false;
                while(r.hasNext()){
                    String key=r.nextName();if(!root.add(key))throw JsonCodec.invalid();
                    switch(key){
                        case "format":format="TELECONTROL_CT_V1".equals(JsonCodec.text(r,64));if(!format)throw JsonCodec.invalid();break;
                        case "version":version=r.peek()==JsonToken.NUMBER&&r.nextInt()==1;if(!version)throw JsonCodec.invalid();break;
                        case "fieldNames":r.beginArray();while(r.hasNext()){String label=JsonCodec.text(r,128);if(labels.contains(label)||labels.size()>=480||label.startsWith("__summary_"))throw JsonCodec.invalid();labels.add(label);}r.endArray();break;
                        case "records":
                            if(!format||!version||!labels.contains("MATRICULA [BDI]")||!labels.contains("NOMBRE [BDI]"))throw JsonCodec.invalid();
                            report.identifier="@ct";rows=true;r.beginObject();Set<String> keys=new HashSet<>();
                            while(r.hasNext()){
                                String id=r.nextName();if(!keys.add(id)||keys.size()>WorkspaceStore.MAX_ROWS)throw JsonCodec.invalid();
                                List<String> values=new ArrayList<>();r.beginArray();while(r.hasNext()){
                                    if(values.size()>=labels.size()+17)throw JsonCodec.invalid();
                                    if(values.size()==15||values.size()==16){if(r.peek()!=JsonToken.NUMBER)throw JsonCodec.invalid();values.add(r.nextString());}
                                    else values.add(JsonCodec.text(r,JsonCodec.MAX_TEXT));
                                }r.endArray();if(values.size()!=labels.size()+17)throw JsonCodec.invalid();
                                LinkedHashMap<String,String> record=new LinkedHashMap<>();for(int i=0;i<17;i++)record.put("__summary_"+i,values.get(i));for(int i=0;i<labels.size();i++)record.put(labels.get(i),values.get(17+i));
                                String registration=CtFields.registration(record);
                                if(!CtFields.normalize(id).equals(registration)||!CtFields.normalize(values.get(0)).equals(registration)||!Objects.equals(values.get(1),CtFields.name(record)))throw new IOException("Cabecera y columnas no corresponden");
                                if(!CtFields.valid(registration)){report.rejected++;continue;}
                                CtFields.lan(record);sink.accept(record,"@ct");report.accepted++;
                            }r.endObject();break;
                        default:throw new IOException("Esquema de catálogo no verificado");
                    }
                }r.endObject();if(!rows)throw JsonCodec.invalid();
            }
            JsonCodec.end(r);if(report.accepted==0)throw new IOException("Catálogo sin registros válidos");return report;
        }
    }
}
