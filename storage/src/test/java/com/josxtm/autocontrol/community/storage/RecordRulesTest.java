package com.josxtm.autocontrol.community.storage;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.LinkedHashMap;

public final class RecordRulesTest {
    @Test public void exactIdentifierPreservesLeadingZerosAndWhitespace() throws Exception {
        LinkedHashMap<String, String> record = new LinkedHashMap<>();
        record.put("syntheticCode", "001"); assertEquals("001", WorkspaceStore.identifier(record, "syntheticCode"));
        record.put("syntheticCode", " SYN-A "); assertEquals(" SYN-A ", WorkspaceStore.identifier(record, "syntheticCode"));
    }
    @Test public void missingBlankNullOrOversizedIdentifierRejected() throws Exception {
        LinkedHashMap<String, String> record = new LinkedHashMap<>();
        for (String invalid : new String[]{null, "", "  ", "X".repeat(257)}) {
            record.put("id", invalid);
            try { WorkspaceStore.identifier(record, "id"); fail(); } catch (IOException expected) { }
        }
        try { WorkspaceStore.identifier(record, "other"); fail(); } catch (IOException expected) { }
    }
    @Test public void noteLimitAllowsEmptyAndUnicodeAndRejectsOversized() throws Exception {
        WorkspaceStore.validateBody(""); WorkspaceStore.validateBody("Synthetic α\n");
        WorkspaceStore.validateBody("x".repeat(JsonCodec.MAX_TEXT));
        try { WorkspaceStore.validateBody("x".repeat(JsonCodec.MAX_TEXT + 1)); fail(); } catch (IOException expected) { }
    }
}
