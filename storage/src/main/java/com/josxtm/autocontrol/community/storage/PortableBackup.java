package com.josxtm.autocontrol.community.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Password-derived portable AES-GCM backups. Authentication completes before restore begins. */
public final class PortableBackup {
    private static final byte[] MAGIC = "ACPB1".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    @FunctionalInterface public interface Export { void write(Writer output) throws Exception; }
    @FunctionalInterface public interface Restore { void read(Reader input) throws Exception; }
    private static Cipher cipher(int mode, char[] passphrase, byte[] salt, byte[] nonce) throws GeneralSecurityException {
        if (passphrase.length < 12 || passphrase.length > 256) throw new GeneralSecurityException("Contraseña de 12 a 256 caracteres requerida");
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, 210000, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(MAGIC); cipher.updateAAD(salt); cipher.updateAAD(nonce);
            return cipher;
        } finally { spec.clearPassword(); if (key != null) Arrays.fill(key, (byte) 0); }
    }
    private static InputStream bounded(InputStream input) {
        return new FilterInputStream(input) {
            private long total;
            @Override public int read() throws IOException { byte[] one = new byte[1]; return read(one,0,1) == -1 ? -1 : one[0] & 255; }
            @Override public int read(byte[] data, int off, int len) throws IOException {
                int count = in.read(data,off,len); if (count > 0 && (total += count) > MAX_BYTES + 64) throw new IOException("Límite de copia excedido"); return count;
            }
        };
    }
    public static void export(OutputStream output, char[] passphrase, Export source) throws Exception {
        byte[] salt = new byte[16], nonce = new byte[12]; SecureRandom random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, passphrase, salt, nonce);
            output.write(MAGIC); output.write(salt); output.write(nonce);
            try (CipherOutputStream encrypted = new CipherOutputStream(output, cipher)) {
                OutputStream limited = new FilterOutputStream(encrypted) {
                    private long total;
                    @Override public void write(int value) throws IOException { if (++total > MAX_BYTES) throw new IOException("Límite de copia excedido"); out.write(value); }
                    @Override public void write(byte[] data, int off, int len) throws IOException {
                        if ((total += len) > MAX_BYTES) throw new IOException("Límite de copia excedido"); out.write(data,off,len);
                    }
                };
                Writer text = new OutputStreamWriter(limited, StandardCharsets.UTF_8);
                source.write(new FilterWriter(text) { @Override public void close() throws IOException { flush(); } });
                text.flush();
            }
        } finally { Arrays.fill(passphrase, '\0'); }
    }
    private static byte[] exact(InputStream input, int count) throws IOException {
        byte[] result = new byte[count]; int read = 0;
        while (read < count) { int n = input.read(result,read,count-read); if (n < 0) throw new EOFException(); read += n; }
        return result;
    }
    public static void restore(InputStream input, char[] passphrase, File cacheDir, Restore target) throws Exception {
        File stage = null;
        try {
            if (!Arrays.equals(exact(input, MAGIC.length), MAGIC)) throw new IOException("Formato de copia no válido");
            byte[] salt = exact(input,16), nonce = exact(input,12);
            Cipher cipher = cipher(Cipher.DECRYPT_MODE,passphrase,salt,nonce);
            stage = File.createTempFile("restore-", ".tmp", cacheDir);
            try (InputStream decrypted = new CipherInputStream(bounded(input),cipher); OutputStream plain = new FileOutputStream(stage)) {
                byte[] buffer = new byte[8192]; long total = 0; int count;
                while ((count = decrypted.read(buffer)) != -1) {
                    if ((total += count) > MAX_BYTES) throw new IOException("Límite de copia excedido"); plain.write(buffer,0,count);
                }
            }
            // The tag is verified at EOF before any database mutation.
            try (Reader reader = new InputStreamReader(new FileInputStream(stage), StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT))) { target.read(reader); }
        } finally { Arrays.fill(passphrase, '\0'); if (stage != null && !stage.delete()) stage.deleteOnExit(); }
    }
    private PortableBackup() { }
}
