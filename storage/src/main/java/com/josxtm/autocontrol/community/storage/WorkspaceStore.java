package com.josxtm.autocontrol.community.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.JsonReader;
import android.util.JsonWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One private public-edition database; every replacement is a single transaction. */
public final class WorkspaceStore extends SQLiteOpenHelper {
    static final int SCHEMA = 1;
    static final int MAX_ROWS = 1000000;
    public WorkspaceStore(Context context) { this(context, "community-local.db"); }
    public WorkspaceStore(Context context, String name) { super(context, name, null, SCHEMA); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY REFERENCES records(id) ON DELETE CASCADE, body TEXT NOT NULL)");
        db.execSQL("CREATE TABLE journal (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, body TEXT NOT NULL)");
        db.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, data BLOB NOT NULL)");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Migración de esquema no disponible");
    }
    private void meta(SQLiteDatabase db, String key, String value) {
        ContentValues row = new ContentValues(); row.put("key", key); row.put("value", value);
        db.insertWithOnConflict("meta", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized String setting(String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }
    public synchronized void setting(String key, String value) { meta(getWritableDatabase(), key, value); }
    static String identifier(LinkedHashMap<String, String> record, String field) throws IOException {
        String id = record.get(field);
        if (id == null || id.trim().isEmpty() || id.length() > 256) throw JsonCodec.invalid();
        return id;
    }
    private void insertRecord(SQLiteDatabase db, LinkedHashMap<String, String> record, String field,
                              Set<String> fields) throws IOException {
        fields.addAll(record.keySet());
        if (fields.size() > JsonCodec.MAX_FIELDS) throw JsonCodec.invalid();
        ContentValues row = new ContentValues();
        row.put("id", identifier(record, field)); row.put("payload", JsonCodec.encode(record));
        db.insertOrThrow("records", null, row);
    }
    private void storeFields(SQLiteDatabase db, Set<String> fields) throws IOException {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String field : fields) map.put(field, "");
        meta(db, "fields", JsonCodec.encode(map));
    }
    public synchronized int importCatalog(Reader input, String field) throws IOException {
        if (field.isEmpty() || field.length() > 128) throw JsonCodec.invalid();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (JsonReader reader = JsonCodec.reader(input)) {
            db.delete("records", null, null); // Cascades notes, never touches independent journal.
            Set<String> fields = new LinkedHashSet<>();
            int count = 0;
            reader.beginArray();
            while (reader.hasNext()) {
                if (++count > MAX_ROWS) throw JsonCodec.invalid();
                insertRecord(db, JsonCodec.record(reader), field, fields);
            }
            reader.endArray(); JsonCodec.end(reader);
            if (count == 0) throw JsonCodec.invalid();
            storeFields(db, fields); meta(db, "identifier", field); meta(db, "visible", "");
            db.setTransactionSuccessful();
            return count;
        } finally { db.endTransaction(); }
    }

    /** A monthly CSV refresh preserves all existing records and their linked notes. */
    public static final class CsvUpdate {
        public final int rows, added, changed, unchanged, retained;
        public final String identifier;
        CsvUpdate(int rows, int added, int changed, int unchanged, int retained, String identifier) {
            this.rows = rows; this.added = added; this.changed = changed;
            this.unchanged = unchanged; this.retained = retained; this.identifier = identifier;
        }
    }

    public synchronized CsvUpdate updateCsvCatalog(Reader reader, String preferredField, boolean apply) throws IOException {
        return updateCsvCatalog(reader, preferredField, apply, CsvCatalog.defaultPrefixes());
    }

    public synchronized CsvUpdate updateCsvCatalog(Reader reader, String preferredField, boolean apply,
                                                    Set<String> selectedPrefixes) throws IOException {
        return updateCsvCatalog(reader, preferredField, apply, selectedPrefixes, new CsvOperation());
    }

    public synchronized CsvUpdate updateCsvCatalog(Reader reader, String preferredField, boolean apply,
            Set<String> selectedPrefixes, CsvOperation operation) throws IOException {
        final Set<String> prefixes = CsvCatalog.checkedPrefixes(selectedPrefixes);
        operation.check();
        SQLiteDatabase db = getReadableDatabase();
        CsvRefresh refresh = new CsvRefresh(db, apply, prefixes, operation);
        // Preview never starts a write transaction, creates a table, or writes metadata.
        if (apply) db.beginTransaction();
        Throwable failure = null;
        try {
            CsvCatalog.Parsed parsed = CsvCatalog.read(reader, preferredField, prefixes, operation,
                    (row, fromField) -> refresh.accept(row, fromField));
            refresh.flush();
            operation.verifyRows(parsed.count);
            if (apply) {
                storeFields(db, refresh.allFields);
                meta(db, "identifier", refresh.targetField);
                operation.commit(db::setTransactionSuccessful);
            }
            operation.publish(true);
            return new CsvUpdate(parsed.count, refresh.stats[0], refresh.stats[1], refresh.stats[2],
                    refresh.existingInScope - refresh.existingSeen, refresh.targetField);
        } catch (IOException | RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            try { refresh.close(); } finally {
                try { if (apply) db.endTransaction(); }
                catch (RuntimeException cleanup) {
                    // SQLITE_FULL can already have rolled SQLite back. Preserve the original
                    // failure instead of replacing it with "no transaction is active".
                    if (failure == null) throw cleanup;
                    failure.addSuppressed(cleanup);
                }
            }
        }
    }

    /** Bounded payload batches and reusable prepared writers; only matrícula IDs survive the stream. */
    private final class CsvRefresh implements java.io.Closeable {
        private static final int BATCH = 128; // below SQLite's parameter limit; <=32 MiB payload worst case
        final SQLiteDatabase db;
        final boolean apply;
        final CsvOperation operation;
        final Set<String> seen = new java.util.HashSet<>();
        final List<LinkedHashMap<String, String>> pending = new ArrayList<>(BATCH);
        final LinkedHashMap<String, String> canonical = new LinkedHashMap<>();
        final java.util.Map<String, String> normalized = new java.util.HashMap<>();
        final java.util.Map<String, Boolean> sensitive = new java.util.HashMap<>();
        final Set<String> allFields;
        final int[] stats = new int[3];
        final int existingInScope;
        int existingSeen;
        String targetField;
        GridMtColumns.Projection projection;
        android.database.sqlite.SQLiteStatement inserter, updater;
        CsvRefresh(SQLiteDatabase db, boolean apply, Set<String> prefixes, CsvOperation operation) throws IOException {
            this.db = db; this.apply = apply; this.operation = operation;
            allFields = new LinkedHashSet<>(fields());
            for (String field : allFields) canonical.put(norm(field), field);
            String old = setting("identifier");
            if (!old.isEmpty() && !CsvCatalog.normalized(GridMtColumns.base(old)).equals("MATRICULA")) {
                try (Cursor c = db.rawQuery("SELECT 1 FROM records LIMIT 1", null)) {
                    if (c.moveToFirst()) throw new IOException("El campo identificador cambió. Requiere migración controlada.");
                }
            }
            targetField = old.isEmpty() ? "MATRÍCULA" : old;
            canonical.put(norm("MATRÍCULA"), targetField);
            StringBuilder where = new StringBuilder();
            List<String> args = new ArrayList<>();
            for (String prefix : prefixes) {
                if (where.length() > 0) where.append(" OR ");
                where.append("(id >= ? AND id < ?)");
                args.add(prefix); args.add(Integer.toString(Integer.parseInt(prefix) + 1));
            }
            try (Cursor c = db.rawQuery("SELECT count(*) FROM records WHERE " + where, args.toArray(new String[0]))) {
                c.moveToFirst(); existingInScope = c.getInt(0);
            }
        }
        String norm(String key) { return normalized.computeIfAbsent(key, CsvCatalog::normalized); }
        void accept(LinkedHashMap<String, String> raw, String fromField) throws IOException {
            operation.check();
            String id = raw.get(fromField);
            if (!seen.add(id)) throw new IOException("Matrícula duplicada en el CSV.");
            if (projection == null) projection = new GridMtColumns.Projection(raw.keySet());
            LinkedHashMap<String, String> incoming = new LinkedHashMap<>();
            for (java.util.Map.Entry<String, String> entry : projection.apply(raw).entrySet())
                incoming.put(canonical.getOrDefault(norm(entry.getKey()), entry.getKey()), entry.getValue());
            incoming.put(targetField, id);
            pending.add(incoming);
            if (pending.size() == BATCH) flush();
        }
        void flush() throws IOException {
            if (pending.isEmpty()) return;
            operation.check();
            String[] ids = new String[pending.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = pending.get(i).get(targetField);
            String slots = String.join(",", java.util.Collections.nCopies(ids.length, "?"));
            java.util.Map<String, String> current = new java.util.HashMap<>();
            try (Cursor c = db.rawQuery("SELECT id,payload FROM records WHERE id IN (" + slots + ")", ids)) {
                while (c.moveToNext()) { operation.check(); current.put(c.getString(0), c.getString(1)); }
            }
            for (LinkedHashMap<String, String> incoming : pending) {
                operation.check();
                String id = incoming.get(targetField), payload = current.get(id);
                LinkedHashMap<String, String> previous = payload == null ? null : JsonCodec.decode(payload);
                LinkedHashMap<String, String> merged = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
                Set<String> columns = new java.util.HashSet<>();
                for (String key : incoming.keySet()) columns.add(norm(key));
                merged.keySet().removeIf(key -> columns.contains(norm(key)) || sensitive.computeIfAbsent(key, GridMtColumns::sensitive));
                merged.putAll(incoming);
                if (merged.size() > JsonCodec.MAX_FIELDS) throw JsonCodec.invalid();
                int total = 0;
                for (java.util.Map.Entry<String, String> entry : merged.entrySet()) {
                    total += entry.getKey().length() + (entry.getValue() == null ? 0 : entry.getValue().length());
                    if (total > 262144) throw JsonCodec.invalid();
                }
                allFields.addAll(merged.keySet());
                if (allFields.size() > JsonCodec.MAX_FIELDS) throw JsonCodec.invalid();
                boolean changed = previous != null && !previous.equals(merged);
                if (previous == null) stats[0]++;
                else { existingSeen++; stats[changed ? 1 : 2]++; }
                if (apply && (previous == null || changed)) {
                    // UPDATE in place: REPLACE would cascade-delete linked notes. Works on API 26 SQLite.
                    if (inserter == null) {
                        inserter = db.compileStatement("INSERT INTO records(payload,id) VALUES (?,?)");
                        updater = db.compileStatement("UPDATE records SET payload=? WHERE id=?");
                    }
                    android.database.sqlite.SQLiteStatement writer = previous == null ? inserter : updater;
                    writer.bindString(1, JsonCodec.encode(merged)); writer.bindString(2, id);
                    writer.execute(); writer.clearBindings();
                }
                operation.processed();
            }
            pending.clear();
        }
        @Override public void close() {
            if (inserter != null) inserter.close();
            if (updater != null) updater.close();
        }
    }

    public synchronized List<String> fields() throws IOException {
        String value = setting("fields");
        return value.isEmpty() ? new ArrayList<>() : new ArrayList<>(JsonCodec.decode(value).keySet());
    }
    public synchronized List<String> visibleFields() throws IOException {
        String visible = setting("visible");
        return visible.isEmpty() ? fields() : new ArrayList<>(JsonCodec.decode(visible).keySet());
    }
    public synchronized void visibleFields(List<String> chosen) throws IOException {
        if (!fields().containsAll(chosen)) throw JsonCodec.invalid();
        LinkedHashMap<String,String> value = new LinkedHashMap<>();
        for (String field : chosen) value.put(field, "");
        setting("visible", JsonCodec.encode(value));
    }
    public synchronized LinkedHashMap<String, String> find(String id) throws IOException {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM records WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? JsonCodec.decode(c.getString(0)) : null;
        }
    }
    public synchronized String note(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT body FROM notes WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }
    public synchronized void saveNote(String id, String body) throws IOException {
        validateBody(body);
        ContentValues row = new ContentValues(); row.put("id", id); row.put("body", body);
        getWritableDatabase().insertWithOnConflict("notes", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }
    static void validateBody(String body) throws IOException {
        if (body.length() > JsonCodec.MAX_TEXT) throw JsonCodec.invalid();
    }
    public synchronized void addJournal(String body) throws IOException {
        validateBody(body); if (body.trim().isEmpty()) throw JsonCodec.invalid();
        ContentValues row = new ContentValues(); row.put("time", System.currentTimeMillis()); row.put("body", body);
        getWritableDatabase().insertOrThrow("journal", null, row);
    }
    public static final class Entry {
        public final long id, time; public final String body;
        Entry(long id, long time, String body) { this.id = id; this.time = time; this.body = body; }
    }
    public synchronized List<Entry> journal(long before) {
        List<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,time,body FROM journal WHERE id<? ORDER BY id DESC LIMIT 25", new String[]{Long.toString(before)})) {
            while (c.moveToNext()) result.add(new Entry(c.getLong(0), c.getLong(1), c.getString(2)));
        }
        return result;
    }
    public synchronized void backup(Writer output) throws IOException {
        try (JsonWriter w = new JsonWriter(output)) {
            w.beginObject().name("format").value("autocontrol-community").name("version").value(SCHEMA);
            w.name("identifier").value(setting("identifier"));
            w.name("visible").value(setting("visible"));
            w.name("records").beginArray();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM records ORDER BY id", null)) {
                while (c.moveToNext()) JsonCodec.writeRecord(w, JsonCodec.decode(c.getString(0)));
            }
            w.endArray().name("notes").beginArray();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT id,body FROM notes ORDER BY id", null)) {
                while (c.moveToNext()) w.beginObject().name("id").value(c.getString(0)).name("body").value(c.getString(1)).endObject();
            }
            w.endArray().name("journal").beginArray();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT id,time,body FROM journal ORDER BY id", null)) {
                while (c.moveToNext()) w.beginObject().name("id").value(c.getLong(0)).name("time").value(c.getLong(1))
                        .name("body").value(c.getString(2)).endObject();
            }
            w.endArray().name("documents").beginArray();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,type FROM documents ORDER BY id", null)) {
                while (c.moveToNext()) {
                    w.beginObject().name("id").value(c.getString(0)).name("name").value(c.getString(1))
                        .name("type").value(c.getString(2)).name("chunks").beginArray();
                    byte[] data = documentData(c.getString(0));
                    for (int offset = 0; offset < data.length; offset += 9000)
                        w.value(android.util.Base64.encodeToString(data, offset, Math.min(9000, data.length - offset), android.util.Base64.NO_WRAP));
                    w.endArray().endObject();
                }
            }
            w.endArray().endObject();
        }
    }
    private static void name(JsonReader r, String expected) throws IOException {
        if (!expected.equals(r.nextName())) throw JsonCodec.invalid();
    }
    public synchronized void restore(Reader input) throws IOException {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try (JsonReader r = JsonCodec.reader(input)) {
            r.beginObject(); name(r, "format");
            if (!"autocontrol-community".equals(JsonCodec.text(r, 64))) throw JsonCodec.invalid();
            name(r, "version"); if (!Integer.toString(SCHEMA).equals(r.nextString())) throw JsonCodec.invalid();
            name(r, "identifier"); String field = JsonCodec.text(r, 128);
            name(r, "visible"); String visible = JsonCodec.text(r, JsonCodec.MAX_TEXT);
            db.delete("records", null, null); db.delete("journal", null, null); db.delete("meta", null, null); db.delete("documents", null, null);
            Set<String> fields = new LinkedHashSet<>();
            name(r, "records"); r.beginArray(); int count = 0;
            while (r.hasNext()) {
                if (++count > MAX_ROWS) throw JsonCodec.invalid();
                insertRecord(db, JsonCodec.record(r), field, fields);
            }
            r.endArray();
            if (!visible.isEmpty() && !fields.containsAll(JsonCodec.decode(visible).keySet())) throw JsonCodec.invalid();
            if (!fields.isEmpty()) storeFields(db, fields);
            meta(db, "identifier", field); meta(db, "visible", visible);
            name(r, "notes"); r.beginArray(); count = 0;
            while (r.hasNext()) {
                if (++count > MAX_ROWS) throw JsonCodec.invalid();
                r.beginObject(); name(r, "id"); String id = JsonCodec.text(r, 256);
                name(r, "body"); String body = JsonCodec.text(r, JsonCodec.MAX_TEXT); r.endObject();
                ContentValues row = new ContentValues(); row.put("id", id); row.put("body", body);
                db.insertOrThrow("notes", null, row);
            }
            r.endArray(); name(r, "journal"); r.beginArray(); count = 0;
            while (r.hasNext()) {
                if (++count > MAX_ROWS) throw JsonCodec.invalid();
                r.beginObject(); name(r, "id"); long id = r.nextLong();
                name(r, "time"); long time = r.nextLong(); name(r, "body"); String body = JsonCodec.text(r, JsonCodec.MAX_TEXT);
                r.endObject(); if (id <= 0 || time < 0 || body.trim().isEmpty()) throw JsonCodec.invalid();
                ContentValues row = new ContentValues(); row.put("id", id); row.put("time", time); row.put("body", body);
                db.insertOrThrow("journal", null, row);
            }
            r.endArray(); name(r, "documents"); r.beginArray(); count = 0;
            while (r.hasNext()) {
                if (++count > 200) throw JsonCodec.invalid();
                r.beginObject(); name(r, "id"); String id = JsonCodec.text(r, 64);
                name(r, "name"); String title = JsonCodec.text(r, 128);
                name(r, "type"); String type = JsonCodec.text(r, 128);
                name(r, "chunks"); r.beginArray(); java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
                while (r.hasNext()) {
                    byte[] chunk = android.util.Base64.decode(JsonCodec.text(r, 12000), android.util.Base64.NO_WRAP);
                    if (chunk.length > 9000 || data.size() + chunk.length > MAX_DOCUMENT) throw JsonCodec.invalid();
                    data.write(chunk);
                }
                r.endArray(); r.endObject();
                if (document(id) != null) throw JsonCodec.invalid();
                saveDocument(id, title, type, data.toByteArray());
            }
            r.endArray(); r.endObject(); JsonCodec.end(r); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public static final int MAX_DOCUMENT = 4 * 1024 * 1024;
    public static final class Document {
        public final String id, name, type; public final byte[] data;
        Document(String id, String name, String type, byte[] data) { this.id = id; this.name = name; this.type = type; this.data = data; }
    }
    public synchronized void saveDocument(String id, String title, String type, byte[] data) throws IOException {
        if (!id.matches("[A-Za-z0-9-]{1,64}") || title.trim().isEmpty() || title.length() > 128 || type.length() > 128
                || data.length > MAX_DOCUMENT) throw JsonCodec.invalid();
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor c = db.rawQuery("SELECT count(*),coalesce(sum(length(data)),0) FROM documents WHERE id<>?", new String[]{id})) {
            c.moveToFirst();
            if (c.getLong(0) >= 200 || c.getLong(1) + data.length > 16 * 1024 * 1024) throw JsonCodec.invalid();
        }
        ContentValues row = new ContentValues(); row.put("id", id); row.put("name", title); row.put("type", type); row.put("data", data);
        db.insertWithOnConflict("documents", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized Document document(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,type FROM documents WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? new Document(c.getString(0), c.getString(1), c.getString(2), documentData(id)) : null;
        }
    }
    private byte[] documentData(String id) {
        int length;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT length(data) FROM documents WHERE id=?", new String[]{id})) {
            if (!c.moveToFirst()) throw new IllegalStateException("Documento no disponible");
            length = c.getInt(0);
        }
        if (length < 0 || length > MAX_DOCUMENT) throw new IllegalStateException("Límite de documento");
        byte[] data = new byte[length];
        // Keep each SQLite CursorWindow comfortably below Android's per-row memory limit.
        for (int offset = 0; offset < length; offset += 262144) {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT substr(data,?,?) FROM documents WHERE id=?",
                    new String[]{Integer.toString(offset+1), "262144", id})) {
                if (!c.moveToFirst()) throw new IllegalStateException("Documento no disponible");
                byte[] chunk = c.getBlob(0); System.arraycopy(chunk,0,data,offset,chunk.length);
            }
        }
        return data;
    }
    public synchronized List<Document> documents() {
        List<Document> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,type FROM documents ORDER BY name,id LIMIT 200", null)) {
            while (c.moveToNext()) result.add(new Document(c.getString(0), c.getString(1), c.getString(2), null));
        }
        return result;
    }
    public synchronized void deleteDocument(String id) { getWritableDatabase().delete("documents", "id=?", new String[]{id}); }

}
