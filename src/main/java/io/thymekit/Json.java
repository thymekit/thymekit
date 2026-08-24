/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Writes a value as JSON for embedding in a {@code <script type="application/ld+json">} element.
 *
 * <p>The accepted set of types is closed on purpose: {@code String}, {@code Integer}, {@code Long},
 * {@code Boolean}, a {@code Map} with string keys, and a {@code Collection}. Anything else is refused
 * rather than serialised as best it can be — a structured-data contribution that carries a date, a
 * double or an element is a mistake at the point it was written, and saying so there is cheaper than
 * shipping a page whose machine-readable half quietly means something else. Widening the set later
 * breaks nobody; having guessed once is a page that means something other than it says.
 *
 * <p>Escaping goes beyond what JSON requires, and the reason is the element the text lands in.
 * {@code <} is written as {@code <} so the sequence {@code </script>} cannot close the block;
 * {@code &} as {@code &} because a document served as XML parses script content and decodes
 * entities, where HTML does not. The line and paragraph separators go the same way — legal in JSON and
 * illegal in a javascript string literal, so a consumer who re-embeds this output somewhere stricter
 * does not discover the difference on their own data.
 *
 * <p>A refusal names the path that reaches the trouble, not only the fact of it: the person who has to
 * fix a bad key is whoever wrote that key, and {@code itemListElement[0].name} tells them where to
 * look. A {@code null} argument is a different failure — a mistake at the call site — and is named as
 * one.
 *
 * <p>Not public: the only caller is the canvas, and the rule of this project is that what one caller
 * uses stays where it is until a second one appears.
 */
final class Json {

    /** Where a refusal points when the trouble is the value handed in, rather than something inside it. */
    private static final String ROOT = "<root>";

    private Json() {}

    /**
     * The value as JSON, compact — no spaces, no newlines. Key order is the caller's: in a graph the
     * order carries meaning to a reader, and sorting would take that away.
     */
    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(Objects.requireNonNull(value, "value"), "", out);
        return out.toString();
    }

    private static void write(@Nullable Object value, String path, StringBuilder out) {
        switch (value) {
            case null -> throw refuse(path, "nothing at all");
            case String text -> writeText(text, out);
            case Integer number -> out.append(number.intValue());
            case Long number -> out.append(number.longValue());
            case Boolean flag -> out.append(flag.booleanValue());
            case Map<?, ?> map -> writeMap(map, path, out);
            case Collection<?> items -> writeList(items, path, out);
            default -> throw refuse(path, "a " + value.getClass().getSimpleName());
        }
    }

    private static void writeMap(Map<?, ?> map, String path, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            if (!(entry.getKey() instanceof String key)) {
                throw refuse(path, "a key of type "
                    + (entry.getKey() == null ? "null" : entry.getKey().getClass().getSimpleName()));
            }
            writeText(key, out);
            out.append(':');
            write(entry.getValue(), path.isEmpty() ? key : path + "." + key, out);
        }
        out.append('}');
    }

    private static void writeList(Collection<?> items, String path, StringBuilder out) {
        out.append('[');
        int index = 0;
        for (Object item : items) {
            if (index > 0) {
                out.append(',');
            }
            write(item, path + "[" + index + "]", out);
            index++;
        }
        out.append(']');
    }

    private static void writeText(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                out.append("\\\"");
            } else if (c == '\\') {
                out.append("\\\\");
            } else if (c == '<' || c == '&' || c == 0x2028 || c == 0x2029 || c < ' ') {
                out.append("\\u").append("%04X".formatted((int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }

    private static IllegalArgumentException refuse(String path, String what) {
        return new IllegalArgumentException("structured data carries " + what + " at "
            + (path.isEmpty() ? ROOT : path)
            + "; a contribution holds text, whole numbers, true or false, maps and lists");
    }
}
