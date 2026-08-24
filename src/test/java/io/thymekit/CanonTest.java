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
     * And nothing hidden is kept for nobody either. The mirror of the rule above, and it exists because
     * the rule above cannot see a field: a guard was moved out of one class into a shared one and left
     * the two constants it used behind, where they meant nothing and nothing complained.
     *
     * <p>Neither gate can complain, which is the point. A static constant is initialised when its class
     * loads, so its instructions are exercised by anything that touches the class at all — dead
     * constants read as fully covered, and there is no mutant of a value nobody reads. Only the reading
     * of a field counts here for the same reason: the initialiser writes to it, so a field that is only
     * ever written looks used to anything that counts accesses rather than reads.
     */
    @Test
    void nothingHiddenIsKeptForNobody() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields()
            .that().arePrivate().or().arePackagePrivate()
            .and().areDeclaredInClassesThat().resideInAPackage("io.thymekit")
            .should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaField>(
                "be read by something in the kit") {
                @Override
                public void check(com.tngtech.archunit.core.domain.JavaField field,
                                  com.tngtech.archunit.lang.ConditionEvents events) {
                    if (field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.SYNTHETIC)
                            || isFolded(field)) {
                        return;
                    }
                    boolean read = field.getAccessesToSelf().stream().anyMatch(access -> access.getAccessType()
                        == com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType.GET);
                    if (!read) {
                        events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(field,
                            field.getFullName() + " is hidden and read by nothing in the kit"));
                    }
                }
            })
            .because("a hidden value nobody reads is left over from something, and no gate can see it")
            .check(KIT);
    }

    /**
     * A compile-time constant — {@code static final} holding a string or a primitive — is copied into
     * every place that uses it, and the field is never read at run time. Nothing can tell such a
     * constant from a dead one by looking at bytecode, so the rule above says nothing about them rather
     * than saying something false. What it does see is every other hidden value, which is where the
     * defect it was written for lived: the two it missed were a compiled pattern and a set.
     */
    private static boolean isFolded(com.tngtech.archunit.core.domain.JavaField field) {
        var modifiers = field.getModifiers();
        return modifiers.contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC)
            && modifiers.contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
            && (field.getRawType().isPrimitive() || field.getRawType().getName().equals("java.lang.String"));
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
        var units = java.util.List.of("zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen");
        var tens = java.util.Map.of("twenty", 20, "thirty", 30, "forty", 40, "fifty", 50);
        String readme = java.nio.file.Files.readString(java.nio.file.Path.of("README.md"));
        var said = java.util.regex.Pattern.compile("([A-Za-z-]+) rules\\s+state what this package is").matcher(readme);
        assertThat(said.find()).as("the readme says how many rules the canon keeps").isTrue();

        String source = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/test/java/io/thymekit/CanonTest.java"));
        // the annotation where it stands, at the head of a method — the word elsewhere in this file is
        // this very line, and a rule that counted itself would be off by one
        int rules = (int) java.util.regex.Pattern.compile("(?m)^\\s+@Test$").matcher(source).results().count();
        String[] spelled = said.group(1).toLowerCase(java.util.Locale.ROOT).split("-");
        int counted = units.contains(spelled[0])
            ? units.indexOf(spelled[0])
            : tens.getOrDefault(spelled[0], -1) + (spelled.length > 1 ? units.indexOf(spelled[1]) : 0);
        assertThat(counted).as("the readme says \"%s rules\" and the canon keeps %d", said.group(1), rules)
            .isEqualTo(rules);
    }

    /**
     * And the verification this project asks for begins from nothing. The configuration processor reads
     * the metadata the kit writes off the compile classpath, and on a clean checkout it compiled before
     * that file had been copied there — so the jar shipped without the one thing an ide reads, and every
     * run here stayed green over the file an earlier build had left behind. A build that is only ever
     * incremental cannot say what it produces from an empty directory. So the gate this project names
     * for itself deletes first, and this rule keeps the deleting in it.
     */
    @Test
    void theVerificationThisProjectAsksForStartsFromNothing() throws java.io.IOException {
        String build = java.nio.file.Files.readString(java.nio.file.Path.of("build.gradle"));
        var gate = java.util.regex.Pattern
            .compile("tasks\\.register\\('verify'\\)\\s*\\{(.*?)\\n\\}", java.util.regex.Pattern.DOTALL)
            .matcher(build);
        assertThat(gate.find()).as("build.gradle names the run that judges a commit").isTrue();
        assertThat(gate.group(1)).as("the run that judges a commit deletes what the last one left")
            .contains("clean");
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

    /**
     * And no class names another element's adapter, either. The rule above watches template addresses;
     * a fragment name slipped past it for months — the currency of composition carried the string
     * {@code "headingEl"}, so the most general type in the kit knew one particular element by name, and
     * the outline check that needed it was living in the wrong house.
     *
     * <p>Two places may say the name: the element that owns the adapter, and a host asking for one
     * through {@code requireAdapter} — that is a guard saying what it will accept, which is the opposite
     * of knowing somebody's internals.
     */
    @Test
    void noClassNamesAnotherElementsAdapter() throws java.io.IOException {
        var templates = java.nio.file.Path.of("src/main/resources/templates/thymekit");
        var adapters = new java.util.TreeMap<String, String>();            // fragment -> its template address
        try (var files = java.nio.file.Files.list(templates)) {
            for (var file : files.filter(java.nio.file.Files::isRegularFile).toList()) {
                var declared = java.util.regex.Pattern.compile("th:fragment=\"([a-zA-Z0-9]+El)\\(")
                    .matcher(java.nio.file.Files.readString(file));
                while (declared.find()) {
                    adapters.put(declared.group(1), "thymekit/" + file.getFileName().toString().replace(".html", ""));
                }
            }
        }
        assertThat(adapters).as("the adapters the kit ships").isNotEmpty();

        java.util.List<String> knowing = new java.util.ArrayList<>();
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/io/thymekit"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                // comments say things about adapters and define nothing, javadoc examples above all
                String source = java.nio.file.Files.readString(file)
                    .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//.*", " ");
                for (var adapter : adapters.entrySet()) {
                    if (!source.contains("\"" + adapter.getKey() + "\"")
                        || source.contains("\"" + adapter.getValue() + "\"")) {
                        continue;                                          // not named, or named by its owner
                    }
                    boolean asAGuard = source.lines()
                        .filter(line -> line.contains("\"" + adapter.getKey() + "\""))
                        .allMatch(line -> line.contains("requireAdapter"));
                    if (!asAGuard) {
                        knowing.add(file.getFileName() + " names " + adapter.getKey());
                    }
                }
            }
        }
        assertThat(knowing).as("classes knowing an adapter that is not theirs").isEmpty();
    }

    /**
     * And an address the readme prints is an address that exists. The table names the adapter of every
     * element, and one of those names outlived its template by a week: the row still said
     * {@code md-section :: mdEl} after the markdown block had been split in two and the file renamed.
     * A reader would have looked for a fragment that was not there, and nothing said a word — the rule
     * above only asks whether a name appears, not whether it means anything.
     *
     * <p>It went wrong a second time in a way this rule did not see: an address written inside a code
     * fence, {@code thymekit/elements/heading :: headingEl}, naming a directory the project has never
     * had. The pattern looked only between backticks, so a prose reader was protected and a reader of
     * the examples was not. An address is an address wherever it is printed, and it is printed in two
     * spellings: joined by {@code ::} as a template says it, and split across the two keys of a
     * descriptor as the readme shows a stored page.
     */
    @Test
    void everyAddressTheReadmePrintsExists() throws java.io.IOException {
        var readme = java.nio.file.Files.readString(java.nio.file.Path.of("README.md"));
        var spellings = java.util.List.of(
            "([a-z][a-z0-9/-]*) :: ([a-zA-Z0-9]+)",
            "\"template\"\\s*:\\s*\"([a-z][a-z0-9/-]*)\",\\s*\"fragment\"\\s*:\\s*\"([a-zA-Z0-9]+)\"");
        java.util.List<String> absent = new java.util.ArrayList<>();
        int found = 0;
        for (var spelling : spellings) {
            var printed = java.util.regex.Pattern.compile(spelling).matcher(readme);
            while (printed.find()) {
                found++;
                var named = printed.group(1);
                var template = java.nio.file.Path.of("src/main/resources/templates",
                    named.startsWith("thymekit/") ? named + ".html" : "thymekit/" + named + ".html");
                if (!java.nio.file.Files.exists(template)) {
                    absent.add(named + ".html — no such template");
                } else if (!java.nio.file.Files.readString(template).contains(
                        "th:fragment=\"" + printed.group(2) + "(")) {
                    absent.add(named + ".html declares no " + printed.group(2));
                }
            }
        }
        assertThat(found).as("the readme prints the addresses of the kit's adapters").isNotZero();
        assertThat(absent).as("addresses the readme prints that lead nowhere").isEmpty();
    }

    /**
     * Structured data is printed by the head and by nothing else.
     *
     * <p>The element this rule stands behind was written the other way before it came here: it built a
     * json string in Java and its adapter printed the block itself. Two things follow from that shape,
     * and both are quiet. A descriptor ends up carrying finished markup, which is the one seam through
     * which markup could reach a page that was stored rather than composed — and the argument for
     * storing pages at all rests on there being no such seam. And a page with several such elements
     * prints several blocks, each having escaped the text on its own, in as many places as there are
     * authors.
     *
     * <p>So an element contributes a node as data and the canvas turns the page's contributions into
     * text once. This watches the fragments, because that is where the temptation is: printing a block
     * beside the markup it describes always looks like the tidy thing to do.
     */
    @Test
    void onlyTheHeadPrintsStructuredData() throws java.io.IOException {
        var templates = java.nio.file.Path.of("src/main/resources/templates/thymekit");
        java.util.List<String> printing = new java.util.ArrayList<>();
        try (var files = java.nio.file.Files.walk(templates)) {
            for (var file : files.filter(java.nio.file.Files::isRegularFile).toList()) {
                if (java.nio.file.Files.readString(file).contains("application/ld+json")) {
                    printing.add(file.getFileName().toString());
                }
            }
        }
        assertThat(printing).as("adapters printing a block of structured data").containsExactly("head.html");
    }

    /**
     * The kit throws only its own.
     *
     * <p>A consumer of this library has a rule of their own: a five hundred is a programming error and
     * something to be woken up for. That rule only works if everything the kit refuses <b>on purpose</b>
     * can be recognised and handled — then what is left uncaught really does mean nobody foresaw it.
     * Today a refusal of ours is an {@code IllegalArgumentException} like any other, and the only way
     * to tell it from their own code failing is to read the message.
     *
     * <p>So every refusal names a type of this kit's, and this watches the three ways a foreign one can
     * leave: thrown directly, thrown by {@code Objects.requireNonNull}, and thrown by an
     * {@code orElseThrow()} that was given nothing to throw. The third was not in the plan for this
     * work — it was found by writing the rule before the code, which is the argument for that order.
     *
     * <p>It is red while the work is done and green when it is finished, so the list it prints is the
     * list of what remains. A rule that starts red has shown it can fail; the check that it still can
     * once the code is clean is owed at the end, because a scan that stops matching goes green too.
     */
    @Test
    void theKitThrowsOnlyItsOwn() throws java.io.IOException {
        var ours = java.util.Set.of("ThymekitException", "MisuseException", "UnsoundPageException",
            "ContractBrokenException");
        // built, not only thrown: a refusal assembled by a helper and thrown elsewhere leaves just the
        // same, and one of them was doing exactly that when this pattern still said "throw new"
        var foreign = java.util.regex.Pattern.compile("new (\\w*(?:Exception|Error))\\(");
        var borrowed = java.util.regex.Pattern.compile("\\brequireNonNull\\(|orElseThrow\\(\\s*\\)");
        java.util.List<String> left = new java.util.ArrayList<>();
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/io/thymekit"))) {
            for (var file : files.filter(java.nio.file.Files::isRegularFile).sorted().toList()) {
                var lines = java.nio.file.Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    var thrown = foreign.matcher(lines.get(i));
                    while (thrown.find()) {
                        if (!ours.contains(thrown.group(1))) {
                            left.add(file.getFileName() + ":" + (i + 1) + " throws " + thrown.group(1));
                        }
                    }
                    if (borrowed.matcher(lines.get(i)).find()) {
                        left.add(file.getFileName() + ":" + (i + 1) + " throws somebody else's by borrowing it");
                    }
                }
            }
        }
        assertThat(left).as("refusals that a consumer cannot tell from their own code failing").isEmpty();
    }

    /**
     * And every one of them is named in the readme, where a consumer looks for what to catch.
     *
     * <p>The rule beside this one says the kit throws only its own; this one says a consumer can find
     * out what those are without reading the source. Together they make one sentence true — what the
     * kit does not name, it did not foresee — and a type added later without a row would quietly
     * shorten it.
     */
    @Test
    void everyFailureTheKitThrowsIsNamedInTheReadme() throws java.io.IOException {
        String readme = java.nio.file.Files.readString(java.nio.file.Path.of("README.md"));
        java.util.List<String> unnamed = new java.util.ArrayList<>();
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/io/thymekit"))) {
            for (var file : files.filter(java.nio.file.Files::isRegularFile).sorted().toList()) {
                String source = java.nio.file.Files.readString(file);
                var declared = java.util.regex.Pattern.compile("class (\\w+) extends ThymekitException")
                    .matcher(source);
                while (declared.find()) {
                    if (!readme.contains("`" + declared.group(1) + "`")) {
                        unnamed.add(declared.group(1));
                    }
                }
            }
        }
        assertThat(unnamed).as("failures a consumer would have to read the source to learn about").isEmpty();
    }

    /**
     * A refusal points at a call, and never at a noun. The place is a field so that a handler can route
     * on it and a person reading one line of a log knows which line of their own code to open;
     * "element", "caption", "slot item", "origin" answer neither question — they name what was wrong,
     * which the message says anyway. Twenty-eight places in this kit were written that way, and two
     * guards took a whole sentence as their place, which put a sentence into a field meant for routing.
     *
     * <p>A call is {@code Type.member}, with the argument at fault named when the call has one, and
     * {@code — one of them} when the trouble is inside a collection that was handed over. A constructor
     * is {@code Type(argument)}.
     *
     * <p>A refusal built with a variable is judged by the variable's name instead: it must be
     * {@code where}, which is what the kit calls a place handed down from a caller. That is not
     * pedantry about names — it caught a refusal whose place was the path of a file on the classpath,
     * which no rule reading literals could ever have seen.
     *
     * <p>A place assembled by concatenation is refused outright rather than judged. Two were written
     * that way and both put a name of the caller's inside the call — {@code Descriptor.with(title)} —
     * which reads like a parameter, cannot be routed on, and is not a call at all. What was wrong with
     * them belongs in the message, where a value is free to be anything.
     *
     * <p>Prose is skipped: the example in Tree's javadoc shows a consumer's own place, which follows
     * their names and not ours.
     */
    /**
     * The guards this rule knows to look at. A list, and therefore a thing that can fall behind — so the
     * rule below reads the source for methods that take a place and refuses to pass while one of them is
     * missing from here. Two blind spots were found by hand before that was written; there is no third.
     */
    private static final java.util.List<String> GUARDS = java.util.List.of("required", "settle", "check",
        "requireText", "requireTag", "requireAbsolute", "requireNavigable", "requireRenderable",
        "requireRenderableElement", "requireAdapter", "inRole", "address", "step", "require");

    @Test
    void everyPlaceARefusalPointsAtIsACall() throws java.io.IOException {
        var shape = java.util.regex.Pattern.compile(
            "[A-Z][A-Za-z0-9]*(?:\\.[a-z][A-Za-z0-9]*)?(?:\\([a-zA-Z0-9]*\\))?(?: — one of them)?");
        var built = java.util.regex.Pattern.compile(
            "new (?:Misuse|UnsoundPage|ContractBroken)Exception\\(\\s*"
            + "(?:\"([^\"]*)\"|([A-Za-z_][A-Za-z0-9_]*))\\s*[,)]");
        var guarded = java.util.regex.Pattern.compile(
            "\\b(?:" + String.join("|", GUARDS) + ")\\("
            + "[^;]*?,\\s*\"([^\"]*)\"\\s*\\)");
        java.util.List<String> nouns = new java.util.ArrayList<>();
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/io/thymekit"))) {
            for (var file : files.filter(java.nio.file.Files::isRegularFile).sorted().toList()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.stripLeading().startsWith("*") || line.stripLeading().startsWith("//")) {
                        continue;
                    }
                    var assembled = java.util.regex.Pattern
                        .compile("new (?:Misuse|UnsoundPage|ContractBroken)Exception\\(\\s*\"[^\"]*\"\\s*\\+")
                        .matcher(line);
                    if (assembled.find()) {
                        nouns.add(file.getFileName() + ":" + (i + 1) + " builds its place out of a value");
                    }
                    var refusal = built.matcher(line);
                    while (refusal.find()) {
                        String literal = refusal.group(1);
                        String variable = refusal.group(2);
                        boolean says = literal != null ? shape.matcher(literal).matches() : "where".equals(variable);
                        if (!says) {
                            nouns.add(file.getFileName() + ":" + (i + 1) + " refuses at "
                                + (literal != null ? "\"" + literal + "\"" : variable));
                        }
                    }
                    var given = guarded.matcher(line);
                    while (given.find()) {
                        if (!shape.matcher(given.group(1)).matches()) {
                            nouns.add(file.getFileName() + ":" + (i + 1) + " refuses at \"" + given.group(1) + "\"");
                        }
                    }
                }
            }
        }
        assertThat(nouns).as("places that name a thing instead of the call that refused").isEmpty();

        // and the list above is held to the source: a guard that takes a place and is not named there
        // would be a hole in this rule of exactly the kind that was found by hand twice
        java.util.List<String> unwatched = new java.util.ArrayList<>();
        var takesAPlace = java.util.regex.Pattern.compile("\\b(\\w+)\\([^;()]*String where[,)]");
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/io/thymekit"))) {
            for (var file : files.filter(java.nio.file.Files::isRegularFile).sorted().toList()) {
                if (file.getFileName().toString().endsWith("Exception.java")) {
                    continue;               // the family takes a place because it is where one ends up
                }
                var guard = takesAPlace.matcher(java.nio.file.Files.readString(file));
                while (guard.find()) {
                    if (!GUARDS.contains(guard.group(1))) {
                        unwatched.add(file.getFileName() + ": " + guard.group(1));
                    }
                }
            }
        }
        assertThat(unwatched).as("guards that take a place and are not watched by the rule above").isEmpty();
    }

    /**
     * And nothing is imported that the file does not use. An import is a claim about what a file needs,
     * and a stale one says the file still leans on something it stopped leaning on — this kit left
     * eight behind in a single afternoon, on the day it replaced somebody else's guard with its own.
     * There is no linter in this build to catch it, and a compiler never will: a dead import is legal.
     */
    @Test
    void nothingIsImportedThatIsNotUsed() throws java.io.IOException {
        var declared = java.util.regex.Pattern.compile("^import (?:static )?[\\w.]*?(\\w+);");
        java.util.List<String> stale = new java.util.ArrayList<>();
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/io/thymekit"))) {
            for (var file : files.filter(java.nio.file.Files::isRegularFile).sorted().toList()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(file);
                String body = lines.stream().filter(line -> !line.startsWith("import ")).reduce("", String::concat);
                for (int i = 0; i < lines.size(); i++) {
                    var name = declared.matcher(lines.get(i));
                    if (name.find() && !java.util.regex.Pattern.compile("\\b" + name.group(1) + "\\b")
                            .matcher(body).find()) {
                        stale.add(file.getFileName() + ":" + (i + 1) + " imports " + name.group(1));
                    }
                }
            }
        }
        assertThat(stale).as("imports the file has stopped needing").isEmpty();
    }

    /**
     * And the entry a release is written from counts them too. The readme says how many rules the canon
     * keeps and a rule holds it to that; the changelog says how many were added and what they are, and
     * nothing held that — so it went out of this very branch claiming twenty-seven while the canon kept
     * twenty-nine. Only the topmost entry is judged: the ones below it are history, and history is
     * right to say what was true when it was written.
     */
    @Test
    void theChangelogEntryBeingWrittenCountsTheRulesCorrectly() throws java.io.IOException {
        var spelled = java.util.List.of("zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen");
        var tens = java.util.Map.of("twenty", 20, "thirty", 30, "forty", 40, "fifty", 50);
        String changelog = java.nio.file.Files.readString(java.nio.file.Path.of("CHANGELOG.md"));
        String newest = changelog.split("(?m)^## ")[1];
        var said = java.util.regex.Pattern.compile("rules, ([a-z]+)(?:-([a-z]+))? now").matcher(newest);
        if (!said.find()) {
            return;                 // an entry that claims no count cannot be wrong about one
        }
        int rules = (int) java.util.regex.Pattern.compile("(?m)^\\s+@Test$")
            .matcher(java.nio.file.Files.readString(
                java.nio.file.Path.of("src/test/java/io/thymekit/CanonTest.java"))).results().count();
        int counted = tens.getOrDefault(said.group(1), -1)
            + (said.group(2) != null ? spelled.indexOf(said.group(2)) : 0);
        assertThat(counted).as("the entry says \"%s now\" and the canon keeps %d", said.group(0), rules)
            .isEqualTo(rules);
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
