package com.josxtm.autocontrol.community.documents;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Inert attachments plus plain UTF-8 editing. No macros, templates or external interpreters. */
public final class DocumentEngine {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final int MAX_EDITABLE = 16000;
    public static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) throw new IOException("Documento demasiado grande"); output.write(buffer,0,count);
        }
        return output.toByteArray();
    }
    public static String editable(byte[] data) {
        if (data.length > MAX_EDITABLE * 4) return null;
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
            if (text.length() > MAX_EDITABLE || text.indexOf('\0') >= 0) return null;
            for (int i=0;i<text.length();i++) if (Character.isISOControl(text.charAt(i)) && "\n\r\t".indexOf(text.charAt(i))<0) return null;
            return text;
        } catch (CharacterCodingException invalid) { return null; }
    }
    public static byte[] encode(String text) throws IOException {
        if (text.length() > MAX_EDITABLE) throw new IOException("Texto demasiado largo");
        return text.getBytes(StandardCharsets.UTF_8);
    }
    private DocumentEngine() { }
}
