/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The single currency of composition: every element's {@code build()} returns one. Elements go onto
 * a canvas ({@link PageModel}), into containers and into larger elements.
 *
 * <p>{@code K} is a <b>phantom marker</b> — the factory class that produced the element
 * ({@code Element<Heading>}). There is no type hierarchy: a new element is just a factory, and the
 * type it produces exists for free. Wide points accept {@code Element<?>}; narrow points name
 * the marker, so the compiler rejects a combination only where it is genuinely meaningless.
 *
 * <p>Inside is a descriptor: the address of the element's adapter fragment ({@code template ::
 * fragment(e)}) plus data. Templates read {@link #asMap()}, so the adapter contract does not depend on
 * typing: the data is untyped, the marker is typed.
 *
 * <p>Five things live in this file, and a reader arriving at it should know which one they are in:
 *
 * <ul>
 *   <li><b>the value</b> — {@link #asMap()}, {@link #build()}, {@link #template()}, {@link #fragment()},
 *       {@link #bare()}, {@link #slot(String)}, {@link #slotNames()}, and equality, which is the
 *       descriptor's;</li>
 *   <li><b>the maker</b> — {@link Descriptor}, the one way an element is made, here and in consumer
 *       code, with {@link #raw} and {@link #script} as its two shortcuts;</li>
 *   <li><b>the guards a host uses</b> — {@link #settle}, {@link #requireRenderable},
 *       {@link #requireRenderableElement}, {@link #requireAdapter}, {@link #requireTag}, all public,
 *       because an element of yours hosts other elements the same way the kit's own do;</li>
 *   <li><b>and nothing about trees.</b> What a page or a subtree yields — the scripts it depends on,
 *       what its elements say about themselves — is {@link Tree}'s business, as is every other question
 *       whose answer needs more than one element.</li>
 * </ul>
 *
 * <p>Each is specified by a file of its own next to {@code ElementTest}. What used to be a fifth thing
 * here — the outline of a page — lives in {@link Outline} now: it is a property of a page rather than
 * of an element, and holding it here had taught the currency the name of one element's adapter.
 */
public final class Element<K> implements Composable<K> {

    /** Descriptor keys reserved by the engine; data cannot use them. */
    static final Set<String> RESERVED = Set.of("template", "fragment", "bare", "slots", "assets",
        "illustration", "describes", "means");

    private final Map<String, Object> m;

    private Element(Map<String, Object> m) {
        this.m = m;
    }

    /** Immutable descriptor as templates and the dispatcher see it. */
    public Map<String, Object> asMap() {
        return m;
    }

    /**
     * An element has already become one, so this hands back the same value. It exists so that a place
     * taking {@link Composable} takes an element too, without a second signature for the same idea.
     */
    @Override
    public Element<K> build() {
        return this;
    }

    public String template() {
        return (String) m.get("template");
    }

    public String fragment() {
        return (String) m.get("fragment");
    }

    /** Contents of a named slot; empty when the slot was never filled. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> slot(String name) {
        Map<String, List<Map<String, Object>>> slots = (Map<String, List<Map<String, Object>>>) m.get("slots");
        List<Map<String, Object>> items = slots == null ? null : slots.get(required(name, "Element.slot(name)"));
        return items == null ? List.of() : items;
    }

    /** Names of the slots this element fills, in the order they were filled; empty when it fills none. */
    @SuppressWarnings("unchecked")
    public Set<String> slotNames() {
        Map<String, List<Map<String, Object>>> slots = (Map<String, List<Map<String, Object>>>) m.get("slots");
        return slots == null ? Set.of() : new LinkedHashSet<>(slots.keySet());
    }

    /**
     * What a key of an element <b>is</b>, as far as the checks a page gets are concerned.
     *
     * <p>Two of them, because the two page checks need two and no more: a role is a question somebody
     * asks of every page, not a label for whatever an element happens to carry. A third arrives when a
     * third check does.
     *
     * <p>This is not {@code describes}, and the two are worth telling apart. A contribution says what a
     * page means <b>to a machine outside</b> — a search engine reading a trail. A role says what a key
     * means <b>to this kit</b>, so a check written for every page can find it. One leaves in the HTML;
     * the other never does.
     */
    public enum Role {

        /** The key carries the level of a heading: 1..6, as a number or as text that reads as one. */
        HEADING_LEVEL,

        /** The key carries an address inside the page — an {@code id} something links to. */
        ANCHOR
    }

    /**
     * What a descriptor declared for a role, or {@code null} when it declared nothing. The kit's own
     * elements answer here exactly as yours do: this asks what a key <b>is</b>, never which adapter
     * carries it, which is what lets a heading of yours join a check written for ours.
     */
    public static @Nullable Object roleIn(Map<?, ?> descriptor, Role role) {
        required(descriptor, "Element.roleIn(descriptor)");
        required(role, "Element.roleIn(role)");
        if (!(descriptor.get("means") instanceof Map<?, ?> means)) {
            return null;
        }
        for (Map.Entry<?, ?> entry : means.entrySet()) {
            if (role.name().equals(entry.getValue())) {
                return descriptor.get(entry.getKey());
            }
        }
        return null;
    }

    /**
     * The heading level a descriptor declares, whatever adapter it belongs to — a number, or text that
     * reads as one, because a descriptor minted by hand may write it either way and a check that
     * understood only a number would let a second title onto a page.
     */
    public static @Nullable Integer headingLevelIn(Map<?, ?> descriptor) {
        Object level = roleIn(descriptor, Role.HEADING_LEVEL);
        if (level instanceof Number number) {
            return number.intValue();
        }
        if (level instanceof String text) {
            try {
                return Integer.valueOf(text.strip());
            } catch (NumberFormatException notALevel) {
                return null;
            }
        }
        return null;
    }

    /** The anchor a descriptor declares, or {@code null} when it declares none. */
    public static @Nullable String anchorIn(Map<?, ?> descriptor) {
        return roleIn(descriptor, Role.ANCHOR) instanceof String anchor ? anchor : null;
    }

    /**
     * A value that had to be given. Replaces {@code Objects.requireNonNull} everywhere in the kit: the
     * exception it throws is somebody else's, and a consumer cannot tell it from their own code
     * failing — which is the whole reason this family exists.
     */
    public static <T> T required(@Nullable T value, String where) {
        if (value == null) {
            throw new MisuseException(where, "was not given");
        }
        return value;
    }

    /**
     * An address that leaves the page — into a canonical link, into Open Graph, into a graph a crawler
     * reads — is absolute or it is broken. Whoever reads it is not looking at the document and has
     * nothing to resolve a path against, and the failure is silent: no preview, or a canonical pointing
     * at a stranger.
     *
     * <p>Public because two elements need it, and two users make a policy rather than a detail.
     */
    public static String requireAbsolute(String url, String where) {
        String value = requireText(url, where).strip();
        if (!value.regionMatches(true, 0, "https://", 0, 8) && !value.regionMatches(true, 0, "http://", 0, 7)) {
            throw new MisuseException(where, "is not an absolute address: \"" + value
                + "\" — this value leaves the page, and whoever reads it has no document to resolve it against");
        }
        return value;
    }

    /** A browser drops these before it reads a scheme, so they cannot be used to hide one. */
    private static final Pattern IGNORED_IN_SCHEME = Pattern.compile("[\\s\\p{Cntrl}]");

    /** Schemes that execute rather than navigate. */
    private static final Set<String> EXECUTING_SCHEMES = Set.of("javascript:", "data:", "vbscript:");

    /**
     * An address that navigates. Kept trimmed, refused when blank, and refused when its scheme executes
     * rather than goes somewhere — {@code javascript:}, {@code data:}, {@code vbscript:}, however they
     * are spelled, since a browser drops spaces, tabs and control characters before reading a scheme and
     * {@code java\tscript:} is the plain one by the time it is followed.
     *
     * <p>Public because two elements need it, and an href is the last place any of those can be stopped
     * before the page.
     */
    public static String requireNavigable(String href, String where) {
        // no check for empty after this: requireText has already refused a blank, and stripping a text
        // that has something in it cannot leave nothing. The check was here when this guard belonged to
        // the heading and its first line was a bare null check; it survived the move and meant nothing
        String value = requireText(href, where).strip();
        String asFollowed = IGNORED_IN_SCHEME.matcher(value).replaceAll("").toLowerCase(java.util.Locale.ROOT);
        for (String executing : EXECUTING_SCHEMES) {
            if (asFollowed.startsWith(executing)) {
                throw new MisuseException(where, "is not a link but a script: \"" + href + "\"");
            }
        }
        return value;
    }

    private static final Pattern LANGUAGE_TAG = Pattern.compile("[A-Za-z0-9]+(-[A-Za-z0-9]+)*");

    /** A template path and a fragment name, as a template resolver would accept them. */
    private static final Pattern TEMPLATE = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9/_.-]*");
    private static final Pattern FRAGMENT = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_]*");

    /**
     * Text a page will show: given, and not empty. Two elements refuse the same thing for the same
     * reason — a caption with nothing in it is an empty box, a heading with nothing in it is a place in
     * the outline a screen reader stops at and finds nothing — so the refusal is one rule in one place
     * rather than two spellings of it.
     *
     * <p>What is given is kept exactly: a space inside a line belongs to whoever wrote the line. Only
     * a text that is nothing at all is refused.
     */
    public static String requireText(String text, String where) {
        required(text, where);
        if (text.isBlank()) {
            throw new MisuseException(where, "is blank — a page shows what it was given, "
                + "and this is nothing");
        }
        return text;
    }

    /**
     * A language tag, the way {@code lang} wants it: letters, digits and hyphens, nothing else. Not the
     * full BCP-47 grammar — just enough that a sentence, a translation or an empty string never ends up
     * in the attribute, where it would silently make the page claim a language it does not speak.
     *
     * <p>Public with the other guards: two elements of the kit already use it, which makes it the policy
     * of a concept rather than a detail of either, and an element of yours that offers {@code lang}
     * needs the same check the kit's own make.
     */
    public static String requireTag(String tag, String where) {
        required(tag, where);
        if (!LANGUAGE_TAG.matcher(tag).matches()) {
            throw new MisuseException(where, "is not a language tag: \"" + tag + "\"");
        }
        return tag;
    }

    /**
     * Whether a descriptor is an illustration: a sample framed for display rather than part of what the
     * page is. Reserved keys are this class's to know, so whoever walks a tree asks instead of reading.
     */
    static boolean isIllustration(Map<?, ?> descriptor) {
        return Boolean.TRUE.equals(descriptor.get("illustration"));
    }

    /** A script element is rendered as {@code template :: fragment}, everything else as {@code fragment(e)}. */
    public boolean bare() {
        return Boolean.TRUE.equals(m.get("bare"));
    }

    // value semantics: an element is its descriptor, so equal descriptors are the same element
    @Override
    public boolean equals(Object o) {
        return o instanceof Element<?> other && m.equals(other.m);
    }

    @Override
    public int hashCode() {
        return m.hashCode();
    }

    @Override
    public String toString() {
        return "Element" + m;
    }

    /**
     * Whatever becomes an element, settled at once and never before: a place that takes a
     * {@link Composable} builds it here, keeps the element and forgets the maker. That is the rule the
     * kit follows everywhere — accept what becomes an element, store only what has become one.
     */
    public static <K> Element<K> settle(Composable<K> composable, String where) {
        Element<K> built = required(composable, where).build();
        if (built == null) {
            throw new MisuseException(where, "built nothing");
        }
        return built;
    }

    /**
     * Guard for wide points: a script element never belongs in the element flow — the dispatcher calls
     * adapters with an argument, while a script fragment takes none. Declare it via {@code requires}.
     */
    public static Element<?> requireRenderableElement(Element<?> e, String where) {
        requireRenderable(e, where);
        return e;
    }

    public static void requireRenderable(Element<?> e, String where) {
        if (required(e, "Element.requireRenderable(element)").bare()) {
            throw new MisuseException(where, "a script element does not belong in the flow — "
                + "declare it as an element dependency via requires(), the canvas collects assets");
        }
    }

    /**
     * Runtime guard for narrow points, where the marker is erased: checks the adapter address of the
     * given element. Public on purpose — consumer elements guard their own narrow points the same way.
     */
    public static void requireAdapter(Element<?> e, String fragment, String where) {
        required(e, "Element.requireAdapter(element)");
        required(fragment, "Element.requireAdapter(fragment)");
        if (!fragment.equals(e.fragment())) {
            throw new MisuseException(where, "wanted the " + fragment + " adapter, and got " + e.fragment());
        }
    }

    /** Marker for a consumer fragment used as an element without a Java factory. */
    public static final class Raw {
        private Raw() {}
    }

    /** Marker for a behaviour script carried as an element. */
    public static final class Script {
        private Script() {}
    }

    /** A consumer fragment as an element: data via {@code with}, terminal {@code build}. */
    public static Descriptor<Raw> raw(String template, String fragment) {
        return new Descriptor<>(template, fragment);
    }

    /** A parameterless script fragment as an element. */
    public static Element<Script> script(String template, String fragment) {
        return new Descriptor<Script>(template, fragment).bare();
    }

    /**
     * Builds a descriptor: adapter address plus data, terminal {@link #build}. It becomes an element,
     * and says so — a descriptor goes into a page or a slot exactly where an element goes.
     */
    public static final class Descriptor<K> implements Composable<K> {

        private final LinkedHashMap<String, Object> d = new LinkedHashMap<>();

        /**
         * The address is checked for shape, and not out of tidiness: the dispatcher builds a fragment
         * expression out of it, so a name assembled from data would be evaluated rather than read.
         */
        Descriptor(String template, String fragment) {
            d.put("template", address(template, "Descriptor(template)", TEMPLATE));
            d.put("fragment", address(fragment, "Descriptor(fragment)", FRAGMENT));
            d.put("bare", false);
        }

        private static String address(String value, String where, Pattern shape) {
            required(value, where);
            if (!shape.matcher(value).matches()) {
                throw new MisuseException(where, "is not an adapter address: \"" + value
                    + "\" — the dispatcher turns it into an expression, so it may only be a path and a name");
            }
            return value;
        }

        /**
         * Entry point for an element factory — its own adapter address and marker:
         * {@code Element.Descriptor.<MyCard>of("fragments/my/card", "myCardEl").with(…).build()}.
         * Elements are written this way both inside the kit and in consumer code.
         */
        public static <K> Descriptor<K> of(String template, String fragment) {
            return new Descriptor<>(template, fragment);
        }

        /**
         * Element data; the adapter reads it as {@code ${e['key']}}. Reserved keys are rejected.
         *
         * <p>A collection handed in here is copied, however deep it goes. An element is its descriptor —
         * that is what makes two of them equal and what asset deduplication counts on — so a list the
         * caller still holds must not be able to change the element after it was built.
         */
        public Descriptor<K> with(String key, Object value) {
            required(key, "Descriptor.with(key)");
            if (RESERVED.contains(key)) {
                throw new MisuseException("Descriptor.with(key)",
                    "the key \"" + key + "\" is reserved by the descriptor");
            }
            // the key is written into the message and not into the place: a place is read by whoever
            // routes on one, and a call with a name of the caller's inside it is no longer a call
            if (value == null) {
                throw new MisuseException("Descriptor.with(value)",
                    "the value of key \"" + key + "\" was not given");
            }
            d.put(key, snapshot(value));
            return this;
        }

        /**
         * A value that cannot be changed from outside afterwards. Collections are copied and wrapped,
         * their contents with them; anything else is taken as it is, since a descriptor carries text,
         * numbers and elements, and those are values already.
         *
         * <p>Copied rather than handed to {@code List.copyOf}: a page may legitimately carry a list with
         * a hole in it, and refusing that here would turn a rendering decision into an exception.
         */
        private static @Nullable Object snapshot(@Nullable Object value) {
            if (value instanceof Map<?, ?> map) {
                LinkedHashMap<Object, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(k, snapshot(v)));
                return Collections.unmodifiableMap(copy);
            }
            if (value instanceof Collection<?> collection) {
                List<Object> copy = new ArrayList<>(collection.size());
                for (Object item : collection) {
                    copy.add(snapshot(item));
                }
                return Collections.unmodifiableList(copy);
            }
            return value;
        }

        /**
         * What this element says about itself for machines — a JSON-LD node, as <b>data</b>. The canvas
         * collects the contributions of a page, turns them into text once and prints one block; an
         * element never prints its own, and never carries finished text, because a descriptor holding
         * markup is the one seam through which markup could reach a page that was stored rather than
         * composed.
         *
         * <p>What may be in it is what the kit can write: text, whole numbers, true or false, maps and
         * lists. Anything else is refused <b>here</b>, not when a page is rendered, and the refusal
         * names the path that reaches it.
         *
         * <p>One element describes itself once. A second call is a mistake either way — accumulating
         * would guess at what was meant, replacing would lose the first quietly — so it is refused.
         */
        public Descriptor<K> describes(Map<String, ?> node) {
            required(node, "Descriptor.describes(node)");
            if (d.containsKey("describes")) {
                throw new MisuseException("Descriptor.describes",
                    "this element already describes itself: one element, one node");
            }
            if (node.isEmpty()) {
                throw new MisuseException("Descriptor.describes(node)", "an empty contribution describes nothing");
            }
            refuseAddressKeys(node);
            // The values are checked here rather than when a page is rendered, so a contribution that
            // cannot be written is refused where it was written — inside the factory that wrote it, with
            // that factory on the stack. And the check is the writing itself, thrown away: what can be
            // written can be contributed, and there is one definition of that rather than two drifting.
            Json.check(node, "Descriptor.describes");
            d.put("describes", snapshot(node));
            return this;
        }

        /**
         * A contribution may not carry the keys that mark a descriptor, at any depth: the walk knows an
         * element by exactly those, and would take a node for one — then look for an adapter that is
         * not there.
         */
        private static void refuseAddressKeys(@Nullable Object value) {
            if (value instanceof Map<?, ?> node) {
                for (Map.Entry<?, ?> entry : node.entrySet()) {
                    if ("template".equals(entry.getKey()) || "fragment".equals(entry.getKey())) {
                        throw new MisuseException("Descriptor.describes(node)",
                            "a contribution may not carry \"" + entry.getKey()
                            + "\": the walk knows a descriptor by that key and would take this for an element");
                    }
                    refuseAddressKeys(entry.getValue());
                }
            } else if (value instanceof Collection<?> items) {
                items.forEach(Descriptor::refuseAddressKeys);
            }
        }

        /**
         * Says what a key of this element <b>is</b>, so that a check written for every page can find it
         * without knowing which adapter carries it. The heading the kit ships and a chapter of yours say
         * the same thing here, and the outline counts both.
         *
         * <p>The key has to be one this element carries — checked at {@link #build()}, because the two
         * calls may be written in either order and refusing early would decide that for you.
         *
         * <p>One role, one key: two keys claiming to be the anchor of one element is a question with two
         * answers, and a check would have to guess which.
         */
        public Descriptor<K> means(String key, Role role) {
            required(key, "Descriptor.means(key)");
            required(role, "Descriptor.means(role)");
            @SuppressWarnings("unchecked")
            Map<String, String> means = (Map<String, String>) d.computeIfAbsent("means",
                k -> new LinkedHashMap<String, String>());
            for (Map.Entry<String, String> said : means.entrySet()) {
                if (said.getValue().equals(role.name()) && !said.getKey().equals(key)) {
                    throw new MisuseException("Descriptor.means(role)", "this element already says that \""
                        + said.getKey() + "\" is its " + role + ": one role, one key");
                }
            }
            means.put(key, role.name());
            return this;
        }

        /**
         * A named slot of a composite element. What may go in is constrained by the factory's slot
         * method, not here; an empty list leaves the slot unrendered.
         */
        @SuppressWarnings("unchecked")
        public Descriptor<K> slot(String name, List<? extends Composable<?>> items) {
            required(name, "Descriptor.slot(name)");
            required(items, "Descriptor.slot(items)");
            Map<String, List<Map<String, Object>>> slots =
                (Map<String, List<Map<String, Object>>>) d.computeIfAbsent("slots", k -> new LinkedHashMap<String, List<Map<String, Object>>>());
            slots.put(name, items.stream()
                .map(item -> settle(item, "Descriptor.slot(items) — one of them").asMap()).toList());
            return this;
        }

        /** Script dependency declared by the factory; the canvas renders it once per page. */
        @SafeVarargs
        @SuppressWarnings("unchecked")
        public final Descriptor<K> requires(Element<Script>... scripts) {
            List<Map<String, Object>> list =
                (List<Map<String, Object>>) d.computeIfAbsent("assets", k -> new ArrayList<Map<String, Object>>());
            for (Element<Script> s : required(scripts, "Descriptor.requires(scripts)")) {
                if (!required(s, "Descriptor.requires(scripts) — one of them").bare()) {
                    throw new MisuseException("Descriptor.requires(scripts) — one of them",
                        "a dependency must be a script element (Element.script)");
                }
                list.add(s.asMap());
            }
            return this;
        }

        /** Marks the element as an illustration: its contents are not page structure (see {@link Outline}). */
        public Descriptor<K> illustration() {
            d.put("illustration", true);
            return this;
        }

        Element<K> bare() {
            d.put("bare", true);
            return build();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Element<K> build() {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>(d);
            if (copy.get("means") instanceof Map<?, ?> means) {
                for (Object key : means.keySet()) {
                    if (!copy.containsKey(key)) {
                        throw new MisuseException("Descriptor.means(key)",
                            "\"" + key + "\" was given a role and never given a value");
                    }
                }
                copy.put("means", Collections.unmodifiableMap(new LinkedHashMap<>((Map<String, String>) means)));
            }
            if (copy.get("slots") instanceof Map<?, ?> slots) {   // snapshot: the builder may go on, the element must not change
                copy.put("slots", Collections.unmodifiableMap(new LinkedHashMap<>((Map<String, Object>) slots)));
            }
            if (copy.get("assets") instanceof List<?> assets) {
                copy.put("assets", List.copyOf((List<Map<String, Object>>) assets));
            }
            return new Element<>(Collections.unmodifiableMap(copy));
        }
    }
}
