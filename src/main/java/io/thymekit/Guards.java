/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a value must be before a page carries it — the refusals every element of the kit makes, and
 * every element of yours may.
 *
 * <p>Its own class because none of these knows what an element is. A text that a page will show, a
 * language tag, an address that leaves the page, an address that navigates rather than executes, an
 * address inside the page: five rules about text and about HTML, plus the one that says a value had to
 * be given at all. They lived in the currency because that is where the first of them was written, and
 * they took the currency's dependencies with them wherever they went.
 *
 * <p>One question sits beside the six refusals — {@link #isNothing} — and it is not an oddity of
 * shape. Every refusal here begins by deciding what counts as nothing at all; a class that kept six
 * refusals and hid the decision they share would leave everybody else to guess it, which is how the
 * kit came to have two answers to it. What a page <b>does</b> turns on that answer as much as what it
 * refuses: a markdown block with nothing in it shows its empty state rather than an empty box.
 *
 * <p>That is the practical half of the move. While the guard everything uses lived in {@code Element},
 * everything that guards anything depended on the currency — and the currency depended back on
 * whatever it called, which is how two classes came to hold each other. Nothing here depends on
 * anything of the kit's, so nothing that guards a value need depend on the currency again.
 *
 * <p>Guards <b>over an element</b> stay where an element is: {@code Element.settle},
 * {@code Element.requireAdapter}, {@code Element.requireRenderable}. The line is not tidiness — it is
 * what keeps the dependency one-way.
 *
 * <p>Every one of them takes the place it is refusing at, and the place is a call:
 * {@code Guards.text(label, "Breadcrumbs.named(label)")}. Yours name your calls, and a handler cannot
 * tell whose element a refusal came from, which is the point.
 */
public final class Guards {

    private Guards() {}

    /** What a browser unpicks before it reads a scheme: whitespace and control characters. */
    private static final Pattern IGNORED_IN_SCHEME = Pattern.compile("[\\s\\p{Cntrl}]");

    /** Schemes that execute instead of navigating. */
    private static final Set<String> EXECUTING_SCHEMES = Set.of("javascript:", "data:", "vbscript:");

    /** A BCP 47 tag as far as an attribute is concerned: alphanumeric parts joined by hyphens. */
    private static final Pattern LANGUAGE_TAG = Pattern.compile("[A-Za-z0-9]+(-[A-Za-z0-9]+)*");

    /**
     * Whether a text is nothing at all: empty, or made of nothing a reader would see.
     *
     * <p>Neither of the two answers java offers is the right one. {@code String.isBlank} asks
     * {@code Character.isWhitespace}, which counts an en space and misses the non-breaking one — and
     * the non-breaking one is exactly what a rich editor leaves behind when somebody empties a field.
     * A regex of {@code \s} is worse still: in java that is seven ASCII characters and nothing else,
     * so an ideographic space would pass it as text.
     *
     * <p>So the question is asked of every code point, and the answer is no if any of them would show
     * something. Whitespace, a space of any width, a format character (the zero width space, the byte
     * order mark, a soft hyphen, a word joiner) and a control character all show nothing; between two
     * visible things they are somebody's meaning and the text is not empty, which is why this asks
     * about the whole of it rather than about what it contains.
     *
     * <p>Public because the answer decides what a page <b>does</b> and not only whether it refuses: a
     * markdown block with nothing in it shows its empty state instead of an empty box, and an element
     * of yours has the same decision to make about data of yours. And one answer rather than several,
     * because the kit used to have two: the markdown of somebody else's article knew that a line of a
     * non-breaking space was empty, and a caption a programmer wrote did not — which is this project's
     * own rule upside down.
     */
    public static boolean isNothing(@Nullable String text) {
        return text == null || text.codePoints().allMatch(Guards::showsNothing);
    }

    /** Whether a code point puts anything on a page. */
    private static boolean showsNothing(int codePoint) {
        if (Character.isWhitespace(codePoint)) {
            return true;
        }
        int kind = Character.getType(codePoint);
        return kind == Character.SPACE_SEPARATOR       // the non-breaking spaces java does not count
            || kind == Character.FORMAT                // zero width space, word joiner, soft hyphen, bom
            || kind == Character.CONTROL;              // and what a terminal would beep at
    }

    /**
     * A value that had to be given. Replaces {@code Objects.requireNonNull} everywhere in the kit: the
     * exception that one throws is somebody else's, and a consumer cannot tell it from their own code
     * failing — which is the whole reason the kit's own family of failures exists.
     */
    public static <T> T required(@Nullable T value, String where) {
        if (value == null) {
            throw new MisuseException(where, "was not given");
        }
        return value;
    }

    /**
     * Text a page will show: given, and not nothing. Two elements refuse the same thing for the same
     * reason, which by the canon of this project makes it one rule rather than two spellings.
     *
     * <p>What is given is kept exactly: a space inside a line belongs to whoever wrote the line. Only a
     * text that is nothing at all is refused.
     */
    public static String text(String text, String where) {
        required(text, where);
        if (isNothing(text)) {
            throw new MisuseException(where, "is blank — a page shows what it was given, "
                + "and this is nothing");
        }
        return text;
    }

    /**
     * A language tag, for the attribute that tells a screen reader how to pronounce a phrase. A
     * sentence there is worse than nothing: a page that claims a language it does not speak is read
     * aloud wrongly and confidently.
     *
     * <p>Shape, not truth. {@code zz-ZZ} has the shape of a tag and names no language on earth, and
     * telling those two apart needs the IANA registry — a list that would have to be carried, kept in
     * step and versioned, for a mistake nobody makes by accident. What is refused is what an attribute
     * cannot hold: a sentence, an underscore, a hyphen with nothing on one side of it.
     */
    public static String tag(String tag, String where) {
        required(tag, where);
        if (!LANGUAGE_TAG.matcher(tag).matches()) {
            throw new MisuseException(where, "is not a language tag: \"" + tag + "\"");
        }
        return tag;
    }

    /**
     * An address that leaves the page — into a canonical link, into Open Graph, into a graph a crawler
     * reads — is absolute or it is broken. Whoever reads it is not looking at the document and has
     * nothing to resolve a path against, so a relative one is not a smaller picture: it is no picture
     * at all, and nothing about the page says so.
     */
    public static String absolute(String url, String where) {
        String value = text(url, where).strip();
        // a space inside it is a space a stranger's parser will not guess the end of, and half an
        // address in an og:url is worse than none: the page looks exactly like a page that had one
        if (value.codePoints().anyMatch(Guards::showsNothing)) {
            throw new MisuseException(where, "is not an address anybody can follow: \"" + value
                + "\" — it carries a space, and whoever reads this is not a browser that could guess"
                + " where it ended");
        }
        int host = value.regionMatches(true, 0, "https://", 0, 8) ? 8
            : value.regionMatches(true, 0, "http://", 0, 7) ? 7
            : -1;
        // a scheme is not an address: "https://" resolves to nowhere, and a page that printed it would
        // hand a crawler a link to nothing while looking exactly like a page that had one
        if (host == -1 || host == value.length() || value.charAt(host) == '/') {
            throw new MisuseException(where, "is not an absolute address: \"" + value
                + "\" — this value leaves the page, and whoever reads it has no document to resolve it against");
        }
        return value;
    }

    /**
     * An address that navigates rather than executes. A browser unpicks whitespace and control
     * characters before it reads a scheme, so {@code java\tscript:} is refused with the plain spelling;
     * an href is the last place any of them can be stopped before the page.
     */
    public static String navigable(String href, String where) {
        String value = text(href, where).strip();
        String asFollowed = IGNORED_IN_SCHEME.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);
        for (String executing : EXECUTING_SCHEMES) {
            if (asFollowed.startsWith(executing)) {
                throw new MisuseException(where, "is not a link but a script: \"" + href + "\"");
            }
        }
        return value;
    }

    /**
     * An address inside a page: given, not empty, and one word. An attribute keeps only what comes
     * before the first space, so an anchor with a space in it is an address that silently truncates.
     */
    public static String anchor(String anchor, String where) {
        String value = text(anchor, where);
        // the same question the rest of this class asks, for the same reason: a space of any width is
        // a space somebody has to type into a link and cannot see, and an ASCII one ends the attribute
        if (value.codePoints().anyMatch(Guards::showsNothing)) {
            throw new MisuseException(where, "is not one word: \"" + value + "\" — an address inside a "
                + "page is typed into a link by hand, and a space in it either ends the attribute or "
                + "cannot be seen to be typed");
        }
        return value;
    }
}
