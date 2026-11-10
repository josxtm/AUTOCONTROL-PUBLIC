package com.josxtm.autocontrol.community.sniffer;

import java.util.Locale;

/** Ephemeral history is deliberately conservative: never persist it to disk. */
public final class CommandHistoryPolicy {
    private CommandHistoryPolicy() {}
    public static boolean rememberable(String command) {
        if (command == null || command.isBlank() || command.length() > 200) return false;
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) return false;
        if (!command.equals(command.trim())) return false;
        String value = command.toLowerCase(Locale.ROOT);
        for (String hint : new String[]{"password", "passwd", "token", "secret", "api_key",
             "apikey", "authorization", "bearer", "sshpass", "private_key", "private-key",
             "credential", "set -x", "export "}) {
            if (value.contains(hint)) return false;
        }
        return true;
    }
}
