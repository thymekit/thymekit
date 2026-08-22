/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The canon of the kit, as code. What a review had to notice, a rule notices instead: the seven passes
 * over the previous branch found nothing an interface could have caught, and everything here is a
 * statement about the shape of this package that a reader would otherwise have to hold in their head.
 *
 * <p>Rules about files — a fragment declared, a stylesheet imported, a class with a rule — live in
 * {@link ElementContractTest}, since no rule about java can see them.
 */
class CanonTest {

    private static final JavaClasses KIT = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("io.thymekit");

    /**
     * A factory is a namespace, not an object: there is nothing to instantiate and nothing to extend.
     * Stated as a condition rather than a list of names, because a list is a thing to forget — which is
     * exactly what this canon exists to stop. What the rule finds is a class with a public static
     * method handing out an element or a builder.
     *
     * <p>The classes Spring touches are deliberately not among them: the renderer is proxied for its
     * cache and subclassed for its heading ceiling, the dialect and the auto-configuration are held by
     * a container. They hand out no elements, so this rule never looks at them.
     */
    @Test
    void aFactoryIsANamespace_finalAndUninstantiable() {
        classes().should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaClass>(
            "be final with a private constructor, if they hand out elements") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass type,
                              com.tngtech.archunit.lang.ConditionEvents events) {
                boolean handsOutElements = type.getMethods().stream()
                    .filter(m -> m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
                    .filter(m -> m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC))
                    .anyMatch(m -> m.getRawReturnType().getName().equals(Element.class.getName())
                        || m.getRawReturnType().getSimpleName().equals("Builder"));
                if (!handsOutElements) {
                    return;
                }
                if (!type.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL)) {
                    events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(type,
                        type.getName() + " hands out elements and is not final"));
                }
                type.getConstructors().stream()
                    .filter(c -> !c.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE))
                    .forEach(c -> events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(type,
                        type.getName() + " hands out elements and can be instantiated")));
            }
        }).check(KIT);
    }

    /**
     * Whatever ends in an element says so in the type system. Asked of anything with a {@code build()}
     * that returns one — which is why the canvas is not among them: a page is rendered, not composed
     * into something else, and its terminal is {@code render(view)}.
     */
    @Test
    void whatEndsInAnElementSaysSo() {
        classes().should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaClass>(
            "implement Composable, if they have a build() that returns an element") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass type,
                              com.tngtech.archunit.lang.ConditionEvents events) {
                boolean buildsAnElement = type.getMethods().stream()
                    .anyMatch(m -> m.getName().equals("build")
                        && m.getRawParameterTypes().isEmpty()
                        && m.getRawReturnType().getName().equals(Element.class.getName()));
                if (buildsAnElement && !type.isAssignableTo(Composable.class)) {
                    events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(type,
                        type.getName() + " builds an element and does not say so: implement Composable"));
                }
            }
        }).check(KIT);
    }

    /** The currency is the only thing that hands a descriptor out; everything else speaks in elements. */
    @Test
    void onlyTheCurrencyExposesTheDescriptor() {
        methods().that().arePublic().and().areDeclaredInClassesThat().resideInAPackage("io.thymekit")
            .and().areDeclaredInClassesThat().doNotHaveSimpleName("Element")
            .should().notHaveRawReturnType(java.util.Map.class)
            .because("a descriptor leaves the kit through Element.asMap() and nowhere else")
            .check(KIT);
    }

    /**
     * Accepted, never stored, and settled at once: a place that takes a Composable turns it into an
     * element there and then, so nothing half-made is ever held and a maker that goes on being
     * configured cannot change what already took it.
     */
    @Test
    void composableIsAcceptedSettledAtOnceAndNeverKept() {
        noFields().should().haveRawType(Composable.class)
            .because("a Composable is settled where it is taken, so nothing half-made is ever held")
            .check(KIT);

        methods().that().areDeclaredInClassesThat().resideInAPackage("io.thymekit")
            .should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaMethod>(
                "settle every Composable they accept") {
                @Override
                public void check(com.tngtech.archunit.core.domain.JavaMethod method,
                                  com.tngtech.archunit.lang.ConditionEvents events) {
                    boolean takesComposable = method.getRawParameterTypes().stream()
                        .anyMatch(t -> t.getName().equals(Composable.class.getName()));
                    boolean isSettleItself = method.getName().equals("settle");
                    boolean hasNoBody = method.getModifiers()
                        .contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT);   // an interface promises, an implementation settles
                    boolean settles = method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTarget().getName().equals("settle")
                            || call.getTarget().getName().equals("inRole"));
                    if (takesComposable && !isSettleItself && !hasNoBody && !settles) {
                        events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(method,
                            method.getFullName() + " takes a Composable and never settles it"));
                    }
                }
            })
            .because("a place that accepts a Composable settles it there and then")
            .check(KIT);
    }

    /** The state of an element is its descriptor, and a descriptor cannot be reached from outside. */
    @Test
    void nothingPublicIsMutableState() {
        fields().that().arePublic().should().beFinal()
            .because("what is public is a value; what is mutable is private")
            .check(KIT);
    }

    /** The core does not know the showcase exists. */
    @Test
    void theCoreDoesNotDependOnItsOwnDemo() {
        noClasses().that().resideInAPackage("io.thymekit").and().resideOutsideOfPackage("io.thymekit.demo")
            .should().dependOnClassesThat().resideInAPackage("io.thymekit.demo")
            .because("the demo is a consumer of the kit, and a consumer is never a dependency")
            .check(KIT);
    }

    /**
     * No element names another element's address. Written by hand over the sources, because a template
     * path is a string and no rule about types can see it — and because this is the one mistake the kit
     * has actually made twice: a core element pointing at a fragment that had moved out of the core.
     */
    @Test
    void noElementNamesAnotherElementsTemplate() throws java.io.IOException {
        var main = java.nio.file.Path.of("src/main/java/io/thymekit");
        var address = java.util.regex.Pattern.compile("\"(thymekit/[a-z-]+)\"");
        java.util.Map<String, java.util.Set<String>> namedBy = new java.util.TreeMap<>();
        try (var files = java.nio.file.Files.walk(main)) {
            for (java.nio.file.Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                var m = address.matcher(java.nio.file.Files.readString(file));
                while (m.find()) {
                    namedBy.computeIfAbsent(m.group(1), k -> new java.util.TreeSet<>())
                        .add(file.getFileName().toString());
                }
            }
        }
        assertThat(namedBy).as("the java of the kit names some adapter addresses").isNotEmpty();
        namedBy.forEach((template, files) -> {
            if (!template.equals("thymekit/element")) {          // the dispatcher belongs to everyone
                assertThat(files).as("%s is named by more than one element: %s", template, files).hasSize(1);
            }
        });
    }

    /**
     * Nothing in the kit is written for a caller that does not exist. Asked of package-private methods
     * only: what is public may be there for a consumer, and what is hidden has no audience but the kit
     * itself — so a hidden method nobody calls is a plan, not a feature, and a plan belongs in a
     * branch. The import leaves tests out on purpose: a method alive only in its own test is exactly
     * the shape this looks for.
     */
    @Test
    void nothingHiddenIsWrittenForNobody() {
        methods().that().arePackagePrivate().and().areDeclaredInClassesThat().resideInAPackage("io.thymekit")
            .should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaMethod>(
                "be called by something in the kit") {
                @Override
                public void check(com.tngtech.archunit.core.domain.JavaMethod method,
                                  com.tngtech.archunit.lang.ConditionEvents events) {
                    if (method.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.SYNTHETIC)) {
                        return;
                    }
                    if (method.getAccessesToSelf().isEmpty()) {
                        events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(method,
                            method.getFullName() + " is hidden and called by nothing in the kit"));
                    }
                }
            })
            .because("a hidden method with no caller is a plan, and a plan is not shipped")
            .check(KIT);
    }

    /**
     * The rule above about holding a Composable, read once more: a collection of them is holding them
     * too. Written by hand over the generic type, because the erased one says only List.
     */
    @Test
    void noCollectionHoldsAComposableEither() {
        fields().should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaField>(
            "not hold a Composable, whatever it is wrapped in") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaField field,
                              com.tngtech.archunit.lang.ConditionEvents events) {
                if (field.getType().toString().contains(Composable.class.getName())) {
                    events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(field,
                        field.getFullName() + " holds a Composable: settle it where it is taken"));
                }
            }
        }).check(KIT);
    }

    /**
     * A space at the end of a line is a change nobody made, shown to whoever reads the next diff. The
     * kit's own sources are held to it, tests included: they are read as often as the main code.
     */
    @Test
    void sourcesCarryNoTrailingWhitespace() throws java.io.IOException {
        java.util.List<String> found = new java.util.ArrayList<>();
        for (String root : java.util.List.of("src/main/java", "src/test/java")) {
            try (var files = java.nio.file.Files.walk(java.nio.file.Path.of(root))) {
                for (java.nio.file.Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    java.util.List<String> lines = java.nio.file.Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        if (!lines.get(i).isEmpty() && lines.get(i).charAt(lines.get(i).length() - 1) == ' ') {
                            found.add(file + ":" + (i + 1));
                        }
                    }
                }
            }
        }
        assertThat(found).as("lines ending in a space").isEmpty();
    }

    /**
     * What the kit shares among its own elements, it shares with everyone. A hidden helper called from
     * one place is that place's business; called from two, it has stopped being a detail and become the
     * policy of a concept — and the kit tells whoever writes an element that their element is an element
     * like its own. A policy reachable only from inside makes that a half-truth: they get the vocabulary
     * and re-implement the behaviour, which is how two spellings of one rule begin.
     */
    @Test
    void whatMoreThanOneElementUsesIsPublic() {
        methods().that().arePackagePrivate().and().areDeclaredInClassesThat().resideInAPackage("io.thymekit")
            .should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaMethod>(
                "be public, being used by more than one element") {
                @Override
                public void check(com.tngtech.archunit.core.domain.JavaMethod method,
                                  com.tngtech.archunit.lang.ConditionEvents events) {
                    if (method.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.SYNTHETIC)) {
                        return;
                    }
                    String home = outermost(method.getOwner()).getName();
                    java.util.Set<String> callers = method.getAccessesToSelf().stream()
                        .map(access -> outermost(access.getOriginOwner()).getName())
                        .filter(name -> !name.equals(home))
                        .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
                    if (callers.size() > 1) {
                        events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(method,
                            method.getFullName() + " is shared by " + callers + " and hidden from everyone else"));
                    }
                }
            })
            .because("what two elements need is a policy, and a policy the kit publishes it uses")
            .check(KIT);
    }

    /** A nested class is part of what encloses it: Heading and Heading.Builder are one place. */
    private static com.tngtech.archunit.core.domain.JavaClass outermost(
            com.tngtech.archunit.core.domain.JavaClass type) {
        var enclosing = type.getEnclosingClass();
        return enclosing.map(CanonTest::outermost).orElse(type);
    }

    /**
     * The front door lists everything a page is built from, in one table. Named somewhere in the prose
     * is not the same as listed: whoever arrives reads the table to learn what the kit has, and an
     * element missing from it does not exist for them. Every one of these was found by a person
     * reading, which is the arrangement this canon exists to end.
     */
    @Test
    void everyElementAndEveryVocabularyIsListedInTheReadmeTable() throws java.io.IOException {
        String readme = elementTableOf(java.nio.file.Files.readString(java.nio.file.Path.of("README.md")));
        java.util.List<String> unmentioned = new java.util.ArrayList<>();
        for (var type : KIT) {
            if (!type.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)
                || type.getEnclosingClass().isPresent()
                || type.getPackageName().startsWith("io.thymekit.demo")) {
                continue;
            }
            boolean handsOutElements = type.getMethods().stream()
                .filter(m -> m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
                .filter(m -> m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC))
                .anyMatch(m -> m.getRawReturnType().getName().equals(Element.class.getName())
                    || m.getRawReturnType().getSimpleName().equals("Builder"));
            // the whole name, not a prefix of a longer one: `ElementContract` does not list Element
            var listed = java.util.regex.Pattern.compile("`" + type.getSimpleName() + "(?![A-Za-z0-9_])");
            if ((handsOutElements || type.isEnum()) && !listed.matcher(readme).find()) {
                unmentioned.add(type.getSimpleName());
            }
        }
        assertThat(unmentioned).as("elements and vocabularies missing from the readme's table").isEmpty();
    }

    /** The table of elements, and nothing else of the readme: its header row down to the first line that is not one. */
    private static String elementTableOf(String readme) {
        java.util.List<String> rows = new java.util.ArrayList<>();
        boolean inside = false;
        for (String line : readme.lines().toList()) {
            if (line.startsWith("| Element ")) {
                inside = true;
            } else if (inside && !line.startsWith("|")) {
                break;
            }
            if (inside) {
                rows.add(line);
            }
        }
        assertThat(rows).as("the readme's table of elements, found by its header row").isNotEmpty();
        return String.join("\n", rows);
    }

    /** The model belongs to the canvas: one place writes it, so a document knows what to expect. */
    @Test
    void onlyTheCanvasWritesTheModel() {
        noClasses().that().doNotBelongToAnyOf(PageModel.class).and().resideOutsideOfPackage("io.thymekit.demo")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.ui.Model")
            .because("what a document finds in the model is filled by the canvas alone")
            .check(KIT);
    }
}
