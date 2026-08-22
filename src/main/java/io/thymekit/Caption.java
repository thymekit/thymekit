/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Owner of the caption concept: short text attached to something. Not a heading (it stays out of the
 * outline) and not a link. The role lives in the factory name and has no default. Renders as
 * {@code <p class="tk-caption tk-caption--{role}">} — inside {@code <hgroup>} only {@code p} and
 * headings are allowed, and {@code p} is valid everywhere else too. Not a form {@code <label>}.
 *
 * <p>Beside the text a caption may carry what machines read: {@link Builder#time} for a date and
 * {@link Builder#lang} for a phrase in another language.
 *
 * <p>The text is required and may not be blank. A caption with nothing in it is a paragraph a reader
 * meets and gets nothing from — and one more thing for a screen reader to announce as silence.
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

    /**
     * The guard a host uses: a caption in the role it asked for, settled and handed back. A hero wants
     * an eyebrow above its title and a subtitle below it, and java says {@code Element<?>} at both
     * points because the marker is erased there — so the role is checked here, and the message says what
     * was wanted and what came.
     *
     * <p>Public, because a host of yours has the same problem the kit's hero has. Handing out the role
     * as a string to compare by hand would be handing out the weaker half of the instrument.
     */
    public static Element<Caption> inRole(Composable<Caption> caption, String role, String what) {
        Element<Caption> settled = Element.settle(caption, "caption");
        Element.requireAdapter(settled, "captionEl", what);
        if (!role.equals(settled.asMap().get("role"))) {
            throw new IllegalArgumentException(what + " in role \"" + role + "\" (got \"" + settled.asMap().get("role") + "\")");
        }
        return settled;
    }

    public static final class Builder implements Composable<Caption> {

        private final Element.Descriptor<Caption> b;

        private Builder(String role, String text) {
            Objects.requireNonNull(text, "text");
            if (text.isBlank()) {
                throw new IllegalArgumentException("text is blank: a caption with nothing to say is an "
                    + "empty box on the page, and a line a screen reader announces as silence");
            }
            this.b = Element.Descriptor.<Caption>of("thymekit/caption", "captionEl")
                .with("role", role)
                .with("text", text);
        }

        /**
         * The day this caption is about, written for machines beside the text written for people:
         * {@code meta("12 March 2026").time(LocalDate.of(2026, 3, 12))} prints
         * {@code <time datetime="2026-03-12">12 March 2026</time>}. The text stays yours — its
         * language, its format, its wording; the attribute is what a search engine and a reader for
         * the blind understand.
         */
        public Builder time(LocalDate day) {
            b.with("datetime", Objects.requireNonNull(day, "day").toString());
            return this;
        }

        /**
         * The moment this caption is about, in UTC and as precise as the instant given — for a
         * publication, an edit, an event. Calling either {@code time} after the other keeps the last.
         */
        public Builder time(Instant moment) {
            b.with("datetime", DateTimeFormatter.ISO_INSTANT.format(Objects.requireNonNull(moment, "moment")));
            return this;
        }

        /**
         * Language of this text when it differs from the page: a Latin name in a Russian catalogue, a
         * quotation, a brand written the way its own country writes it. A screen reader pronounces it
         * accordingly instead of reading it as broken page language.
         */
        public Builder lang(String languageTag) {
            b.with("lang", Element.requireTag(languageTag, "languageTag"));
            return this;
        }

        @Override
        public Element<Caption> build() {
            return b.build();
        }
    }
}
