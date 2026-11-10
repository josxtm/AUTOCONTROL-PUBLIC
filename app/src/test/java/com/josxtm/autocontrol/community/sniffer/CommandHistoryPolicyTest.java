package com.josxtm.autocontrol.community.sniffer;

import static org.junit.Assert.*;
import org.junit.Test;

public final class CommandHistoryPolicyTest {
    @Test public void retainsOrdinaryCommands() {
        assertTrue(CommandHistoryPolicy.rememberable("whoami"));
        assertTrue(CommandHistoryPolicy.rememberable("ping -c 1 -W 2 " + String.join(".", "198","51","100","7")));
    }
    @Test public void blocksLikelySecrets() {
        for (String cmd : new String[]{"export MY_PASSWORD=synthetic", "token=synthetic",
            "sshpass -p synthetic ssh test", "echo bearer synthetic", " ", "date\nwhoami"}) {
            assertFalse(cmd, CommandHistoryPolicy.rememberable(cmd));
        }
    }
}
