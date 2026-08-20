/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Objects;

/**
 * Owner of the caption concept: short text attached to something. Not a heading (it stays out of the
 * outline) and not a link. The role lives in the factory name and has no default. Renders as
 * {@code <p class="tk-caption tk-caption--{role}">} — inside {@code <hgroup>} only {@code p} and
 * headings are allowed, and {@code p} is valid everywhere else too. Not a form {@code <label>}.
 */
public final class Caption {

    private Caption() {}

    public static final String EYEBROW = "eyebrow";
    public static final String SUBTITLE = "subtitle";
    public static final String LABEL = "label";
    public static final String META = "meta";

    /** Sits above the H1 inside a heading group. */
    public static Builder eyebrow(String text) { return new Builder(EYEBROW, text); }
    /** Sits below the H1 inside a heading group. */
    public static Builder subtitle(String text) { return new Builder(SUBTITLE, text); }
    /** Labels an object, a group or a frame. */
    public static Builder label(String text) { return new Builder(LABEL, text); }
    /** Secondary line: a counter, a slug, card meta. */
    public static Builder meta(String text) { return new Builder(META, text); }

    /** Role of the caption, for host guards. */
    public static String roleOf(Element<Caption> caption) {
        return String.valueOf(Objects.requireNonNull(caption, "caption").asMap().get("role"));
    }

    /** Narrow-point guard: a caption in the required role. */
    static void requireRole(Element<Caption> caption, String role, String what) {
        Element.requireAdapter(Objects.requireNonNull(caption, "caption"), "captionEl", what);
        if (!role.equals(caption.asMap().get("role"))) {
            throw new IllegalArgumentException(what + " in role \"" + role + "\" (got \"" + caption.asMap().get("role") + "\")");
        }
    }

    public static final class Builder {

        private final Element.Descriptor<Caption> b;

        private Builder(String role, String text) {
            this.b = Element.Descriptor.<Caption>of("fragments/thymekit/caption", "captionEl")
                .with("role", role)
                .with("text", Objects.requireNonNull(text, "text"));
        }

        public Element<Caption> build() {
            return b.build();
        }
    }
}
