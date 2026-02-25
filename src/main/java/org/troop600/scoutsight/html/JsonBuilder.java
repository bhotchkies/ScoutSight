package org.troop600.scoutsight.html;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal fluent JSON serializer — no external dependencies.
 *
 * Usage pattern:
 * <pre>
 *   String json = new JsonBuilder()
 *       .obj()
 *           .field("title", "My Title")
 *           .arr("items")
 *               .obj()
 *                   .field("id", 1)
 *                   .field("active", true)
 *               .endObj()
 *           .endArr()
 *       .endObj()
 *       .toString();
 * </pre>
 */
class JsonBuilder {

    private final StringBuilder sb = new StringBuilder();
    // true = next item in this container is the first (no leading comma needed)
    private final Deque<Boolean> firstItem = new ArrayDeque<>();

    /** Start an anonymous object (root or array element). */
    JsonBuilder obj() {
        commaIfNeeded();
        sb.append('{');
        firstItem.push(true);
        return this;
    }

    /** Start a keyed object field: {@code "key":{...}}. */
    JsonBuilder obj(String key) {
        commaIfNeeded();
        appendKey(key);
        sb.append('{');
        firstItem.push(true);
        return this;
    }

    /** Start an anonymous array (root or array element). */
    JsonBuilder arr() {
        commaIfNeeded();
        sb.append('[');
        firstItem.push(true);
        return this;
    }

    /** Start a keyed array field: {@code "key":[...]}. */
    JsonBuilder arr(String key) {
        commaIfNeeded();
        appendKey(key);
        sb.append('[');
        firstItem.push(true);
        return this;
    }

    /** Emit {@code "key":"value"}. */
    JsonBuilder field(String key, String value) {
        commaIfNeeded();
        appendKey(key);
        sb.append('"').append(escape(value)).append('"');
        return this;
    }

    /** Emit {@code "key":true} or {@code "key":false}. */
    JsonBuilder field(String key, boolean value) {
        commaIfNeeded();
        appendKey(key);
        sb.append(value);
        return this;
    }

    /** Emit {@code "key":123}. */
    JsonBuilder field(String key, int value) {
        commaIfNeeded();
        appendKey(key);
        sb.append(value);
        return this;
    }

    /** Emit {@code "key":["val1","val2",...]}. */
    JsonBuilder strArr(String key, java.util.List<String> values) {
        arr(key);
        for (String v : values) {
            commaIfNeeded();
            sb.append('"').append(escape(v)).append('"');
        }
        return endArr();
    }

    /** Close the current object. */
    JsonBuilder endObj() {
        sb.append('}');
        firstItem.pop();
        return this;
    }

    /** Close the current array. */
    JsonBuilder endArr() {
        sb.append(']');
        firstItem.pop();
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    // -------------------------------------------------------------------------

    private void commaIfNeeded() {
        if (firstItem.isEmpty()) return;
        if (firstItem.peek()) {
            firstItem.pop();
            firstItem.push(false);
        } else {
            sb.append(',');
        }
    }

    private void appendKey(String key) {
        sb.append('"').append(escape(key)).append('"').append(':');
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
