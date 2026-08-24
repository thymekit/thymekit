/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What {@link Rel} is for, written before it is that: the shared vocabulary of what a link says about
 * itself, and — the part it does not yet keep — the shared policy that goes with the vocabulary.
 *
 * <p>The kit tells a consumer that an element of theirs is an element like the kit's own, and package-info
 * calls this type "shared by every element that links". Shared means reachable: whoever writes a linking
 * element gets the five words, the guards, the order, the attribute value and the one safety rule that
 * must never be forgotten. Today they get the five words. That is the gap this file describes.
 *
 * <p>Written before the class it describes: the calls went through reflection while the shape did not
 * exist, so that a spec of what is missing would not take every other rule in the module down with it.
 * The shape exists now, and the calls are ordinary — a spec the compiler does not check is a spec a
 * rename can walk away from.
 */
class RelTest {

    /** The vocabulary itself: five values, each spelled the way it is written in the attribute. */
    @Test
    void everyValueSpellsItselfAsTheAttributeDoes() {
        assertThat(Rel.values()).extracting(Rel::token)
            .containsExactly("nofollow", "sponsored", "ugc", "noopener", "noreferrer");
    }

    /**
     * A set of values keeps the order it was given and says each thing once: rel="nofollow ugc" is what
     * the author wrote, not what a hash map happened to arrange.
     */
    @Test
    void valuesKeepTheirOrderAndAreSaidOnce() {
        assertThat(Rel.of(Rel.NOFOLLOW, Rel.UGC, Rel.NOFOLLOW)).containsExactly(Rel.NOFOLLOW, Rel.UGC);
        assertThat(Rel.of(Rel.UGC, Rel.NOFOLLOW)).containsExactly(Rel.UGC, Rel.NOFOLLOW);
    }

    /** Asking for values without naming one is a mistake, and so is a hole among them. */
    @Test
    void askingForNothingIsRefused() {
        assertThatThrownBy(() -> Rel.of()).isInstanceOf(MisuseException.class)
            .hasMessageContaining("name at least one");
        assertThatThrownBy(() -> Rel.of((Rel[]) null)).isInstanceOf(MisuseException.class);
        assertThatThrownBy(() -> Rel.of(Rel.UGC, null)).isInstanceOf(MisuseException.class);
    }

    /**
     * What comes back is a value. An element holds it, hands it to a fragment and hands it to the next
     * element; a set that can be added to afterwards is a descriptor that changes behind the page.
     */
    @Test
    void whatComesBackCannotBeChangedAfterwards() {
        Set<Rel> values = Rel.of(Rel.NOFOLLOW);
        assertThatThrownBy(() -> values.add(Rel.UGC)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Rel.forNewTab(List.of(Rel.UGC)).add(Rel.NOFOLLOW))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /** The attribute value: the tokens in order, one space between them, and nothing at all for nothing. */
    @Test
    void theAttributeValueIsTheTokensInOrder() {
        assertThat(Rel.tokens(Rel.of(Rel.NOFOLLOW, Rel.NOOPENER))).isEqualTo("nofollow noopener");
        assertThat(Rel.tokens(List.of())).isEmpty();
        assertThatThrownBy(() -> Rel.tokens(null)).isInstanceOf(MisuseException.class);
        assertThatThrownBy(() -> Rel.tokens(java.util.Arrays.asList(Rel.UGC, null)))
            .isInstanceOf(MisuseException.class);
    }

    /**
     * The rule the kit exists to remove from human memory: a link that opens a new tab carries noopener,
     * or the opened page reaches back into this one through window.opener. The kit already does this for
     * its own heading. A consumer writing their own linking element must be able to say the same thing
     * in one call — a safety that only the kit's elements enjoy is a safety the kit did not give.
     */
    @Test
    void aNewTabCarriesNoopenerForWhoeverOpensIt() {
        assertThat(Rel.forNewTab(List.of(Rel.NOFOLLOW))).containsExactly(Rel.NOFOLLOW, Rel.NOOPENER);
        assertThat(Rel.forNewTab(List.of())).containsExactly(Rel.NOOPENER);
        assertThat(Rel.forNewTab(List.of(Rel.NOOPENER, Rel.UGC))).containsExactly(Rel.NOOPENER, Rel.UGC);
        assertThatThrownBy(() -> Rel.forNewTab(null)).isInstanceOf(MisuseException.class);
        assertThatThrownBy(() -> Rel.forNewTab(java.util.Arrays.asList(Rel.UGC, null)))
            .isInstanceOf(MisuseException.class);

        List<Rel> given = new java.util.ArrayList<>(List.of(Rel.UGC));
        Rel.forNewTab(given);
        assertThat(given).as("what the caller handed over is theirs, and stays as they left it")
            .containsExactly(Rel.UGC);
    }

    /**
     * A refusal says where it was made, and two places are not one: the collection was not handed over,
     * or something inside it was not there. Whoever routes on {@code where()} can tell those apart, and
     * so can a person reading one line of a log — which is the whole reason the place is a field and not
     * a sentence.
     */
    @Test
    void aRefusalNamesTheCallThatMadeIt() {
        assertThat(placeOf(() -> Rel.of())).isEqualTo("Rel.of(values)");
        assertThat(placeOf(() -> Rel.of((Rel[]) null))).isEqualTo("Rel.of(values)");
        assertThat(placeOf(() -> Rel.of(Rel.UGC, null))).isEqualTo("Rel.of(values) — one of them");
        assertThat(placeOf(() -> Rel.forNewTab(null))).isEqualTo("Rel.forNewTab(values)");
        assertThat(placeOf(() -> Rel.forNewTab(java.util.Arrays.asList(Rel.UGC, null))))
            .isEqualTo("Rel.forNewTab(values) — one of them");
        assertThat(placeOf(() -> Rel.tokens(null))).isEqualTo("Rel.tokens(values)");
        assertThat(placeOf(() -> Rel.tokens(java.util.Arrays.asList(Rel.UGC, null))))
            .isEqualTo("Rel.tokens(values) — one of them");
    }

    /** The place of the refusal a call makes, and an assertion failure if it makes none. */
    private static String placeOf(Runnable call) {
        try {
            call.run();
        } catch (MisuseException refusal) {
            return refusal.where();
        }
        throw new AssertionError("nothing was refused");
    }

    /**
     * And all of it is reachable. A vocabulary a consumer can read and a policy a consumer cannot call
     * is half a gift: the kit keeps the guards, the order and the safety for its own two elements and
     * hands out five strings.
     */
    @Test
    void thePolicyIsAsPublicAsTheVocabulary() {
        assertThat(publicApi("of", Rel[].class)).as("Rel.of(Rel...) — the guards and the order").isNotNull();
        assertThat(publicApi("tokens", Collection.class)).as("Rel.tokens(...) — the attribute value").isNotNull();
        assertThat(publicApi("forNewTab", Collection.class)).as("Rel.forNewTab(...) — the safety").isNotNull();
    }

    /** Public means public: the spec lives in the same package, so only reflection can tell the two apart. */
    private static Method publicApi(String name, Class<?> parameter) {
        try {
            Method method = Rel.class.getDeclaredMethod(name, parameter);
            return Modifier.isPublic(method.getModifiers()) ? method : null;
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }
}
