/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A page is a tree of elements, and this is what can be learned by walking it.
 *
 * <p>Its own class for the reason {@link Outline} and {@link Anchors} are theirs: a question about a
 * whole page is not a property of an element, and an element that answers one has been taught
 * something it has no business knowing. The traversal lived on the currency for a while, which is how
 * the outline came to live there too before it moved out; a third tenant made the pattern impossible
 * to keep calling an exception.
 *
 * <p>Written once because it is the kind of code that goes subtly wrong in a copy: a walk that misses
 * a branch finds nothing there and says nothing about it, which is the quietest way for a check to
 * stop checking. Public for the reason the guards are — a check of your own over a page of yours is
 * written the way the kit writes its own, and {@code Outline} and {@code Anchors} are the two worked
 * examples.
 */
public final class Tree {

    private Tree() {}

    /**
     * Every descriptor of a tree, whatever the tree is made of: elements, the maps they are, and
     * collections of either, at any depth. The visitor is handed each descriptor and answers whether to
     * go deeper into it — an illustration is a place one walker stops and another does not.
     *
     * <p>What is handed over is required, both of it: a walk over nothing finds nothing and says so to
     * nobody, and a check written over that passes while checking nothing. A hole <b>inside</b> a tree
     * is a different matter and is walked past — a page is entitled to carry a list with a gap in it,
     * and refusing to walk one would be this class deciding what a page may be made of.
     *
     * <pre>{@code
     * Tree.walk(page, descriptor -> {
     *     if (myKit.isPicture(descriptor) && descriptor.get("alt") == null) {
     *         throw new MisuseException("myKit.picture", "nothing said about it");
     *     }
     *     return true;
     * });
     * }</pre>
     */
    public static void walk(Object node, java.util.function.Predicate<Map<?, ?>> visit) {
        Guards.required(node, "Tree.walk(node)");
        Guards.required(visit, "Tree.walk(visit)");
        descend(node, visit);
    }

    /**
     * The walk itself, guarded once at the door rather than at every node: what is asked here is asked
     * of each map, each list and each element of a page, and a check that cannot fail after the first
     * node is a check paid for at every one of them.
     */
    private static void descend(@Nullable Object node, java.util.function.Predicate<Map<?, ?>> visit) {
        if (node instanceof Element<?> element) {
            descend(element.asMap(), visit);
        } else if (node instanceof Map<?, ?> map) {
            // a descriptor is a map that names an adapter; the others a tree holds — the slots of an
            // element, data of your own — are passed through rather than offered to the visitor
            if (!map.containsKey("fragment") || visit.test(map)) {
                for (Object value : map.values()) {
                    descend(value, visit);
                }
            }
        } else if (node instanceof Collection<?> items) {
            for (Object item : items) {
                descend(item, visit);
            }
        }
    }

    /**
     * Script dependencies of a tree — every {@code requires} in it, deduplicated by address in
     * traversal order. The canvas collects them and renders each once, so a consumer never wires a
     * script by hand.
     */
    public static List<Element<Element.Script>> assetsOf(Collection<?> roots) {
        Guards.required(roots, "Tree.assetsOf(roots)");
        LinkedHashMap<String, Element<Element.Script>> found = new LinkedHashMap<>();
        collectAssets(roots, found);
        return List.copyOf(found.values());
    }

    /**
     * What the elements of a tree say about themselves for machines, in the order the page carries
     * them, each thing said once: a page that shows the same trail twice does not describe it twice,
     * and a reader of the graph could not tell which copy was meant.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> describedBy(Collection<?> roots) {
        Guards.required(roots, "Tree.describedBy(roots)");
        List<Map<String, Object>> found = new ArrayList<>();
        walk(roots, descriptor -> {
            if (descriptor.get("describes") instanceof Map<?, ?> node && !found.contains(node)) {
                found.add((Map<String, Object>) node);
            }
            return true;
        });
        return List.copyOf(found);
    }

    @SuppressWarnings("unchecked")
    private static void collectAssets(Object node, Map<String, Element<Element.Script>> found) {
        walk(node, descriptor -> {
            if (descriptor.get("assets") instanceof List<?> declared) {
                for (Object a : declared) {
                    Map<String, Object> d = (Map<String, Object>) a;
                    String template = (String) d.get("template");
                    String fragment = (String) d.get("fragment");
                    found.putIfAbsent(template + " :: " + fragment, Element.script(template, fragment));
                }
            }
            return true;
        });
    }
}
