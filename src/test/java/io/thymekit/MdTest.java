/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The text of a page: markdown somebody wrote, or the empty state where nobody has yet.
 *
 * <p>This element is the one whose content comes from data rather than from code, and everything about
 * it follows from that. A caption refuses a blank text, because a blank caption is a programmer writing
 * nothing on purpose; this one treats it as an absence, because a column in a database is empty for
 * reasons nobody chose and a page falling over for it would be the kit punishing a consumer for their
 * data. What the consumer writes here — the hint, the affordance, the link policy — is code again, and
 * is held to the stricter rule.
 *
 * <p>What the markdown becomes is {@link MarkdownRenderer}'s, and what the adapter prints is the walk
 * over triples'. This file is about the descriptor and the guards.
 */
class MdTest {

    /** Text there is: carried as it was given, for the renderer to make html of. */
    @Test
    void textThereIsIsCarriedAsGiven() {
        assertThat(Md.of("**bold**").build().asMap())
            .containsEntry("template", "thymekit/md").containsEntry("fragment", "mdEl")
            .containsEntry("markdown", "**bold**")
            .as("a heading around it belongs to the section, not here").doesNotContainKey("heading");
    }

    /**
     * Nothing written yet is nothing written yet, whichever way the data says so. Null is a column that
     * was never filled and blank is one that was filled with nothing; a page cannot tell them apart and
     * neither does this.
     */
    @Test
    void nothingWrittenYetIsAnAbsenceHoweverTheDataSaysIt() {
        for (String nothing : new String[] {null, "", "   ", "\n\t "}) {
            assertThat(Md.of(nothing).emptyHint("Nothing written yet").build().asMap())
                .as("text: %s", nothing == null ? "null" : "\"" + nothing + "\"")
                .doesNotContainKey("markdown").containsEntry("emptyHint", "Nothing written yet");
        }
    }

    /** And with nothing written and no hint given, there is nothing here to show at all. */
    @Test
    void withoutAHintThereIsNothingToShow() {
        assertThat(Md.of(null).build().asMap())
            .doesNotContainKey("markdown").doesNotContainKey("emptyHint").doesNotContainKey("addAction");
    }

    /**
     * The hint is written by whoever composes the page, not by the data — so it is held to the rule the
     * kit holds every written text to: a hint with nothing in it is an empty box where an explanation
     * was meant to be.
     */
    @Test
    void theHintIsWrittenByAPersonAndIsHeldToIt() {
        assertThatThrownBy(() -> Md.of(null).emptyHint("  "))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
        assertThatThrownBy(() -> Md.of(null).emptyHint(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("emptyHint");
    }

    /**
     * An affordance stands beside the empty state and nowhere else. Where there is text, the adapter
     * prints it nowhere — so it is refused here rather than carried into a page that will not show it.
     */
    @Test
    void anAffordanceStandsBesideTheEmptyStateOrNowhere() {
        Element<Element.Raw> writeIt = Element.raw("fragments/my/actions", "actionsEl").build();

        assertThat(Md.of(null).emptyHint("Nothing written yet").addAction(writeIt).build().asMap())
            .containsEntry("addAction", writeIt.asMap());

        assertThatThrownBy(() -> Md.of("text").addAction(writeIt).build())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("nowhere to show it");
        assertThatThrownBy(() -> Md.of("   ").addAction(writeIt).build())
            .as("blank text is an absence, but an affordance still needs a hint to stand beside")
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("nowhere to show it");
        assertThatThrownBy(() -> Md.of(null).addAction(writeIt).build())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("nowhere to show it");
        assertThatThrownBy(() -> Md.of(null).emptyHint("x").addAction(Element.script("t", "myJs")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("script element");
        assertThatThrownBy(() -> Md.of(null).emptyHint("x").addAction(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("action");
    }

    /**
     * What the links of somebody else's text say about themselves. The kit has no default: a review and
     * an editor's article need opposite ones, and only the consumer knows which is on the page.
     */
    @Test
    void whatTheLinksOfSomebodyElsesTextSay() {
        assertThat(Md.of("[out](https://example.com/x)").linkRel(Rel.UGC, Rel.NOFOLLOW).build().asMap())
            .containsEntry("linkRel", "ugc nofollow");
        assertThat(Md.of("text").linkRel(Rel.UGC).linkRel(Rel.NOFOLLOW).build().asMap())
            .as("two calls accumulate, repetitions collapse").containsEntry("linkRel", "ugc nofollow");
        assertThat(Md.of("text").build().asMap()).doesNotContainKey("linkRel");

        assertThatThrownBy(() -> Md.of("text").linkRel())
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("without a value");
        assertThatThrownBy(() -> Md.of("text").linkRel(Rel.UGC, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("rel values");
    }

    /** A policy over a text that is not there applies to nothing, and is refused rather than carried. */
    @Test
    void aPolicyWithoutATextAppliesToNothing() {
        assertThatThrownBy(() -> Md.of(null).emptyHint("Nothing yet").linkRel(Rel.UGC).build())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("no text");
        assertThatThrownBy(() -> Md.of("  ").linkRel(Rel.UGC).build())
            .as("blank is the same absence here as anywhere in this element")
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("no text");
    }

    /** A block of text is a value: two written the same way are the same block. */
    @Test
    void aBlockOfTextIsAValue() {
        assertThat(Md.of("text").build()).isEqualTo(Md.of("text").build())
            .isNotEqualTo(Md.of("other").build());
        assertThat(new HashSet<>(List.of(Md.of("x").build(), Md.of("x").build()))).hasSize(1);
        assertThat(Md.of(null).build()).as("two absences are the same absence")
            .isEqualTo(Md.of("").build());
    }
}
