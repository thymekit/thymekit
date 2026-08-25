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
 *   <li><b>the guards over an element</b> — {@link #settle}, {@link #requireRenderable},
 *       {@link #requireRenderableElement}, {@link #requireAdapter}, all public, because an element of
 *       yours hosts other elements the same way the kit's own do. Guards over a <b>value</b> are
 *       {@link Guards}: none of those knows what an element is, and keeping them apart is what stops
 *       everything that guards anything from depending on the currency;</li>
 *   <li><b>and nothing about pages.</b> What a page or a subtree yields is {@link Tree}'s business,
 *       what a page asks of an element is {@link Roles}', and every other question whose answer needs
 *       more than one element belongs to whoever asks it.</li>
 * </ul>
 *
 * <p>Each is specified by a file of its own next to {@code ElementTest}. What used to be a fifth thing
 * here — the outline of a page — lives in {@link Outline} now: it is a property of a page rather than
 * of an element, and holding it here had taught the currency the name of one element's adapter.
 */
public final class Element<K> implements Composable<K> {

    /** Descriptor keys reserved by the engine; data cannot use them. */
    static final Set<String> RESERVED = Set.of("template", "fragment", "bare", "slots", "assets",
        "illustration", "describes", "roles");

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
        List<Map<String, Object>> items = slots == null ? null : slots.get(Guards.required(name, "Element.slot(name)"));
        return items == null ? List.of() : items;
    }

    /** Names of the slots this element fills, in the order they were filled; empty when it fills none. */
    @SuppressWarnings("unchecked")
    public Set<String> slotNames() {
        Map<String, List<Map<String, Object>>> slots = (Map<String, List<Map<String, Object>>>) m.get("slots");
        return slots == null ? Set.of() : new LinkedHashSet<>(slots.keySet());
    }

    /**
     * What a key of an element is, as far as the checks a page gets are concerned.
     *
     * <p>Not public, and that is the design rather than an omission: what a consumer needs are the
     * three verbs below and the three readers of {@link Roles}. This is the vocabulary of what may be
     * written into a descriptor, which is why it lives with the descriptor; the questions asked of it
     * live with the checks that ask them.
     *
     * <p>This is not {@code describes}: a contribution says what a page means to a machine outside, a
     * role says what a key means to this kit. One leaves in the HTML; the other never does.
     */
    enum Role { HEADING_LEVEL, ANCHOR, NAME }

    /** A template path and a fragment name, as a template resolver would accept them. */
    private static final Pattern TEMPLATE = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9/_.-]*");
    private static final Pattern FRAGMENT = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_]*");

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
        Element<K> built = Guards.required(composable, where).build();
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
        if (Guards.required(e, "Element.requireRenderable(element)").bare()) {
            throw new MisuseException(where, "a script element does not belong in the flow — "
                + "declare it as an element dependency via requires(), the canvas collects assets");
        }
    }

    /**
     * Runtime guard for narrow points, where the marker is erased: checks the adapter address of the
     * given element. Public on purpose — consumer elements guard their own narrow points the same way.
     */
    public static void requireAdapter(Element<?> e, String fragment, String where) {
        Guards.required(e, "Element.requireAdapter(element)");
        Guards.required(fragment, "Element.requireAdapter(fragment)");
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
            Guards.required(value, where);
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
            Guards.required(key, "Descriptor.with(key)");
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
         * numbers and booleans, and those are values already. An element is not one of them: it goes in
         * a slot, or in as its descriptor, and both are what every element the kit ships does. Put the
         * element itself and nothing would tell you — the dispatcher asks a descriptor for its address
         * and an element is not one, so that part of the page would simply not be there.
         *
         * <p>Copied rather than handed to {@code List.copyOf}: a page may legitimately carry a list with
         * a hole in it, and refusing that here would turn a rendering decision into an exception.
         */
        private static @Nullable Object snapshot(@Nullable Object value) {
            if (value instanceof Composable<?>) {
                throw new MisuseException("Descriptor.with(value)", "an element is not the value of a"
                    + " key — put it in a slot, or put its descriptor with element.asMap() where the"
                    + " adapter renders it in place");
            }
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
            Guards.required(node, "Descriptor.describes(node)");
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
         * The level of a heading this element carries, said and put in one call. An element that says
         * so takes part in the outline of every page it lands on, the kit's own heading and yours
         * alike — the check asks what a key is, never which adapter carries it.
         *
         * <p>Whether the level is one HTML has is judged by {@link Outline}, over the whole page,
         * along with the levels around it. Refusing a seventh here as well would leave that judgement
         * with nothing to reach it — and a page assembled from stored data would have no judge at all.
         */
        public Descriptor<K> headingLevel(String key, int level) {
            Guards.required(key, "Descriptor.headingLevel(key)");
            return says(key, Role.HEADING_LEVEL, level, "Descriptor.headingLevel");
        }

        /**
         * An address inside the page this element carries. Two things on one page answering to one
         * anchor is refused before the page renders, for your elements as for the kit's own.
         */
        public Descriptor<K> anchor(String key, String value) {
            Guards.required(key, "Descriptor.anchor(key)");
            return says(key, Role.ANCHOR, Guards.anchor(value, "Descriptor.anchor(value)"),
                "Descriptor.anchor");
        }

        /**
         * What to call this element in a message about the page — the words a person would recognise it
         * by. Nothing prints it; a refusal names it.
         */
        public Descriptor<K> name(String key, String value) {
            Guards.required(key, "Descriptor.name(key)");
            return says(key, Role.NAME, Guards.text(value, "Descriptor.name(value)"), "Descriptor.name");
        }

        /**
         * Says and puts in one call, which is the whole shape of it: a key can never be given a role
         * and left without a value, so nothing has to check that it wasn't. One role belongs to one
         * key — two keys claiming to be the anchor is a question with two answers, and a check would
         * have to pick one.
         */
        private Descriptor<K> says(String key, Role role, Object value, String where) {
            @SuppressWarnings("unchecked")
            Map<String, String> roles = (Map<String, String>) d.computeIfAbsent("roles",
                k -> new LinkedHashMap<String, String>());
            String said = roles.get(role.name());
            if (said != null && !said.equals(key)) {
                throw new MisuseException(where, "this element already says that \"" + said
                    + "\" is what it carries there: one role, one key");
            }
            roles.put(role.name(), key);
            return with(key, value);
        }

        /**
         * A named slot of a composite element. What may go in is constrained by the factory's slot
         * method, not here; an empty list leaves the slot unrendered.
         */
        @SuppressWarnings("unchecked")
        public Descriptor<K> slot(String name, List<? extends Composable<?>> items) {
            Guards.required(name, "Descriptor.slot(name)");
            Guards.required(items, "Descriptor.slot(items)");
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
            for (Element<Script> s : Guards.required(scripts, "Descriptor.requires(scripts)")) {
                if (!Guards.required(s, "Descriptor.requires(scripts) — one of them").bare()) {
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
            if (copy.get("roles") instanceof Map<?, ?> roles) {
                copy.put("roles", Collections.unmodifiableMap(new LinkedHashMap<>((Map<String, String>) roles)));
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
