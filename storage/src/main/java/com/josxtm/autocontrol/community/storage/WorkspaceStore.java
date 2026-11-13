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
        SQLiteDatabase db = getWritableDatabase();
        String oldIdentifier = setting("identifier");
        final int[] stats = new int[3]; // new, modified, unchanged
        final LinkedHashMap<String, String> canonical = new LinkedHashMap<>();
        for (String name : fields()) canonical.put(CsvCatalog.normalized(name), name);
        final Set<String> allFields = new LinkedHashSet<>(fields());
        final String[] targetField = {null};
        db.beginTransaction();
        try {
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS csv_refresh_seen (id TEXT PRIMARY KEY NOT NULL)");
            db.execSQL("DELETE FROM csv_refresh_seen");
            CsvCatalog.Parsed parsed = CsvCatalog.read(reader, preferredField, (raw, fromField) -> {
                String id = raw.get(fromField);
                if (targetField[0] == null) {
                    String old = oldIdentifier == null ? "" : oldIdentifier;
                    if (!old.isEmpty() && !CsvCatalog.normalized(old).equals(CsvCatalog.normalized(fromField))) {
                        try (Cursor c = db.rawQuery("SELECT count(*) FROM records", null)) {
                            c.moveToFirst();
                            if (c.getLong(0) != 0)
                                throw new IOException("El campo identificador cambió. No se ha actualizado el catálogo.");
                        }
                    }
                    targetField[0] = old.isEmpty() ? fromField : old;
                    canonical.put(CsvCatalog.normalized(fromField), targetField[0]);
                }
                ContentValues seen = new ContentValues(); seen.put("id", id);
                if (db.insertWithOnConflict("csv_refresh_seen", null, seen, SQLiteDatabase.CONFLICT_IGNORE) < 0)
                    throw new IOException("Matrícula duplicada en el CSV.");
                LinkedHashMap<String, String> incoming = new LinkedHashMap<>();
                for (java.util.Map.Entry<String, String> entry : raw.entrySet()) {
                    String key = canonical.getOrDefault(CsvCatalog.normalized(entry.getKey()), entry.getKey());
                    incoming.put(key, entry.getValue());
                }
                incoming.put(targetField[0], id);
                String current = null;
                try (Cursor c = db.rawQuery("SELECT payload FROM records WHERE id=?", new String[]{id})) {
                    if (c.moveToFirst()) current = c.getString(0);
                }
                LinkedHashMap<String, String> merged = current == null
                    ? new LinkedHashMap<>() : JsonCodec.decode(current);
                // Retain only user-specific fields absent from the export; replace changed catalog columns.
                Set<String> columns = new LinkedHashSet<>();
                for (String key : incoming.keySet()) columns.add(CsvCatalog.normalized(key));
                merged.keySet().removeIf(key -> columns.contains(CsvCatalog.normalized(key)));
                merged.putAll(incoming);
                if (merged.size() > JsonCodec.MAX_FIELDS) throw JsonCodec.invalid();
                int total = 0;
                for (java.util.Map.Entry<String,String> value : merged.entrySet()) {
                    total += value.getKey().length() + (value.getValue() == null ? 0 : value.getValue().length());
                    if (total > 262144) throw JsonCodec.invalid();
                }
                allFields.addAll(merged.keySet());
                if (allFields.size() > JsonCodec.MAX_FIELDS) throw JsonCodec.invalid();
                String encoded = JsonCodec.encode(merged);
                if (current == null) {
                    ContentValues newRecord = new ContentValues();
                    newRecord.put("id", id); newRecord.put("payload", encoded);
                    db.insertOrThrow("records", null, newRecord);
                    stats[0]++;
                } else if (current.equals(encoded)) {
                    stats[2]++;
                } else {
                    ContentValues updated = new ContentValues(); updated.put("payload", encoded);
                    db.update("records", updated, "id=?", new String[]{id});
                    stats[1]++;
                }
            });
            int retained;
            try (Cursor c = db.rawQuery(
                    "SELECT count(*) FROM records WHERE id NOT IN (SELECT id FROM csv_refresh_seen)", null)) {
                c.moveToFirst(); retained = c.getInt(0);
            }
            storeFields(db, allFields);
            meta(db, "identifier", targetField[0]);
            // Retain the existing visible-field configuration, which remains a valid subset.
            CsvUpdate summary = new CsvUpdate(parsed.count, stats[0], stats[1], stats[2], retained, targetField[0]);
            if (apply) db.setTransactionSuccessful();
            return summary;
        } finally {
            db.endTransaction();
            db.execSQL("DROP TABLE IF EXISTS csv_refresh_seen");
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
