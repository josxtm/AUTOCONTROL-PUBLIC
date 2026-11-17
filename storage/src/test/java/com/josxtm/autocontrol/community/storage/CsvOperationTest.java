package com.josxtm.autocontrol.community.storage;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class CsvOperationTest {
    @Test public void cancellationClosesStreamAndPreventsCommit() throws Exception {
        CsvOperation operation = new CsvOperation();
        CountDownLatch closed = new CountDownLatch(1);
        operation.attach(closed::countDown);
        operation.cancel();
        assertTrue(closed.await(2, TimeUnit.SECONDS));
        try { operation.commit(() -> fail("Cancelled operation committed")); fail(); }
        catch (CsvOperation.Cancelled expected) { }
        try { operation.attach(() -> { }); fail(); } catch (CsvOperation.Cancelled expected) { }
        CsvOperation next = new CsvOperation(); next.check();
    }
    @Test public void completedCommitCannotBeReportedAsCancelled() throws Exception {
        CsvOperation operation = new CsvOperation();
        AtomicInteger commits = new AtomicInteger();
        operation.commit(commits::incrementAndGet);
        operation.cancel(); operation.check(); assertEquals(1, commits.get());
    }
    @Test public void progressCountsAllRowsAndSelectedCtAndThrottles() throws Exception {
        List<CsvOperation.Progress> updates = new ArrayList<>();
        CsvOperation operation = new CsvOperation(updates::add);
        operation.start(false, 0, 200);
        operation.bytes(150);
        String csv = "MATRICULA;NOMBRE\n28-SYN-A;a\n13-SYN-B;b\n45-SYN-C;c\n82-SYN-D;d\n";
        CsvCatalog.read(new StringReader(csv), "id", Collections.singleton("45"), operation, (row, field) -> { });
        CsvOperation.Progress last = updates.get(updates.size() - 1);
        assertEquals(4, last.read); assertEquals(1, last.selected); assertEquals(150, last.bytes);
        int before = updates.size();
        for (int i = 0; i < 1000; i++) operation.reading(i, i);
        assertTrue(updates.size() - before < 10);
    }
    @Test public void quotedEofAndBufferBoundaryAndSensitiveProjection() throws Exception {
        String label = "x".repeat(10000);
        StringBuilder source = new StringBuilder("MATRICULA;NOMBRE;CONTRASEÑA;CLAVE;TOKEN\r\n");
        for (int i = 0; i < 4; i++) source.append("28-SYN-").append(i).append(";\"").append(label).append("\";a;b;c\r\n");
        String csv = source.toString();
        CsvCatalog.Parsed parsed = CsvCatalog.read(new StringReader(csv), "id", (row, field) -> {
            Map<String, String> projected = GridMtColumns.project(row);
            assertEquals(label, projected.get("NOMBRE")); assertEquals(2, projected.size());
        });
        assertEquals(4, parsed.count);
    }
}
