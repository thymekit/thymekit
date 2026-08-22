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
     * cache, the dialect and the auto-configuration are held by a container. They hand out no elements,
     * so this rule never looks at them.
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

    /**
     * A cached answer is only as good as the question it was filed under, and the question is everything
     * that decides the answer: the arguments, and the object that was asked. Two rules, both learned the
     * hard way.
     *
     * <p>Arguments by position ({@code #p0}), never by name. A library jar carries parameter names only
     * if it was compiled with {@code -parameters}; where it was not, {@code #source} evaluates to null
     * for every call, every text lands on one entry, and the second page shows the text of the first.
     *
     * <p>And {@code #root.target} wherever the object has state of its own. Two instances configured
     * differently are two answers to the same arguments, and a key that cannot tell them apart hands one
     * of them the other's work.
     */
    @Test
    void aCachedAnswerIsFiledUnderTheWholeQuestion() {
        var byName = java.util.regex.Pattern.compile("#(?!p\\d|root\\b)[a-zA-Z]");
        java.util.List<String> wrong = new java.util.ArrayList<>();
        for (var type : KIT) {
            for (var method : type.getMethods()) {
                var cacheable = method.tryGetAnnotationOfType(
                    org.springframework.cache.annotation.Cacheable.class);
                if (cacheable.isEmpty()) {
                    continue;
                }
                String key = cacheable.get().key();
                if (key.isBlank()) {
                    wrong.add(method.getFullName() + " — cached under a key nobody wrote");
                } else if (byName.matcher(key).find()) {
                    wrong.add(method.getFullName() + " — names an argument instead of its position: " + key);
                }
                boolean hasState = type.getFields().stream().anyMatch(
                    f -> !f.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC));
                if (hasState && !key.contains("#root.target")) {
                    wrong.add(method.getFullName() + " — the object it was asked is not in the key: " + key);
                }
            }
        }
        assertThat(wrong).as("cached methods filed under an incomplete question").isEmpty();
    }

    /**
     * Nothing here outlives the call that made it. A page is rendered by objects a framework builds for
     * one render and drops; state kept beside them, in a thread, belongs to whatever runs on that thread
     * next — a pooled thread, a second render, an exception that never sent its closing events. Where a
     * counter is needed, it is a field of the short-lived object doing the counting, and the counting
     * ends when the object does.
     *
     * <p>Written after a handler carried a thread local, a weak reference and a class to hold them, all
     * to survive being reused — which the engine that builds it never does.
     */
    @Test
    void nothingKeepsStateBesideTheCallThatMadeIt() {
        noFields().should().haveRawType(ThreadLocal.class)
            .because("state that outlives a call belongs to whoever runs next, not to us")
            .check(KIT);
    }

    /**
     * Every class of the kit is described by a spec of its own. A class nobody wrote one for is a class
     * whose behaviour is whatever it happens to do — the last one found that way had none, and only a
     * person reading it noticed. What is red here is not a defect but a queue: it is the list of classes
     * this project has yet to take through, and it shortens by one each time one is taken.
     */
    @Test
    void everyClassOfTheKitHasASpec() throws java.io.IOException {
        java.util.List<String> unspecified = new java.util.ArrayList<>();
        for (var type : KIT) {
            if (!type.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)
                || type.getEnclosingClass().isPresent()) {
                continue;
            }
            var spec = java.nio.file.Path.of("src/test/java",
                type.getPackageName().replace('.', '/'), type.getSimpleName() + "Test.java");
            if (!java.nio.file.Files.exists(spec)) {
                unspecified.add(type.getSimpleName());
            }
        }
        assertThat(unspecified).as("classes still waiting for a spec of their own").isEmpty();
    }

    /**
     * And the readme counts them right. The canon is the part of this project a reader is asked to take
     * on trust, so the sentence that introduces it is checked like anything else — a list that grew by
     * five while its own first word stayed at eight is how a document stops being read.
     */
    @Test
    void theReadmeCountsTheRulesCorrectly() throws java.io.IOException {
        var written = java.util.List.of("zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen", "twenty");
        String readme = java.nio.file.Files.readString(java.nio.file.Path.of("README.md"));
        var said = java.util.regex.Pattern.compile("([A-Za-z]+) rules\\s+state what this package is").matcher(readme);
        assertThat(said.find()).as("the readme says how many rules the canon keeps").isTrue();

        String source = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/test/java/io/thymekit/CanonTest.java"));
        // the annotation where it stands, at the head of a method — the word elsewhere in this file is
        // this very line, and a rule that counted itself would be off by one
        int rules = (int) java.util.regex.Pattern.compile("(?m)^\\s+@Test$").matcher(source).results().count();
        assertThat(written.indexOf(said.group(1).toLowerCase(java.util.Locale.ROOT)))
            .as("the readme says \"%s rules\" and the canon keeps %d", said.group(1), rules)
            .isEqualTo(rules);
    }

    /**
     * One call spelled twice says the same thing about absence. A bridge that forwards to a renderer, a
     * builder that forwards to a guard: where the same name takes the same arguments in two places, both
     * places agree on which of them may be missing. Otherwise the package is {@code @NullMarked} and one
     * of the two is lying — the one that documents an absence it does not declare.
     */
    @Test
    void oneCallSpelledTwiceSaysTheSameAboutAbsence() {
        record Signature(String name, java.util.List<Class<?>> parameters) {}
        var nullableAt = new java.util.LinkedHashMap<Signature, java.util.Map<String, java.util.Set<Integer>>>();
        for (var type : KIT) {
            for (var method : type.reflect().getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                    continue;
                }
                var absent = new java.util.LinkedHashSet<Integer>();
                var parameters = method.getAnnotatedParameterTypes();
                for (int i = 0; i < parameters.length; i++) {
                    if (parameters[i].isAnnotationPresent(org.jspecify.annotations.Nullable.class)) {
                        absent.add(i);
                    }
                }
                nullableAt.computeIfAbsent(
                        new Signature(method.getName(), java.util.List.of(method.getParameterTypes())),
                        signature -> new java.util.LinkedHashMap<>())
                    .put(type.getSimpleName(), absent);
            }
        }
        var disagreeing = nullableAt.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .filter(e -> new java.util.HashSet<>(e.getValue().values()).size() > 1)
            .map(e -> e.getKey().name() + e.getKey().parameters() + " " + e.getValue())
            .toList();
        assertThat(disagreeing).as("the same call, spelled twice, disagreeing about what may be absent").isEmpty();
    }

    /**
     * What is not meant to be extended says so. A class left open is an invitation, and this kit means
     * exactly one: the bean Spring proxies for its cache. Even the auto-configuration is final —
     * {@code @AutoConfiguration} carries {@code proxyBeanMethods = false}, so nothing subclasses it, and
     * the exemption it once had here was a guess that turned out to be wrong.
     */
    @Test
    void whatIsNotMeantToBeExtendedSaysSo() {
        java.util.List<String> open = new java.util.ArrayList<>();
        for (var type : KIT) {
            var reflected = type.reflect();
            if (!java.lang.reflect.Modifier.isPublic(reflected.getModifiers())
                || reflected.isInterface() || reflected.isEnum() || reflected.isAnnotation()
                || reflected.getEnclosingClass() != null
                || java.lang.reflect.Modifier.isFinal(reflected.getModifiers())
                || java.lang.reflect.Modifier.isAbstract(reflected.getModifiers())) {
                continue;
            }
            boolean proxiedForItsCache = java.util.Arrays.stream(reflected.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(org.springframework.cache.annotation.Cacheable.class));
            if (!proxiedForItsCache) {
                open.add(reflected.getSimpleName());
            }
        }
        assertThat(open).as("public classes left open with nothing to extend them").isEmpty();
    }

    /**
     * A name the kit puts into somebody else's registry carries the kit's own. A dialect's name is what
     * an engine's configuration and its error messages call it, and it sits in a namespace shared with
     * every other library a consumer has added — "markdown" alone is a claim on a word that belongs to
     * nobody. Read from the sources, because the name is an argument to a constructor and no rule about
     * types can see it.
     */
    @Test
    void aNameTheKitPutsInSomebodyElsesRegistryCarriesItsOwn() throws java.io.IOException {
        var declaration = java.util.regex.Pattern.compile("(?s)extends AbstractDialect.*?super\\(\"([^\"]+)\"\\)");
        java.util.List<String> foreign = new java.util.ArrayList<>();
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/io/thymekit"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                var named = declaration.matcher(java.nio.file.Files.readString(file));
                while (named.find()) {
                    if (!named.group(1).startsWith("thymekit-")) {
                        foreign.add(file.getFileName() + " calls itself \"" + named.group(1) + "\"");
                    }
                }
            }
        }
        assertThat(foreign).as("dialects named without saying whose they are").isEmpty();
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
