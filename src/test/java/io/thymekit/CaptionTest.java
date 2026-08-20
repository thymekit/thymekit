/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Contract of {@link Caption}: four roles, descriptor shape, role guards and value semantics. */
class CaptionTest {

    @Test
    void roles_descriptorShape() {
        for (String role : java.util.List.of(Caption.EYEBROW, Caption.SUBTITLE, Caption.LABEL, Caption.META)) {
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
        Caption.requireRole(Caption.eyebrow("x").build(), Caption.EYEBROW, "ok");
        assertThatThrownBy(() -> Caption.requireRole(Caption.meta("x").build(), Caption.EYEBROW, "Hero.eyebrow accepts a caption"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eyebrow").hasMessageContaining("meta");
        Element<Caption> notCaption = (Element<Caption>) (Element<?>) Element.raw("t", "f").build();
        assertThatThrownBy(() -> Caption.requireRole(notCaption, Caption.LABEL, "something"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("f");
        assertThatThrownBy(() -> Caption.requireRole(null, Caption.LABEL, "x")).isInstanceOf(NullPointerException.class).hasMessageContaining("caption");
        assertThatThrownBy(() -> Caption.roleOf(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Caption.label(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("text");
        assertThatThrownBy(() -> Caption.subtitle(null)).isInstanceOf(NullPointerException.class);
    }
}
