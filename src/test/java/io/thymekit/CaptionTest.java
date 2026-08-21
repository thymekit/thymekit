/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract of {@link Caption}: four roles, descriptor shape, role guards and value semantics. */
class CaptionTest {

    @Test
    void roles_descriptorShape() {
        for (String role : List.of(Caption.EYEBROW, Caption.SUBTITLE, Caption.LABEL, Caption.META)) {
            Element<Caption> c = switch (role) {
                case Caption.EYEBROW -> Caption.eyebrow("t").build();
                case Caption.SUBTITLE -> Caption.subtitle("t").build();
                case Caption.LABEL -> Caption.label("t").build();
                default -> Caption.meta("t").build();
            };
            assertThat(c.template()).isEqualTo("fragments/thymekit/caption");
            assertThat(c.fragment()).isEqualTo("captionEl");
            assertThat(c.asMap()).containsEntry("role", role).containsEntry("text", "t");
            assertThat(Caption.roleOf(c)).isEqualTo(role);
        }
        assertThat(Caption.meta("x").build()).isEqualTo(Caption.meta("x").build());   // value
    }

    @Test @SuppressWarnings("unchecked")
    void requireRole_guard_typeAdapterRole() {
        assertThat(Caption.inRole(Caption.eyebrow("x"), Caption.EYEBROW, "ok").asMap())
            .containsEntry("role", Caption.EYEBROW);                       // a builder is settled by the guard
        assertThat(Caption.inRole(Caption.eyebrow("x").build(), Caption.EYEBROW, "ok").asMap())
            .containsEntry("text", "x");                                   // and an element passes through it
        assertThatThrownBy(() -> Caption.inRole(Caption.meta("x"), Caption.EYEBROW, "Hero.eyebrow accepts a caption"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eyebrow").hasMessageContaining("meta");
        Element<Caption> notCaption = (Element<Caption>) (Element<?>) Element.raw("t", "f").build();
        assertThatThrownBy(() -> Caption.inRole(notCaption, Caption.LABEL, "something"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("f");
        assertThatThrownBy(() -> Caption.inRole(null, Caption.LABEL, "x")).isInstanceOf(NullPointerException.class).hasMessageContaining("caption");
        assertThatThrownBy(() -> Caption.inRole(() -> null, Caption.LABEL, "x"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("built nothing");
        assertThatThrownBy(() -> Caption.roleOf(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Caption.label(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("text");
        assertThatThrownBy(() -> Caption.subtitle(null)).isInstanceOf(NullPointerException.class);
    }

    /** A day written for people, and the same day written for machines. */
    @Test
    void time_and_lang_areCarriedByTheDescriptor() {
        assertThat(Caption.meta("12 March 2026").time(LocalDate.of(2026, 3, 12)).build().asMap())
            .containsEntry("datetime", "2026-03-12").containsEntry("text", "12 March 2026");
        assertThat(Caption.meta("noon, sharp").time(Instant.parse("2026-03-12T12:00:00Z")).build().asMap())
            .containsEntry("datetime", "2026-03-12T12:00:00Z");
        assertThat(Caption.meta("x").time(Instant.parse("2026-03-12T12:00:00.123456Z")).build().asMap())
            .containsEntry("datetime", "2026-03-12T12:00:00.123456Z");        // as precise as the instant given
        assertThat(Caption.meta("x").time(LocalDate.of(2026, 3, 12))
            .time(Instant.parse("2026-03-12T12:00:00Z")).build().asMap())
            .containsEntry("datetime", "2026-03-12T12:00:00Z");               // the last call wins
        assertThat(Caption.subtitle("Adansonia digitata").lang("la").build().asMap()).containsEntry("lang", "la");
        assertThat(Caption.subtitle("Adansonia digitata").lang("pt-BR").build().asMap()).containsEntry("lang", "pt-BR");

        assertThatThrownBy(() -> Caption.meta("x").time((LocalDate) null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("day");
        assertThatThrownBy(() -> Caption.meta("x").time((Instant) null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("moment");
        assertThatThrownBy(() -> Caption.subtitle("x").lang(null)).isInstanceOf(NullPointerException.class);
        for (String wrong : List.of("", " ", "по-русски", "la la", "la_LA")) {
            assertThatThrownBy(() -> Caption.subtitle("x").lang(wrong))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("language tag");
        }
    }
}
