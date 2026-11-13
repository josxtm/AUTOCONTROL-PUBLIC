package com.josxtm.autocontrol.community.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Streaming CSV/TSV reader for user-supplied eSir GRID MT exports.
 * Does not perform network operations, infer missing values, or log cell contents.
 */
public final class CsvCatalog {
    public interface RowConsumer {
        void onRow(LinkedHashMap<String, String> values, String identifier) throws IOException;
    }
    public static final class Parsed {
        public final String identifier;
        public final int count;
        public final List<String> headers;
        Parsed(String identifier, int count, List<String> headers) {
            this.identifier = identifier; this.count = count; this.headers = headers;
        }
    }

    private static final int MAX_CHARS = 64 * 1024 * 1024;
    private static final int MAX_CELL = JsonCodec.MAX_TEXT;
    private static final int MAX_HEADER = 262144;

    public static String normalized(String name) {
        String stripped = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
        return stripped.replaceAll("[^A-Z0-9]", "");
    }

    private static char delimiter(String line) throws IOException {
        int[] counts = new int[3];
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') i++;
                else quoted = !quoted;
            } else if (!quoted) {
                if (ch == ';') counts[0]++;
                if (ch == ',') counts[1]++;
                if (ch == '\t') counts[2]++;
            }
        }
        int best = 0;
        for (int i = 1; i < 3; i++) if (counts[i] > counts[best]) best = i;
        if (counts[best] == 0) throw new IOException("No se reconoce el separador del CSV.");
        return new char[]{';', ',', '\t'}[best];
    }

    private static String identifier(List<String> names, String preferred) throws IOException {
        String selected = null;
        // GRID MT is keyed by matrícula: do not silently use the row number or a changing address.
        for (String header : names) {
            String name = normalized(header);
            if (name.equals("MATRICULA") || name.equals("MATRICULACT")
                    || name.equals("MATRICULACENTRO") || name.equals("MATRICULACENTROTRANSFORMACION")) {
                selected = header; break;
            }
        }
        if (selected == null && preferred != null && !preferred.isBlank()) {
            String wanted = normalized(preferred);
            for (String header : names) if (normalized(header).equals(wanted)) { selected = header; break; }
        }
        if (selected == null) throw new IOException("No se encuentra la columna MATRICULA. Revisa el CSV original.");
        return selected;
    }

    public static Parsed read(Reader source, String preferred, RowConsumer consumer) throws IOException {
        Reader bounded = new Reader() {
            private int read;
            @Override public int read(char[] target, int offset, int length) throws IOException {
                int count = source.read(target, offset, Math.min(length, Math.max(1, MAX_CHARS - read + 1)));
                if (count > 0 && (read += count) > MAX_CHARS) throw new IOException("CSV demasiado grande.");
                return count;
            }
            @Override public void close() throws IOException { source.close(); }
        };
        try (BufferedReader buffered = new BufferedReader(bounded, 8192)) {
            buffered.mark(MAX_HEADER + 1);
            String sample = buffered.readLine();
            if (sample == null || sample.isEmpty() || sample.length() > MAX_HEADER)
                throw new IOException("Cabecera CSV vacía o demasiado larga.");
            char sep = delimiter(sample);
            buffered.reset();
            Rows parser = new Rows(buffered, sep);
            List<String> headers = parser.next();
            if (headers == null || headers.size() < 2 || headers.size() > JsonCodec.MAX_FIELDS)
                throw new IOException("Cabecera CSV inválida.");
            headers.set(0, headers.get(0).replace("\uFEFF", ""));
            Set<String> unique = new HashSet<>();
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i).trim();
                if (header.isEmpty() || header.length() > 128 || !unique.add(normalized(header)))
                    throw new IOException("Hay columnas vacías o duplicadas en el CSV.");
                headers.set(i, header);
            }
            String id = identifier(headers, preferred);
            int count = 0;
            List<String> cells;
            while ((cells = parser.next()) != null) {
                if (cells.size() == 1 && cells.get(0).trim().isEmpty()) continue;
                if (cells.size() != headers.size()) throw new IOException("Una fila CSV no coincide con la cabecera.");
                if (++count > WorkspaceStore.MAX_ROWS) throw new IOException("Límite de CT superado.");
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                int characters = 0;
                for (int i = 0; i < headers.size(); i++) {
                    String cell = cells.get(i);
                    if ((characters += cell.length() + headers.get(i).length()) > 262144)
                        throw new IOException("Fila CSV demasiado larga.");
                    row.put(headers.get(i), cell);
                }
                String key = row.get(id).trim();
                if (key.isEmpty() || key.length() > 256) throw new IOException("Matrícula vacía o inválida.");
                row.put(id, key);
                consumer.onRow(row, id);
            }
            if (count == 0) throw new IOException("CSV sin registros.");
            return new Parsed(id, count, headers);
        }
    }

    private static final class Rows {
        private final PushbackReader input;
        private final char sep;
        private boolean finished;
        Rows(Reader reader, char sep) { this.input = new PushbackReader(reader, 2); this.sep = sep; }
        List<String> next() throws IOException {
            if (finished) return null;
            List<String> result = new ArrayList<>();
            StringBuilder value = new StringBuilder();
            boolean quoted = false, afterQuote = false, started = false;
            int next;
            while ((next = input.read()) != -1) {
                char ch = (char) next; started = true;
                if (quoted) {
                    if (ch == '"') {
                        int other = input.read();
                        if (other == '"') value.append('"');
                        else { quoted = false; afterQuote = true; if (other != -1) input.unread(other); }
                    } else value.append(ch);
                } else if (ch == sep || ch == '\n' || ch == '\r') {
                    result.add(value.toString());
                    if (ch == sep) { value.setLength(0); afterQuote = false; }
                    else {
                        if (ch == '\r') {
                            int other = input.read();
                            if (other != '\n' && other != -1) input.unread(other);
                        }
                        return result;
                    }
                } else if (ch == '"' && value.length() == 0 && !afterQuote) quoted = true;
                else if (afterQuote && (ch == ' ' || ch == '\t')) { /* Excel allows trailing spaces. */ }
                else if (afterQuote || ch == '"') throw new IOException("Comillas CSV incorrectas.");
                else value.append(ch);
                if (value.length() > MAX_CELL) throw new IOException("Celda CSV demasiado larga.");
            }
            finished = true;
            if (quoted) throw new IOException("Comillas CSV sin cerrar.");
            if (!started && result.isEmpty()) return null;
            result.add(value.toString());
            return result;
        }
    }

    private CsvCatalog() { }
}
