package com.josxtm.autocontrol.community.storage;

import android.content.Context;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Only generated synthetic data. Shared by host native-SQLite tests and real Android CI. */
public abstract class CsvImportScenarios {
    protected abstract Context context();
    private WorkspaceStore store;
    private File grid;
    private static final Set<String> ALL = new LinkedHashSet<>(Arrays.asList("28", "45", "82"));
    @Before public void setUpCsv() throws Exception {
        context().deleteDatabase("synthetic-grid-benchmark.db");
        store = new WorkspaceStore(context(), "synthetic-grid-benchmark.db");
        grid = new File(context().getCacheDir(), "synthetic-grid-test.tmp");
        seed();
    }
    @After public void tearDownCsv() {
        store.close(); context().deleteDatabase("synthetic-grid-benchmark.db"); grid.delete();
    }
    private void seed() throws Exception {
        store.importCatalog(new StringReader("[{\"MATRÍCULA\":\"28-SYN-0\",\"NOMBRE\":\"old\",\"MANUAL\":\"keep\"}," +
            "{\"MATRÍCULA\":\"45-HIST\",\"NOMBRE\":\"history\"},{\"MATRÍCULA\":\"13-HIST\",\"NOMBRE\":\"outside\"}]"), "MATRÍCULA");
        store.saveNote("28-SYN-0", "field note"); store.saveNote("45-HIST", "historic note");
        store.addJournal("independent diary");
        store.saveDocument("synthetic-doc", "test", "text/plain", new byte[]{65, 66});
        store.setting("visible", "{\"NOMBRE\":\"\"}"); store.setting("synthetic-theme", "9");
    }
    private String backup() throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        OutputStream sink = new OutputStream() {
            @Override public void write(int value) { }
            @Override public void write(byte[] value, int offset, int length) { }
        };
        try (Writer output = new BufferedWriter(new OutputStreamWriter(
                new java.security.DigestOutputStream(sink, digest), StandardCharsets.UTF_8), 32768)) {
            store.backup(output);
        }
        return java.util.Arrays.toString(digest.digest());
    }
    private static String prefix(int i) { return i < 13153 ? "28" : i < 19901 ? "45" : i < 19948 ? "82" : "13"; }
    private void generate(int rows) throws Exception {
        // Exactly 364 columns: all reviewed technical columns, sensitive traps, then ignored synthetic columns.
        java.lang.reflect.Field selection = GridMtColumns.class.getDeclaredField("FIELD_NAMES");
        selection.setAccessible(true);
        List<String> headers = new ArrayList<>(Arrays.asList(((String) selection.get(null)).split("\\|")));
        headers.remove("MATRICULA"); headers.add(0, "MATRICULA [BDI]");
        headers.addAll(Arrays.asList("CONTRASEÑAS GCT [EDIT]", "PASSWORD", "API KEY", "TOKEN", "CLAVE", "CREDENTIAL"));
        while (headers.size() < 364) headers.add("SYNTHETIC_COLUMN_" + headers.size());
        assertEquals(364, headers.size());
        try (Writer out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(grid)), StandardCharsets.UTF_8))) {
            out.write(String.join(";", headers)); out.write('\n');
            for (int i = 0; i < rows; i++) {
                out.write(prefix(i) + "-SYN-" + i);
                for (int c = 1; c < 364; c++) out.write(";syn");
                out.write('\n');
            }
        }
    }
    private WorkspaceStore.CsvUpdate run(boolean apply, Set<String> prefixes, CsvOperation operation) throws Exception {
        try (Reader reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(grid)), StandardCharsets.UTF_8)) {
            return store.updateCsvCatalog(reader, "MATRÍCULA", apply, prefixes, operation);
        }
    }
    private void preserved() throws Exception {
        assertEquals("field note", store.note("28-SYN-0"));
        assertEquals("historic note", store.note("45-HIST"));
        assertEquals("keep", store.find("28-SYN-0").get("MANUAL"));
        assertEquals("history", store.find("45-HIST").get("NOMBRE"));
        assertEquals("outside", store.find("13-HIST").get("NOMBRE"));
        assertEquals(1, store.journal(Long.MAX_VALUE).size());
        assertArrayEquals(new byte[]{65, 66}, store.document("synthetic-doc").data);
        assertEquals("9", store.setting("synthetic-theme"));
        assertEquals(Collections.singletonList("NOMBRE"), store.visibleFields());
    }
    @Test public void fullGridPreviewApplyRepeatAndSingleZone() throws Exception {
        generate(70000);
        String before = backup();
        long changes = android.database.DatabaseUtils.longForQuery(store.getReadableDatabase(), "SELECT total_changes()", null);
        long start = System.nanoTime();
        WorkspaceStore.CsvUpdate preview = run(false, ALL, new CsvOperation());
        long previewMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(19948, preview.rows); assertEquals(19947, preview.added); assertEquals(1, preview.changed);
        assertEquals(before, backup());
        assertEquals(changes, android.database.DatabaseUtils.longForQuery(store.getReadableDatabase(), "SELECT total_changes()", null));
        assertFalse(store.getReadableDatabase().inTransaction());
        start = System.nanoTime();
        WorkspaceStore.CsvUpdate applied = run(true, ALL, new CsvOperation());
        long applyMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(preview.added, applied.added); assertEquals(1, applied.retained);
        preserved();
        assertEquals("syn", store.find("82-SYN-19947").get("IP LAN"));
        for (String key : store.find("28-SYN-0").keySet()) assertFalse(GridMtColumns.sensitive(key));
        for (String key : store.fields()) {
            assertFalse(key.startsWith("SYNTHETIC_COLUMN_")); assertFalse(GridMtColumns.sensitive(key));
        }
        String after = backup();
        start = System.nanoTime();
        WorkspaceStore.CsvUpdate repeated = run(true, ALL, new CsvOperation());
        long repeatMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(19948, repeated.unchanged); assertEquals(0, repeated.changed); assertEquals(after, backup());
        Set<String> only45 = Collections.singleton("45");
        start = System.nanoTime();
        WorkspaceStore.CsvUpdate single = run(false, only45, new CsvOperation());
        long singleMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(6748, single.rows); assertEquals(after, backup());
        run(true, only45, new CsvOperation()); assertEquals(after, backup()); preserved();
        System.out.println("GRID_BENCHMARK rows=70000 columns=364 selected=19948 gzipBytes=" + grid.length() +
            " previewMs=" + previewMs + " applyMs=" + applyMs + " repeatMs=" + repeatMs + " singleZonePreviewMs=" + singleMs);
    }
    @Test public void cancellationDuringReadAndWriteRollsBackAndCanRestart() throws Exception {
        generate(70000);
        String before = backup();
        // Force a progress boundary exactly after meaningful background work, without timing races.
        for (boolean writing : new boolean[]{false, true}) {
            final CsvOperation[] op = new CsvOperation[1];
            final long[] cancelledAt = {0};
            long changesBefore = android.database.DatabaseUtils.longForQuery(store.getReadableDatabase(), "SELECT total_changes()", null);
            op[0] = new CsvOperation(p -> {
                if (cancelledAt[0] == 0 && ((!writing && p.read >= 512) || (writing && p.processed >= 256))) {
                    cancelledAt[0] = System.nanoTime(); op[0].cancel();
                }
            });
            Reader reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(grid)), StandardCharsets.UTF_8) {
                @Override public int read(char[] chars, int offset, int length) throws IOException {
                    int n = super.read(chars, offset, length); op[0].publish(true); return n;
                }
            };
            try (Reader owned = reader) {
                try { store.updateCsvCatalog(owned, "MATRÍCULA", writing, ALL, op[0]); fail("Cancellation ignored"); }
                catch (CsvOperation.Cancelled expected) { }
            }
            assertTrue(cancelledAt[0] > 0);
            System.out.println("GRID_CANCEL writing=" + writing + " completionMs=" + (System.nanoTime() - cancelledAt[0]) / 1_000_000);
            if (writing) assertTrue(android.database.DatabaseUtils.longForQuery(store.getReadableDatabase(), "SELECT total_changes()", null) > changesBefore);
            assertEquals(before, backup()); assertFalse(store.getReadableDatabase().inTransaction()); preserved();
        }
        assertEquals(19948, run(true, ALL, new CsvOperation()).rows); preserved();
    }
    @Test public void corruptGzipLateFailureNeverCommits() throws Exception {
        generate(700);
        try (RandomAccessFile file = new RandomAccessFile(grid, "rw")) { file.setLength(file.length() - 5); }
        String before = backup();
        for (boolean apply : new boolean[]{false, true}) {
            try { run(apply, ALL, new CsvOperation()); fail("Truncated GZIP accepted"); }
            catch (IOException expected) { }
            assertEquals(before, backup()); preserved();
        }
    }
    @Test public void databaseFailureAndFullDiskKeepPreviousData() throws Exception {
        generate(700);
        String before = backup();
        android.database.sqlite.SQLiteDatabase db = store.getWritableDatabase();
        db.execSQL("CREATE TEMP TRIGGER synthetic_abort BEFORE INSERT ON records WHEN NEW.id='28-SYN-300' " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END");
        try {
            try { run(true, ALL, new CsvOperation()); fail("Database failure ignored"); }
            catch (android.database.sqlite.SQLiteException expected) { }
        } finally { db.execSQL("DROP TRIGGER synthetic_abort"); }
        assertEquals(before, backup()); preserved();
        long maximum = android.database.DatabaseUtils.longForQuery(db, "PRAGMA max_page_count", null);
        long pages = android.database.DatabaseUtils.longForQuery(db, "PRAGMA page_count", null);
        android.database.DatabaseUtils.longForQuery(db, "PRAGMA max_page_count=" + pages, null);
        try {
            try { run(true, ALL, new CsvOperation()); fail("Full database accepted"); }
            catch (android.database.sqlite.SQLiteFullException expected) { }
        } finally { android.database.DatabaseUtils.longForQuery(db, "PRAGMA max_page_count=" + maximum, null); }
        assertEquals(before, backup()); preserved();
        assertEquals(700, run(true, ALL, new CsvOperation()).rows);
    }
    @Test public void changedRowCountSincePreviewRollsBack() throws Exception {
        generate(700);
        String before = backup();
        CsvOperation operation = new CsvOperation(); operation.start(true, 600, -1);
        try { run(true, ALL, operation); fail("Changed input accepted"); } catch (IOException expected) { }
        assertEquals(before, backup());
    }
    @Test public void duplicateAcrossBatchesAndIncompatibleHeadersAreAtomic() throws Exception {
        StringBuilder csv = new StringBuilder("MATRICULA;NOMBRE;CLAVE\n");
        for (int i = 0; i < 300; i++) csv.append("28-SYN-").append(i).append(";syn;untrusted\n");
        csv.append("28-SYN-0;syn;untrusted\n");
        String before = backup();
        for (boolean apply : new boolean[]{false, true}) {
            for (String invalid : Arrays.asList(csv.toString(), "WRONG;NOMBRE\n28-SYN-0;syn\n")) {
                try { store.updateCsvCatalog(new StringReader(invalid), "MATRÍCULA", apply, ALL); fail("Invalid CSV accepted"); }
                catch (IOException expected) { }
                assertEquals(before, backup());
            }
        }
    }
}
