/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Writes a value as JSON for embedding in a {@code <script type="application/ld+json">} element.
 *
 * <p>A description has a bottom. Deeper than {@link #DEEPEST} it is refused — not because anything
 * anybody writes goes that deep, but because a map may hold itself, nothing stops a caller making one,
 * and this walk meets a contribution before it is copied. Walking one until the stack ends would hand
 * the consumer a {@code StackOverflowError}: the failure that kills a thread, names no element and is
 * exactly the unnamed kind this kit promises never to produce.
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
 * entities, path HTML does not. The line and paragraph separators go the same way — legal in JSON and
 * illegal in a javascript string literal, so a consumer who re-embeds this output somewhere stricter
 * does not discover the difference on their own data.
 *
 * <p>A refusal names the path that reaches the trouble, not only the fact of it: the person who has to
 * fix a bad key is whoever wrote that key, and {@code Descriptor.describes.itemListElement[0].name} tells them
 * path to look. A {@code null} argument is a different failure — a mistake at the call site — and is
 * named as one.
 *
 * <p>Two duties, named apart at the door because they happen at different moments: a contribution is
 * <b>checked</b> when an element is built, so that the factory which wrote a bad value is the one on
 * the stack, and the page is <b>written</b> when the canvas prints it. Neither is public. What an
 * element author needs of this policy they get through {@code describes}, which applies it for them;
 * publishing the writer would be publishing a JSON library, which this kit is not.
 */
final class Json {

    /** Where a refusal points when the trouble is the value handed in, rather than something inside it. */
    private static final String ROOT = "<root>";

    /**
     * How deep a description may go before this stops walking it.
     *
     * <p>Not a limit anybody will meet: a graph a search engine reads is a description of one thing,
     * and the deepest the vocabularies go is a handful. What it is for is the graph that has no bottom
     * — a map that holds itself, which nothing prevents a caller building and which arrives here
     * before it is copied, so this walk meets exactly what was passed.
     *
     * <p>Walking one until the stack ends is the worst failure this kit could hand over: a
     * {@code StackOverflowError} kills the thread it lands on, names no element, and is precisely the
     * unnamed kind the family of refusals exists to abolish.
     */
    private static final int DEEPEST = 32;

    private Json() {}

    /**
     * The value as JSON, compact — no spaces, no newlines. Key order is the caller's: in a graph the
     * order carries meaning to a reader, and sorting would take that away.
     */
    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(Guards.required(value, "Json.write(value)"), "", 0, out);
        return out.toString();
    }

    /**
     * Refuses a value the kit could not write, without writing it anywhere a page will see. The check
     * <b>is</b> the writing, thrown away: what may be contributed then has one definition rather than
     * two that drift apart. The name given is what a refusal reads under — a path inside the value is
     * written beneath it, so the reader is told both which faculty took the value and path in it the
     * trouble sits.
     */
    static void check(Object value, String where) {
        write(value, where, 0, new StringBuilder());
    }

    private static void write(@Nullable Object value, String path, int depth, StringBuilder out) {
        if (depth > DEEPEST) {
            // the path is never empty here: to be this deep is to be inside something, and to be
            // inside something is to have been reached by a key or an index that named the way
            throw refuse(path, "structured data is nested too deeply — deeper than " + DEEPEST
                + " is a description of nothing, and a description that holds itself has no bottom at all");
        }
        switch (value) {
            case null -> throw carries(path, "nothing at all");
            case String text -> writeText(text, out);
            case Integer number -> out.append(number.intValue());
            case Long number -> out.append(number.longValue());
            case Boolean flag -> out.append(flag.booleanValue());
            case Map<?, ?> map -> writeMap(map, path, depth, out);
            case Collection<?> items -> writeList(items, path, depth, out);
            default -> throw carries(path, "a " + value.getClass().getSimpleName());
        }
    }

    private static void writeMap(Map<?, ?> map, String path, int depth, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            if (!(entry.getKey() instanceof String key)) {
                throw carries(path, "a key of type "
                    + (entry.getKey() == null ? "null" : entry.getKey().getClass().getSimpleName()));
            }
            writeText(key, out);
            out.append(':');
            write(entry.getValue(), path.isEmpty() ? key : path + "." + key, depth + 1, out);
        }
        out.append('}');
    }

    private static void writeList(Collection<?> items, String path, int depth, StringBuilder out) {
        out.append('[');
        int index = 0;
        for (Object item : items) {
            if (index > 0) {
                out.append(',');
            }
            write(item, path + "[" + index + "]", depth + 1, out);
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

    /**
     * A refusal about what is inside a value, named by the way down to it. One door for both kinds,
     * so that the place is worked out once: a path that is empty means the value handed in itself.
     */
    private static MisuseException refuse(String path, String detail) {
        String where = path.isEmpty() ? ROOT : path;
        return new MisuseException(where, detail);
    }

    /** What the writer will take, said the same way wherever it refuses. */
    private static MisuseException carries(String path, String what) {
        return refuse(path, "structured data carries " + what
            + "; a contribution holds text, whole numbers, true or false, maps and lists");
    }
}
