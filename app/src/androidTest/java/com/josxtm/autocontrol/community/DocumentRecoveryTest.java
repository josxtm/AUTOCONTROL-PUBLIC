package com.josxtm.autocontrol.community;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import com.josxtm.autocontrol.community.storage.*;
import java.io.*;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class DocumentRecoveryTest {
    @Test public void documentsAndWorkDataSurviveEncryptedRestoreAndBadTag() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("synthetic-recovery.db");
        try (WorkspaceStore store = new WorkspaceStore(context,"synthetic-recovery.db")) {
            store.importCatalog(new StringReader("[{\"id\":\"SYN-001\"}]"),"id");
            store.saveNote("SYN-001","Synthetic note"); store.addJournal("Synthetic diary");
            byte[] binary=new byte[3 * 1024 * 1024]; Arrays.fill(binary,(byte)42);
            store.saveDocument("SYN-DOC","Synthetic attachment","application/octet-stream",binary);
            ByteArrayOutputStream backup=new ByteArrayOutputStream();
            PortableBackup.export(backup,"Synthetic-passphrase".toCharArray(),store::backup);
            store.deleteDocument("SYN-DOC");
            PortableBackup.restore(new ByteArrayInputStream(backup.toByteArray()),"Synthetic-passphrase".toCharArray(),context.getCacheDir(),store::restore);
            assertArrayEquals(binary,store.document("SYN-DOC").data); assertEquals("Synthetic note",store.note("SYN-001"));
            byte[] damaged=backup.toByteArray(); damaged[damaged.length-1]^=1;
            try { PortableBackup.restore(new ByteArrayInputStream(damaged),"Synthetic-passphrase".toCharArray(),context.getCacheDir(),store::restore); fail(); }
            catch(Exception expected) { }
            assertArrayEquals(binary,store.document("SYN-DOC").data); assertEquals(1,store.journal(Long.MAX_VALUE).size());
            StringWriter snapshot=new StringWriter(); store.backup(snapshot);
            String invalid=snapshot.toString().replace("\"id\":\"SYN-DOC\"","\"id\":\"../bad\"");
            try { store.restore(new StringReader(invalid)); fail(); } catch(Exception expected) { }
            assertArrayEquals(binary,store.document("SYN-DOC").data);
        } finally { context.deleteDatabase("synthetic-recovery.db"); }
    }
}
