package org.troop600.scoutsight.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 character-level CSV tokenizer.
 *
 * Uses a PushbackReader (size 1) to handle:
 *   - Quoted fields with embedded bare LF newlines (present in Advancement text)
 *   - CRLF record terminators
 *   - Doubled "" quote escapes
 */
public final class CsvTokenizer implements Closeable {

    private static final int EOF = -1;

    private final PushbackReader reader;
    private boolean atEof = false;

    public CsvTokenizer(Path path, Charset charset) throws IOException {
        this.reader = new PushbackReader(Files.newBufferedReader(path, charset), 2);
    }

    /** Drains and discards the header row. */
    public void skipHeader() throws IOException {
        nextRecord();
    }

    public boolean hasNext() {
        return !atEof;
    }

    /**
     * Returns the next record as a list of field strings, or null at EOF.
     * Field strings have surrounding quotes stripped and "" escapes resolved.
     */
    public List<String> nextRecord() throws IOException {
        if (atEof) return null;

        int ch = read();
        if (ch == EOF) {
            atEof = true;
            return null;
        }
        unread(ch);

        List<String> fields = new ArrayList<>();
        while (true) {
            fields.add(readField());
            int sep = read();
            if (sep == ',') {
                continue;          // more fields
            }
            if (sep == '\r') {
                read();            // consume the \n of CRLF
                break;
            }
            if (sep == '\n' || sep == EOF) {
                if (sep == EOF) atEof = true;
                break;
            }
            // Unexpected character after field — treat as separator and continue
            unread(sep);
            break;
        }
        return fields;
    }

    private String readField() throws IOException {
        int ch = peek();
        if (ch == '"') {
            return readQuotedField();
        }
        return readUnquotedField();
    }

    private String readQuotedField() throws IOException {
        read(); // consume opening '"'
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = read();
            if (ch == EOF) {
                break;  // truncated file — return whatever we have
            }
            if (ch == '"') {
                int next = peek();
                if (next == '"') {
                    read();        // consume second '"'; append one '"'
                    sb.append('"');
                } else if (next == ',' || next == '\r' || next == '\n' || next == EOF) {
                    break;         // proper closing quote
                } else {
                    // Malformed: '"' not followed by a valid field/record separator.
                    // Put the '"' back so the next nextRecord() call can start cleanly.
                    unread(ch);
                    break;
                }
            } else {
                sb.append((char) ch);   // includes bare LF — preserved as-is
            }
        }
        return sb.toString();
    }

    private String readUnquotedField() throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = peek();
            if (ch == ',' || ch == '\r' || ch == '\n' || ch == EOF) {
                break;
            }
            sb.append((char) read());
        }
        return sb.toString();
    }

    private int peek() throws IOException {
        int ch = read();
        if (ch != EOF) unread(ch);
        return ch;
    }

    private int read() throws IOException {
        return reader.read();
    }

    private void unread(int ch) throws IOException {
        if (ch != EOF) reader.unread(ch);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
