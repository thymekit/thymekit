/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Set;
import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IComment;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.IText;

/**
 * The stream handler behind {@link TidyDialect}: a whitespace node (or a run of them) becomes one
 * newline indented by the current nesting depth. Inside {@code pre/textarea/script/style} nothing is
 * touched.
 *
 * <p>Newlines inside a tag — attributes written across several lines in a template — are removed by
 * rebuilding the tag, since whitespace between attributes is insignificant. The rebuild only happens
 * when it is provably safe: every attribute value in double quotes, no minimised attributes.
 *
 * <p>What the handler sees, checked by rendering rather than assumed: one instance is given the whole
 * render, nested templates included — a {@code <pre>} in one file whose content comes from another
 * keeps that content untouched, which only happens if the same handler is still counting. And a render
 * that fails part-way leaves nothing behind for the next one. Both are pinned in
 * {@code TidyDialectTest}, because both are promises this class makes to a page.
 *
 * <p>Depth is kept in a thread local keyed by the render's context, and not in a field, so that the
 * promise holds even where the first sentence does not: an instance handed a second render, or reused
 * by a pool, starts from zero because the context it sees is a different object. Counting closes
 * instead would not do — a render that dies on an expression error never sends its end events, and its
 * depth would travel into whatever renders next on that thread. The reference is weak, so a finished
 * render is not kept alive by a thread that has moved on.
 *
 * <p>The class is public because Thymeleaf builds the post-processor by class name, not because a
 * consumer has any use for it.
 */
public final class WhitespaceHandler extends AbstractTemplateHandler {

    private static final Set<String> PRESERVE = Set.of("pre", "textarea", "script", "style");
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private int preserveDepth;

    @Override
    public void handleOpenElement(IOpenElementTag tag) {
        State s = state();
        flush(s, s.depth);
        if (isPreserved(tag.getElementCompleteName())) {
            preserveDepth++;
        }
        s.depth++;
        super.handleOpenElement(normalize(tag));
    }

    @Override
    public void handleCloseElement(ICloseElementTag tag) {
        State s = state();
        s.depth--;
        flush(s, s.depth);
        if (isPreserved(tag.getElementCompleteName())) {
            preserveDepth--;
        }
        super.handleCloseElement(tag);
    }

    @Override
    public void handleStandaloneElement(IStandaloneElementTag tag) {
        State s = state();
        flush(s, s.depth);
        super.handleStandaloneElement(normalize(tag));
    }

    @Override
    public void handleComment(IComment comment) {
        State s = state();
        flush(s, s.depth);
        super.handleComment(comment);
    }

    @Override
    public void handleText(IText text) {
        State s = state();
        if (preserveDepth == 0 && isFormattingWhitespace(text)) {
            s.pending = true;      // held: the indent depends on what comes next, an open or a close
            return;
        }
        flush(s, s.depth);
        super.handleText(text);
    }

    /** A tag whose attributes span lines becomes the same tag on one line, when that is safe. */
    private IOpenElementTag normalize(IOpenElementTag tag) {
        return spansLines(tag) && rebuildable(tag)
            ? getContext().getModelFactory().createOpenElementTag(
                tag.getElementCompleteName(), tag.getAttributeMap(), AttributeValueQuotes.DOUBLE, tag.isSynthetic())
            : tag;
    }

    private IStandaloneElementTag normalize(IStandaloneElementTag tag) {
        return spansLines(tag) && rebuildable(tag)
            ? getContext().getModelFactory().createStandaloneElementTag(
                tag.getElementCompleteName(), tag.getAttributeMap(), AttributeValueQuotes.DOUBLE,
                tag.isSynthetic(), tag.isMinimized())
            : tag;
    }

    /** An attribute placed on another line than the tag means the tag spans lines in the template. */
    private static boolean spansLines(IProcessableElementTag tag) {
        for (IAttribute a : tag.getAllAttributes()) {
            if (a.hasLocation() && tag.hasLocation() && a.getLine() != tag.getLine()) {
                return true;
            }
        }
        return false;
    }

    /** Rebuilding must not change meaning: minimised attributes and other quoting are left alone. */
    private static boolean rebuildable(IProcessableElementTag tag) {
        for (IAttribute a : tag.getAllAttributes()) {
            if (a.getValue() == null || a.getValueQuotes() != AttributeValueQuotes.DOUBLE) {
                return false;
            }
        }
        return true;
    }

    /** State of the current render; a different context means a new render. */
    private State state() {
        State s = STATE.get();
        if (s.render.get() != getContext()) {
            s.render = new WeakReference<>(getContext());
            s.depth = 0;
            s.pending = false;
        }
        return s;
    }

    /** Emits the held newline, indented to {@code depth}. */
    private void flush(State s, int depth) {
        if (!s.pending) {
            return;
        }
        s.pending = false;
        super.handleText(getContext().getModelFactory().createText(indent(depth)));
    }

    private static String indent(int depth) {
        return "\n" + " ".repeat(Math.clamp(depth, 0, State.MAX_INDENT_DEPTH) * TidyDialect.INDENT);
    }

    private static boolean isPreserved(String name) {
        return PRESERVE.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Whitespace-only and containing a newline: template indentation rather than significant space. */
    private static boolean isFormattingWhitespace(IText text) {
        boolean newline = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                newline = true;
            } else if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return newline;
    }

    /** Depth and held newline, shared by every template of one render. */
    private static final class State {
        private static final int MAX_INDENT_DEPTH = 24;   // deeper than this the indent stops growing
        private int depth;
        private boolean pending;
        /** Context of the current render; weak so a pooled thread does not keep it alive. */
        private WeakReference<Object> render = new WeakReference<>(null);
    }
}
