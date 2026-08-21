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
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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
 */
public final class Element<K> implements Composable<K> {

    /** Descriptor keys reserved by the engine; data cannot use them. */
    static final Set<String> RESERVED = Set.of("template", "fragment", "bare", "slots", "assets", "illustration");

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
        List<Map<String, Object>> items = slots == null ? null : slots.get(Objects.requireNonNull(name, "name"));
        return items == null ? List.of() : items;
    }

    /** Names of the slots this element fills, in the order they were filled; empty when it fills none. */
    @SuppressWarnings("unchecked")
    public Set<String> slotNames() {
        Map<String, List<Map<String, Object>>> slots = (Map<String, List<Map<String, Object>>>) m.get("slots");
        return slots == null ? Set.of() : new LinkedHashSet<>(slots.keySet());
    }

    /**
     * Script dependencies of the whole element tree — its own {@code requires} plus every nested one,
     * deduplicated by address in traversal order. The canvas collects them and renders each once, so a
     * consumer never wires a script by hand.
     */
    public List<Element<Script>> assets() {
        return assetsOf(List.of(m));
    }

    /** Same, for a collection of elements, descriptors or page models. */
    public static List<Element<Script>> assetsOf(Collection<?> roots) {
        LinkedHashMap<String, Element<Script>> acc = new LinkedHashMap<>();
        collectAssets(roots, acc);
        return List.copyOf(acc.values());
    }

    @SuppressWarnings("unchecked")
    private static void collectAssets(@Nullable Object node, Map<String, Element<Script>> acc) {
        if (node instanceof Element<?> e) {
            collectAssets(e.m, acc);
        } else if (node instanceof Map<?, ?> map) {
            if (map.get("assets") instanceof List<?> declared) {
                for (Object a : declared) {
                    Map<String, Object> d = (Map<String, Object>) a;
                    String t = (String) d.get("template");
                    String f = (String) d.get("fragment");
                    acc.putIfAbsent(t + " :: " + f, script(t, f));
                }
            }
            for (Map.Entry<?, ?> en : map.entrySet()) {
                if (!"assets".equals(en.getKey())) {
                    collectAssets(en.getValue(), acc);
                }
            }
        } else if (node instanceof Collection<?> c) {
            for (Object o : c) {
                collectAssets(o, acc);
            }
        }
    }

    private static final Pattern LANGUAGE_TAG = Pattern.compile("[A-Za-z0-9]+(-[A-Za-z0-9]+)*");

    /** A template path and a fragment name, as a template resolver would accept them. */
    private static final Pattern TEMPLATE = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9/_.-]*");
    private static final Pattern FRAGMENT = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_]*");

    /**
     * A language tag, the way {@code lang} wants it: letters, digits and hyphens, nothing else. Not the
     * full BCP-47 grammar — just enough that a sentence, a translation or an empty string never ends up
     * in the attribute, where it would silently make the page claim a language it does not speak.
     */
    static String requireTag(String tag, String name) {
        Objects.requireNonNull(tag, name);
        if (!LANGUAGE_TAG.matcher(tag).matches()) {
            throw new IllegalArgumentException(name + " is not a language tag: \"" + tag + "\"");
        }
        return tag;
    }

    /**
     * Outline guard, run by the canvas before a page is rendered. Two things are checked, and both are
     * defects a reader or a crawler would meet on the finished page rather than opinions about style:
     *
     * <ul>
     *   <li>at most one first-level heading — the title of a page is one thing, not several;</li>
     *   <li>no level skipped — a page that uses h4 while nothing on it is an h3 has a hole in its
     *       outline, and a screen reader walking headings falls straight through it;</li>
     *   <li>no level outside h1..h6 — html has six, and the adapter renders nothing at all for a
     *       seventh, which is the kind of silence a page should never ship with.</li>
     * </ul>
     *
     * <p>A level is read however it was written into the descriptor — as a number or as text. The
     * factories always write a number, but an element minted by hand may not, and a guard that only
     * understood one of the two would have let a second H1 through while the adapter happily rendered
     * it.
     *
     * <p>The levels a page uses have to be contiguous; where in the flow they stand is not the guard's
     * business, so nesting an element deeper never trips it. Illustration subtrees are skipped — a
     * sample framed for display is not page structure — and a page with no headings at all is legal.
     *
     * <p>The guarantee stops where the kit stops. A heading an author wrote inside markdown is not seen
     * here — that text is data and arrives as HTML long after this runs. Such headings are lowered under
     * the page by {@link MarkdownRenderer} instead, so content does not declare a second H1; a gap the
     * author left inside the text travels with it, and neither this guard nor the renderer closes it.
     */
    public static void assertOutline(Collection<?> roots) {
        List<String> h1 = new ArrayList<>();
        TreeSet<Integer> levels = new TreeSet<>();
        collectHeadings(roots, h1, levels);
        if (h1.size() > 1) {
            throw new IllegalStateException("more than one H1 on the page: " + h1
                + " — the title of a page is one thing; sections start at h2");
        }
        if (levels.isEmpty()) {
            return;
        }
        if (levels.first() < 1 || levels.last() > 6) {
            throw new IllegalStateException("heading level outside h1..h6 on the page: " + levels
                + " — html has six, and the adapter renders nothing at all for anything else");
        }
        for (int level = levels.first(); level < levels.last(); level++) {
            if (!levels.contains(level + 1)) {
                throw new IllegalStateException("heading level h" + (level + 1) + " is missing on a page that uses "
                    + levels + " — an outline with a hole in it is a page a screen reader falls through");
            }
        }
    }

    /** A level as the descriptor happens to carry it: a number, or text that reads as one. */
    private static @Nullable Integer levelOf(@Nullable Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.valueOf(text.strip());
            } catch (NumberFormatException notALevel) {
                return null;
            }
        }
        return null;
    }

    private static void collectHeadings(@Nullable Object node, List<String> h1, Set<Integer> levels) {
        if (node instanceof Element<?> e) {
            collectHeadings(e.m, h1, levels);
        } else if (node instanceof Map<?, ?> map) {
            if (Boolean.TRUE.equals(map.get("illustration"))) {
                return;
            }
            if ("headingEl".equals(map.get("fragment"))) {
                Integer level = levelOf(map.get("level"));
                if (level != null) {
                    levels.add(level);
                    if (level == 1) {
                        h1.add(String.valueOf(map.get("text")));
                    }
                }
            }
            for (Map.Entry<?, ?> en : map.entrySet()) {
                if (!"assets".equals(en.getKey())) {
                    collectHeadings(en.getValue(), h1, levels);
                }
            }
        } else if (node instanceof Collection<?> c) {
            for (Object o : c) {
                collectHeadings(o, h1, levels);
            }
        }
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
    public static <K> Element<K> settle(Composable<K> composable, String name) {
        Objects.requireNonNull(composable, name);
        return Objects.requireNonNull(composable.build(), () -> name + " built nothing");
    }

    /**
     * Guard for wide points: a script element never belongs in the element flow — the dispatcher calls
     * adapters with an argument, while a script fragment takes none. Declare it via {@code requires}.
     */
    public static Element<?> requireRenderableElement(Element<?> e, String what) {
        requireRenderable(e, what);
        return e;
    }

    public static void requireRenderable(Element<?> e, String what) {
        if (Objects.requireNonNull(e, "element").bare()) {
            throw new IllegalArgumentException(what + ": a script element does not belong in the flow — "
                + "declare it as an element dependency via requires(), the canvas collects assets");
        }
    }

    /**
     * Runtime guard for narrow points, where the marker is erased: checks the adapter address of the
     * given element. Public on purpose — consumer elements guard their own narrow points the same way.
     */
    public static void requireAdapter(Element<?> e, String fragment, String what) {
        if (!fragment.equals(e.fragment())) {
            throw new IllegalArgumentException(what + " (got " + e.fragment() + ")");
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
            d.put("template", address(template, "template", TEMPLATE));
            d.put("fragment", address(fragment, "fragment", FRAGMENT));
            d.put("bare", false);
        }

        private static String address(String value, String name, Pattern shape) {
            Objects.requireNonNull(value, name);
            if (!shape.matcher(value).matches()) {
                throw new IllegalArgumentException(name + " is not an adapter address: \"" + value
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
            Objects.requireNonNull(key, "key");
            if (RESERVED.contains(key)) {
                throw new IllegalArgumentException("key \"" + key + "\" is reserved by the descriptor");
            }
            d.put(key, snapshot(Objects.requireNonNull(value, () -> "value of key \"" + key + "\"")));
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
         * A named slot of a composite element. What may go in is constrained by the factory's slot
         * method, not here; an empty list leaves the slot unrendered.
         */
        @SuppressWarnings("unchecked")
        public Descriptor<K> slot(String name, List<? extends Composable<?>> items) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(items, "items");
            Map<String, List<Map<String, Object>>> slots =
                (Map<String, List<Map<String, Object>>>) d.computeIfAbsent("slots", k -> new LinkedHashMap<String, List<Map<String, Object>>>());
            slots.put(name, items.stream().map(item -> settle(item, "slot item").asMap()).toList());
            return this;
        }

        /** Script dependency declared by the factory; the canvas renders it once per page. */
        @SafeVarargs
        @SuppressWarnings("unchecked")
        public final Descriptor<K> requires(Element<Script>... scripts) {
            List<Map<String, Object>> list =
                (List<Map<String, Object>>) d.computeIfAbsent("assets", k -> new ArrayList<Map<String, Object>>());
            for (Element<Script> s : Objects.requireNonNull(scripts, "scripts")) {
                if (!Objects.requireNonNull(s, "script").bare()) {
                    throw new IllegalArgumentException("a dependency must be a script element (Element.script)");
                }
                list.add(s.asMap());
            }
            return this;
        }

        /** Marks the element as an illustration: its contents are not page structure (see {@link #assertOutline}). */
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
