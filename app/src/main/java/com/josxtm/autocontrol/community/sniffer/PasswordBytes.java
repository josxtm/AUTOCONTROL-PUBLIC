package com.josxtm.autocontrol.community.sniffer;

import java.nio.*;
import java.nio.charset.*;
import java.util.Arrays;

final class PasswordBytes {
    interface Receiver { void accept(byte[] bytes) throws Exception; }
    static void use(char[] password, Receiver receiver) throws Exception {
        ByteBuffer buffer = null;
        byte[] encoded = null;
        try {
            buffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password));
            encoded = new byte[buffer.remaining()]; buffer.get(encoded);
            receiver.accept(encoded); // JSch 2.28.8 copies the byte array internally.
        } finally {
            Arrays.fill(password, '\0');
            if (encoded != null) Arrays.fill(encoded, (byte)0);
            if (buffer != null && buffer.hasArray()) Arrays.fill(buffer.array(), (byte)0);
        }
    }
    private PasswordBytes() { }
}
