package com.josxtm.autocontrol.community.storage;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PortableBackupTest {
    private static char[] pass() { return "Synthetic-test-passphrase".toCharArray(); }
    private byte[] snapshot() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PortableBackup.export(output, pass(), writer -> writer.write("Synthetic portable α"));
        return output.toByteArray();
    }
    @Test public void authenticatedRoundTripAndPasswordErasure() throws Exception {
        byte[] encrypted = snapshot(); assertFalse(new String(encrypted,StandardCharsets.UTF_8).contains("Synthetic portable"));
        File folder = Files.createTempDirectory("synthetic-backup").toFile(); char[] pass = pass();
        try {
            PortableBackup.restore(new ByteArrayInputStream(encrypted),pass,folder,reader -> {
                StringBuilder text = new StringBuilder(); int c; while ((c=reader.read()) != -1) text.append((char)c);
                assertEquals("Synthetic portable α",text.toString());
            });
            for (char value:pass) assertEquals('\0',value);
            assertEquals(0,folder.list().length);
        } finally { folder.delete(); }
    }
    @Test public void randomSaltAndNonceProduceDifferentCopies() throws Exception { assertFalse(Arrays.equals(snapshot(),snapshot())); }
    @Test public void invalidPasswordTamperingAndTruncationNeverCallRestore() throws Exception {
        byte[] source = snapshot();
        for (int kind=0;kind<4;kind++) {
            byte[] broken = source.clone(); char[] phrase = pass();
            if (kind==0) phrase = "Another-synthetic-passphrase".toCharArray();
            if (kind==1) broken[broken.length-1] ^= 1;
            if (kind==2) broken = Arrays.copyOf(broken,broken.length-5);
            if (kind==3) broken[0] ^= 1;
            AtomicBoolean called = new AtomicBoolean(); File folder=Files.createTempDirectory("synthetic-backup").toFile();
            try {
                PortableBackup.restore(new ByteArrayInputStream(broken),phrase,folder,r -> called.set(true)); fail();
            } catch (Exception expected) { assertFalse(called.get()); }
            finally { for(char value:phrase) assertEquals('\0',value); assertEquals(0,folder.list().length); folder.delete(); }
        }
    }
    @Test public void shortPassphraseRejected() throws Exception {
        try { PortableBackup.export(new ByteArrayOutputStream(),"short".toCharArray(),w -> w.write("Synthetic")); fail(); }
        catch (java.security.GeneralSecurityException expected) { }
    }
}
