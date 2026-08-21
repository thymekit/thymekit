/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

/** Mount point of the showcase: the terminal returns the library view name and fills the canvas model. */
class DemoTest {

    @Test
    void page_returnsLibraryView_andFillsCanvasModel() {
        var model = new ExtendedModelMap();
        assertThat(Demo.page(model)).isEqualTo("thymekit/demo");
        assertThat(model.asMap()).containsEntry("pageTitle", "thymekit — element showcase")
            .containsKeys("head", "page", "assets");
        @SuppressWarnings("unchecked")
        var page = (java.util.Map<String, Object>) model.get("page");
        assertThat(page).containsEntry("fragment", "canvasEl");
        assertThat((java.util.List<?>) page.get("elements")).hasSizeGreaterThan(1);   // hero plus sections
    }

    @Test
    void page_requiresModel() {
        assertThatThrownBy(() -> Demo.page(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("model");
    }
}
