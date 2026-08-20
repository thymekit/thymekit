/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
public final class Element<K> {

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

    /**
     * Outline guard: at most one first-level heading per page. Illustration subtrees are skipped — a
     * sample framed for display is not page structure. No H1 at all is legal.
     */
    public static void assertSingleH1(Collection<?> roots) {
        List<String> found = new ArrayList<>();
        collectH1(roots, found);
        if (found.size() > 1) {
            throw new IllegalStateException("more than one H1 on the page: " + found
                + " — only the page hero carries an H1, sections start at h2");
        }
    }

    private static void collectH1(@Nullable Object node, List<String> found) {
        if (node instanceof Element<?> e) {
            collectH1(e.m, found);
        } else if (node instanceof Map<?, ?> map) {
            if (Boolean.TRUE.equals(map.get("illustration"))) {
                return;
            }
            if ("headingEl".equals(map.get("fragment")) && Integer.valueOf(1).equals(map.get("level"))) {
                found.add(String.valueOf(map.get("text")));
            }
            for (Map.Entry<?, ?> en : map.entrySet()) {
                if (!"assets".equals(en.getKey())) {
                    collectH1(en.getValue(), found);
                }
            }
        } else if (node instanceof Collection<?> c) {
            for (Object o : c) {
                collectH1(o, found);
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

    /** Builds a descriptor: adapter address plus data, terminal {@link #build}. */
    public static final class Descriptor<K> {

        private final LinkedHashMap<String, Object> d = new LinkedHashMap<>();

        Descriptor(String template, String fragment) {
            d.put("template", Objects.requireNonNull(template, "template"));
            d.put("fragment", Objects.requireNonNull(fragment, "fragment"));
            d.put("bare", false);
        }

        /**
         * Entry point for an element factory — its own adapter address and marker:
         * {@code Element.Descriptor.<MyCard>of("fragments/my/card", "myCardEl").with(…).build()}.
         * Elements are written this way both inside the kit and in consumer code.
         */
        public static <K> Descriptor<K> of(String template, String fragment) {
            return new Descriptor<>(template, fragment);
        }

        /** Element data; the adapter reads it as {@code ${e['key']}}. Reserved keys are rejected. */
        public Descriptor<K> with(String key, Object value) {
            Objects.requireNonNull(key, "key");
            if (RESERVED.contains(key)) {
                throw new IllegalArgumentException("key \"" + key + "\" is reserved by the descriptor");
            }
            d.put(key, Objects.requireNonNull(value, () -> "value of key \"" + key + "\""));
            return this;
        }

        /**
         * A named slot of a composite element. What may go in is constrained by the factory's slot
         * method, not here; an empty list leaves the slot unrendered.
         */
        @SuppressWarnings("unchecked")
        public Descriptor<K> slot(String name, List<? extends Element<?>> items) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(items, "items");
            Map<String, List<Map<String, Object>>> slots =
                (Map<String, List<Map<String, Object>>>) d.computeIfAbsent("slots", k -> new LinkedHashMap<String, List<Map<String, Object>>>());
            slots.put(name, items.stream().map(Element::asMap).toList());
            return this;
        }

        /** Script dependency declared by the factory; the canvas renders it once per page. */
        @SuppressWarnings("unchecked")
        public Descriptor<K> requires(Element<Script>... scripts) {
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

        /** Marks the element as an illustration: its contents are not page structure (see {@link #assertSingleH1}). */
        public Descriptor<K> illustration() {
            d.put("illustration", true);
            return this;
        }

        Element<K> bare() {
            d.put("bare", true);
            return build();
        }

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
