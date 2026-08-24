/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * The walk over a triple, handed to whoever writes an element. The kit checks its own elements with
 * it; an element of yours is checked the same way, in your own test:
 *
 * <pre>{@code
 * @Test
 * void myElementsKeepTheContract() {
 *     ElementContract.of(Price.of("12.00", "EUR"), Badge.of("in stock"))
 *         .renderedBy(templateEngine)
 *         .styledBy("static/my/ui.css")
 *         .check();
 * }
 * }</pre>
 *
 * <p>What it looks at is what no compiler can:
 *
 * <ul>
 *   <li>the address points at a template that exists, and the template declares that fragment — with
 *       an argument for an adapter, without one for a script, since the dispatcher calls them
 *       differently;</li>
 *   <li>the adapter is named the way an adapter is named, and a script has not been put where an
 *       element belongs;</li>
 *   <li>the keys the adapter says it reads are the keys the factory puts in, and the slots it says it
 *       renders are the slots the factory fills;</li>
 *   <li>and when an engine is given: that the element renders something a browser would show, and that
 *       every key it carries and every slot it fills changes what comes out — data that leaves the page
 *       exactly as it found it is data travelling for nothing;</li>
 *   <li>and when stylesheets are given: that every class it prints has a rule in one of them.</li>
 * </ul>
 *
 * <p>Keys and slots are declared in the template, above the fragment, in comments Thymeleaf strips
 * before anything is rendered:
 *
 * <pre>{@code
 * <!--/* keys: heading *\/-->
 * <!--/* slots: items *\/-->
 * <section th:fragment="sectionEl(e)" ...>
 * }</pre>
 *
 * <p>A declaration belongs to the fragment beneath it and to no other — the window searched ends at the
 * previous fragment of the same file — and a fragment named inside a comment is prose, not a
 * declaration, in both directions: it neither declares anything nor moves that boundary.
 *
 * <p>Declare nothing and nothing is checked — the walk says so once rather than failing. Declare, and
 * a key carried by an element that its adapter never reads becomes a failure: data travelling for
 * nothing.
 *
 * <p>The other direction — a key an adapter reads that nothing here puts in — is only a defect if the
 * elements given were meant to cover the whole adapter. Say {@link #coveringEveryKey()} and it is
 * checked; that is a claim about the samples, not about the element, so it is asked for rather than
 * assumed. The kit makes that claim about its own.
 *
 * <p>Nothing here is required to write an element. It is the same walk the kit takes over its own, and
 * it fails with every problem at once rather than with the first.
 */
public final class ElementContract {

    /** An adapter is named for what it renders and ends in El; a second contract gets a version suffix. */
    private static final Pattern ADAPTER = Pattern.compile("^[a-z][A-Za-z0-9]*El(V\\d+)?$");

    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile("class=\"([^\"]+)\"");

    /** What an adapter says it reads: a comment above the fragment, stripped before rendering. */
    private static final Pattern DECLARED_KEYS = Pattern.compile("keys:\\s*([A-Za-z0-9_,\\s]+?)\\s*\\*/");

    /** The other half of the declaration: the slots the adapter renders, named the same way. */
    private static final Pattern DECLARED_SLOTS = Pattern.compile("slots:\\s*([A-Za-z0-9_,\\s]+?)\\s*\\*/");

    /** A comment declares; a comment does not define. Fragment lookup reads the template without them. */
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");

    /** Where one adapter's declaration ends: the fragment before the one being looked at. */
    private static final Pattern ANY_FRAGMENT = Pattern.compile("th:fragment=\"");

    private final List<Element<?>> elements;
    private final @Nullable ITemplateEngine engine;
    private final List<String> stylesheets;
    private final List<String> templateRoots;
    private final boolean everyKey;


    private ElementContract(List<Element<?>> elements, @Nullable ITemplateEngine engine,
                            List<String> stylesheets, List<String> templateRoots, boolean everyKey) {
        this.elements = elements;
        this.engine = engine;
        this.stylesheets = stylesheets;
        this.templateRoots = templateRoots;
        this.everyKey = everyKey;
    }

    /** The elements to walk: one live sample of each, as a page would build them. */
    public static ElementContract of(Composable<?>... elements) {
        Guards.required(elements, "ElementContract.of(elements)");
        List<Element<?>> settled = new ArrayList<>();
        for (Composable<?> element : elements) {
            settled.add(Element.settle(element, "ElementContract.of(elements) — one of them"));
        }
        if (settled.isEmpty()) {
            throw new MisuseException("ElementContract.of(elements)",
                "no elements to check: give the contract at least one");
        }
        return new ElementContract(settled, null, List.of(), List.of("templates/", ""), false);
    }

    /** Renders each element through the dispatcher, with the engine your application uses. */
    public ElementContract renderedBy(ITemplateEngine engine) {
        return new ElementContract(elements, Guards.required(engine, "ElementContract.renderedBy(engine)"),
            stylesheets, templateRoots, everyKey);
    }

    /**
     * Where your templates live on the classpath, when they are not under {@code templates/} — the
     * prefix your template resolver was given, {@code "views/"} or whatever it is. Without this the
     * contract looks under {@code templates/} and then at the address as written, which covers the
     * default and nothing else.
     */
    public ElementContract templatesUnder(String... classpathPrefixes) {
        List<String> roots = new ArrayList<>(List.of(
            Guards.required(classpathPrefixes, "ElementContract.templatesUnder(classpathPrefixes)")));
        roots.addAll(templateRoots);
        return new ElementContract(elements, engine, stylesheets, List.copyOf(roots), everyKey);
    }

    /**
     * Where the looks live, as classpath resources ({@code "static/my/ui.css"}). Given these, every
     * class an element prints must have a rule in one of them; without them the classes are not looked
     * at, since the contract has no way to guess where a consumer keeps its CSS.
     */
    public ElementContract styledBy(String... cssResources) {
        List<String> all = new ArrayList<>(stylesheets);            // said twice means both, never the last one only
        all.addAll(List.of(Guards.required(cssResources, "ElementContract.styledBy(cssResources)")));
        return new ElementContract(elements, engine, List.copyOf(all), templateRoots, everyKey);
    }

    /**
     * Also require that every key the adapters declare is put in by something here — a claim about the
     * elements given, not about the elements themselves: it says "these cover their adapters". Without
     * it a template branch nothing reaches goes unmentioned, which is right for a consumer checking one
     * element and wrong for a suite that means to check them all.
     */
    public ElementContract coveringEveryKey() {
        return new ElementContract(elements, engine, stylesheets, templateRoots, true);
    }

    /** Runs the walk. Throws with everything that is wrong, not with the first thing. */
    public void check() {
        List<String> failures = new ArrayList<>();
        Set<String> printedClasses = new LinkedHashSet<>();
        // what each adapter declares and what the elements given carried, gathered as the walk goes:
        // local, because a walk is one run and holds nothing between two of them
        Map<String, Set<String>> declaredByAdapter = new LinkedHashMap<>();
        Map<String, Set<String>> carriedByAdapter = new LinkedHashMap<>();
        Map<String, Set<String>> slotsDeclaredByAdapter = new LinkedHashMap<>();
        Map<String, Set<String>> slotsFilledByAdapter = new LinkedHashMap<>();
        for (Element<?> element : elements) {
            String address = element.template() + " :: " + element.fragment();
            if (!ADAPTER.matcher(element.fragment()).matches()) {
                failures.add(address + " — an adapter is named like myCardEl, or myCardElV2 for a second contract");
            }
            if (element.bare()) {
                failures.add(address + " — a script element does not belong among elements; declare it with requires()");
            }
            String template = read(element.template() + ".html");   // once per element, for both walks below
            declaresItself(element, template, address, failures);
            declarationMatches(element, template, address, failures,
                declaredByAdapter, carriedByAdapter, slotsDeclaredByAdapter, slotsFilledByAdapter);
            // requires() already refuses anything but a script
            for (Element<Element.Script> asset : Tree.assetsOf(List.of(element))) {
                declaresItself(asset, read(asset.template() + ".html"),
                    address + " — its script " + asset.template() + " :: " + asset.fragment(), failures);
            }

            if (engine != null) {
                String html = renderAndReport(element, engine, address, failures);
                if (html != null) {
                    printedClasses.addAll(classesIn(html));
                    everyKeyChangesTheOutput(element, engine, address, html, failures);
                    anAnchorIsPrintedAsOne(element, address, html, failures);
                }
            }
        }
        if (!stylesheets.isEmpty()) {
            checkClasses(printedClasses, failures);
        }
        if (everyKey) {
            declaredByAdapter.forEach((address, declared) -> {
                Set<String> carried = carriedByAdapter.getOrDefault(address, Set.of());
                declared.stream().filter(key -> !carried.contains(key)).forEach(key ->
                    failures.add(address + " — its adapter reads \"" + key + "\", and nothing given here puts"
                        + " it in: a branch of the template nothing reaches, or samples too poor to reach it"));
            });
            slotsDeclaredByAdapter.forEach((address, declared) -> {
                Set<String> filled = slotsFilledByAdapter.getOrDefault(address, Set.of());
                declared.stream().filter(slot -> !filled.contains(slot)).forEach(slot ->
                    failures.add(address + " — its adapter renders the slot \"" + slot + "\", and nothing given"
                        + " here fills it: a part of the element no sample ever put anything into"));
            });
        }
        if (!failures.isEmpty()) {
            throw new ContractBrokenException("ElementContract.check",
                "the element contract is broken in " + failures.size()
                + " place(s):\n  - " + String.join("\n  - ", failures));
        }
    }

    /**
     * The address points at a template that exists, and the template declares the fragment it names —
     * with an argument for an adapter, without one for a script. The dispatcher calls an adapter with
     * the descriptor and a script with nothing, so a fragment written the other way would fail at the
     * moment a page is served.
     */
    private void declaresItself(Element<?> element, @Nullable String template, String address, List<String> failures) {
        if (template == null) {
            failures.add(address + " — no template on the classpath at " + element.template() + ".html"
                + " (looked under " + String.join(", ", templateRoots.stream().map(r -> r.isEmpty() ? "the address itself" : r).toList()) + ")");
            return;
        }
        String declaration = "th:fragment=\"" + element.fragment() + (element.bare() ? "\"" : "(");
        if (!withoutComments(template).contains(declaration)) {
            failures.add(address + " — the template declares no fragment " + element.fragment()
                + (element.bare() ? ", written without an argument: the dispatcher calls a script with none"
                    : "(e); the dispatcher calls an adapter with the descriptor, so it takes one argument"));
        }
    }

    /**
     * The template with its comments blanked out and its length kept, so a position found here is a
     * position in the original. A comment names adapters — a javadoc example, a note about a neighbour —
     * and declares none of them; but the declarations of keys and slots live in comments too, which is
     * why they are read from the original text rather than from this.
     */
    private static String withoutComments(String template) {
        return COMMENT.matcher(template).replaceAll(match -> " ".repeat(match.group().length()));
    }

    /**
     * What an adapter says it reads against what the element actually carries — keys and slots alike.
     * Both directions are silent failures otherwise: data nobody reads, and a template branch nothing
     * can reach.
     *
     * <p>A declaration belongs to the fragment it stands above and to no other: the window searched
     * ends at the previous fragment of the same file, so an adapter that declares nothing inherits
     * nothing from the one before it and is simply left unchecked.
     */
    private void declarationMatches(Element<?> element, @Nullable String template, String address,
                                    List<String> failures,
                                    Map<String, Set<String>> declaredByAdapter,
                                    Map<String, Set<String>> carriedByAdapter,
                                    Map<String, Set<String>> slotsDeclaredByAdapter,
                                    Map<String, Set<String>> slotsFilledByAdapter) {
        if (template == null) {
            return;                                   // already reported by declaresItself
        }
        String masked = withoutComments(template);
        int fragmentAt = masked.indexOf("th:fragment=\"" + element.fragment() + "(");
        if (fragmentAt == -1) {
            return;                                   // same
        }
        String window = declarationWindow(template, masked, fragmentAt);
        Set<String> declaredKeys = named(DECLARED_KEYS, window);
        Set<String> declaredSlots = named(DECLARED_SLOTS, window);
        if (declaredKeys.isEmpty() && declaredSlots.isEmpty()) {
            return;                                   // nothing declared, nothing checked
        }
        Set<String> carried = new LinkedHashSet<>(element.asMap().keySet());
        carried.removeAll(Element.RESERVED);
        for (String key : carried) {
            if (!declaredKeys.contains(key)) {
                failures.add(address + " — carries the key \"" + key + "\" that its adapter does not read;"
                    + " declare it above the fragment or stop putting it in");
            }
        }
        Set<String> filled = element.slotNames();
        for (String slot : filled) {
            if (!declaredSlots.contains(slot)) {
                failures.add(address + " — fills the slot \"" + slot + "\" that its adapter does not render;"
                    + " declare it above the fragment or stop filling it");
            }
        }
        declaredByAdapter.computeIfAbsent(address, a -> new LinkedHashSet<>()).addAll(declaredKeys);
        carriedByAdapter.computeIfAbsent(address, a -> new LinkedHashSet<>()).addAll(carried);
        slotsDeclaredByAdapter.computeIfAbsent(address, a -> new LinkedHashSet<>()).addAll(declaredSlots);
        slotsFilledByAdapter.computeIfAbsent(address, a -> new LinkedHashSet<>()).addAll(filled);
    }

    /**
     * The text a declaration may stand in: after the previous fragment of the file, before this one.
     * Where the previous fragment is is read off the masked text — a fragment named in prose is prose —
     * and what stands in the window is read off the original, since a declaration is a comment.
     */
    private static String declarationWindow(String template, String masked, int fragmentAt) {
        Matcher previous = ANY_FRAGMENT.matcher(masked.substring(0, fragmentAt));
        int from = 0;
        while (previous.find()) {
            from = previous.end();
        }
        return template.substring(from, fragmentAt);
    }

    /** The last declaration of its kind in the window — the one nearest the fragment. */
    private static Set<String> named(Pattern declaration, String window) {
        Matcher m = declaration.matcher(window);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last == null ? Set.of() : new LinkedHashSet<>(List.of(last.trim().split("\\s*,\\s*")));
    }

    /** Renders one element through the single dispatcher, exactly as a page would. */
    private static @Nullable String renderAndReport(Element<?> element, ITemplateEngine engine,
                                                    String address, List<String> failures) {
        String html;
        try {
            html = render(engine, element.asMap());
        } catch (RuntimeException notRendered) {
            failures.add(address + " — does not render: " + notRendered.getMessage());
            return null;
        }
        if (html.isBlank() || !html.contains("<")) {
            failures.add(address + " — renders nothing a browser would show");
            return null;
        }
        return html;
    }

    private static String render(ITemplateEngine engine, Map<String, Object> descriptor) {
        Context context = new Context();
        context.setVariable("e", descriptor);
        return engine.process("thymekit/element", Set.of("render"), context);
    }

    /**
     * An element that says one of its keys is an address inside the page prints it as one. The anchor
     * check holds every page to that value; if the adapter never puts it in an {@code id}, the check
     * guards a name that is not in the document, and two pages could quietly answer to nothing.
     *
     * <p>Asked only where there is an engine to render with — a declaration is taken at its word until
     * something can look at what came out.
     */
    private static void anAnchorIsPrintedAsOne(Element<?> element, String address, String html,
                                               List<String> failures) {
        String anchor = Roles.anchorIn(element.asMap());
        if (anchor != null && !html.contains("id=\"" + anchor + "\"") && !html.contains("id='" + anchor + "'")) {
            failures.add(address + " — says \"" + anchor + "\" is an address inside the page and prints no"
                + " id with it: the anchor check would hold a page to a name no browser can find");
        }
    }

    private static Set<String> classesIn(String html) {
        Set<String> classes = new LinkedHashSet<>();
        Matcher m = CLASS_ATTRIBUTE.matcher(html);
        while (m.find()) {
            classes.addAll(List.of(m.group(1).trim().split("\\s+")));
        }
        return classes;
    }

    /**
     * Data that travels for nothing: a key the element carries and the adapter renders the same page
     * without. Declaring a key says the adapter reads it; printing the same html with it taken away
     * says it does not — a condition upstream of it, or a branch that never runs. Where taking the key
     * away breaks the adapter instead, the key is read, and loudly.
     */
    @SuppressWarnings("unchecked")
    private static void everyKeyChangesTheOutput(Element<?> element, ITemplateEngine engine, String address,
                                                 String whole, List<String> failures) {
        for (String slot : element.slotNames()) {
            if (element.slot(slot).isEmpty()) {
                continue;               // an empty slot renders nothing either way, and says nothing about the adapter
            }
            Map<String, Object> without = new LinkedHashMap<>(element.asMap());
            Map<String, Object> slots = new LinkedHashMap<>((Map<String, Object>) without.get("slots"));
            slots.remove(slot);
            without.put("slots", slots);
            if (rendersTheSame(engine, whole, without)) {
                failures.add(address + " — fills the slot \"" + slot + "\" and renders exactly the same"
                    + " without it: whatever a consumer puts there is put nowhere");
            }
        }
        Set<String> carried = new LinkedHashSet<>(element.asMap().keySet());
        carried.removeAll(Element.RESERVED);
        for (String key : carried) {
            Map<String, Object> without = new LinkedHashMap<>(element.asMap());
            without.remove(key);
            if (rendersTheSame(engine, whole, without)) {
                failures.add(address + " — carries the key \"" + key + "\" and renders exactly the same"
                    + " without it: the adapter prints it nowhere, or a condition keeps it off the page");
            }
        }
    }

    /** Whether the page is the same with a piece of the descriptor taken away; a page that will not render without it is not. */
    private static boolean rendersTheSame(ITemplateEngine engine, String whole, Map<String, Object> without) {
        try {
            return whole.equals(render(engine, without));
        } catch (RuntimeException readAndLoudly) {
            return false;
        }
    }

    /** Every class an element printed has a rule; comments are stripped, since a name in a header styles nothing. */
    private void checkClasses(Set<String> printed, List<String> failures) {
        StringBuilder css = new StringBuilder();
        for (String resource : stylesheets) {
            String text = read(resource);
            if (text == null) {
                failures.add("no stylesheet on the classpath at " + resource);
            } else {
                css.append(text.replaceAll("(?s)/\\*.*?\\*/", " "));
            }
        }
        for (String className : printed) {
            if (!Pattern.compile("\\." + Pattern.quote(className) + "(?![\\w-])").matcher(css).find()) {
                failures.add("class " + className + " is printed by an element and styled by none of the stylesheets given");
            }
        }
    }

    /** A classpath resource, tried under every root the caller named — the last of which is no root at all. */
    private @Nullable String read(String resource) {
        for (String root : templateRoots) {
            String candidate = root + resource;
            InputStream in = ElementContract.class.getClassLoader().getResourceAsStream(candidate);
            if (in != null) {
                return textOf(in, candidate);
            }
        }
        return null;
    }

    /**
     * The text of a resource that was found. Its own method because the failure it has to handle — a
     * resource that exists and cannot be read — is unreachable through a classpath and would otherwise
     * be a corner nothing ever enters and nothing ever checks. Swallowing it is not an option: a walk
     * that could not read a template would report the element as declaring nothing, which is a verdict
     * about the element rather than about the disk.
     */
    static String textOf(InputStream found, String candidate) {
        try (found) {
            return new String(found.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new ContractBrokenException("ElementContract.check", "cannot read " + candidate, unreadable);
        }
    }
}
