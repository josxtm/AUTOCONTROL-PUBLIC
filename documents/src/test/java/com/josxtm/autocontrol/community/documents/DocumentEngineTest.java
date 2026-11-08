package com.josxtm.autocontrol.community.documents;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class DocumentEngineTest {
    @Test public void textRoundTripAndBinaryIsInert() throws Exception {
        byte[] text=DocumentEngine.encode("Synthetic α\ntext");
        assertEquals("Synthetic α\ntext", DocumentEngine.editable(DocumentEngine.read(new ByteArrayInputStream(text))));
        assertNull(DocumentEngine.editable(new byte[]{0,1,2}));
        assertNull(DocumentEngine.editable(new byte[]{(byte)0xff}));
    }
    @Test public void oversizedInputRejectedAndLongTextRemainsAttachment() throws Exception {
        assertNull(DocumentEngine.editable("x".repeat(DocumentEngine.MAX_EDITABLE+1).getBytes(StandardCharsets.UTF_8)));
        try { DocumentEngine.encode("x".repeat(DocumentEngine.MAX_EDITABLE+1)); fail(); } catch(IOException expected) { }
        InputStream stream=new InputStream() {
            private int count;
            @Override public int read() { return count++ > DocumentEngine.MAX_BYTES ? -1 : 'X'; }
        };
        try { DocumentEngine.read(stream); fail(); } catch(IOException expected) { }
    }
}
