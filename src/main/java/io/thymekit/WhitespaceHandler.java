/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

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
 * when it is provably safe: every attribute value in double quotes, no minimised attributes. And only
 * when the spanning can be seen at all — an attribute a processor made carries no place in any file, so
 * a tag built entirely of those is left as it stands rather than rebuilt on a guess.
 *
 * <p>What the handler sees, checked by rendering rather than assumed: Thymeleaf builds one of these for
 * one render and gives it the whole of it, nested templates included — a {@code <pre>} in one file whose
 * content comes from another keeps that content untouched, which only happens if the same handler is
 * still counting. Nothing is carried between renders because there is nothing to carry it in: the
 * counters are ordinary fields of an object that is used once and dropped, so a render that dies on an
 * expression error leaves nothing behind by construction rather than by cleanup. Both facts are pinned
 * in {@code WhitespaceHandlerTest}, being promises this class makes to a page.
 *
 * <p>The class is public because Thymeleaf builds the post-processor by class name, not because a
 * consumer has any use for it.
 */
public final class WhitespaceHandler extends AbstractTemplateHandler {

    private static final Set<String> PRESERVE = Set.of("pre", "textarea", "script", "style");

    /** Deeper than this the indent stops growing: a page nested that far needs no wider margin. */
    private static final int MAX_INDENT_DEPTH = 24;

    /** How many preserved zones are open, how deep the nesting is, and whether a newline is held. */
    private int preserveDepth;
    private int depth;
    private boolean pending;

    @Override
    public void handleOpenElement(IOpenElementTag tag) {
        flush(depth);
        if (isPreserved(tag.getElementCompleteName())) {
            preserveDepth++;
        }
        depth++;
        super.handleOpenElement(normalize(tag));
    }

    @Override
    public void handleCloseElement(ICloseElementTag tag) {
        depth--;
        flush(depth);
        if (isPreserved(tag.getElementCompleteName())) {
            preserveDepth--;
        }
        super.handleCloseElement(tag);
    }

    @Override
    public void handleStandaloneElement(IStandaloneElementTag tag) {
        flush(depth);
        super.handleStandaloneElement(normalize(tag));
    }

    @Override
    public void handleComment(IComment comment) {
        flush(depth);
        super.handleComment(comment);
    }

    @Override
    public void handleText(IText text) {
        if (preserveDepth == 0 && isFormattingWhitespace(text)) {
            pending = true;        // held: the indent depends on what comes next, an open or a close
            return;
        }
        flush(depth);
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
            if (a.hasLocation() && a.getLine() != tag.getLine()) {
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

    /** Emits the held newline, indented to {@code depth}. */
    private void flush(int depth) {
        if (!pending) {
            return;
        }
        pending = false;
        super.handleText(getContext().getModelFactory().createText(indent(depth)));
    }

    private static String indent(int depth) {
        return "\n" + " ".repeat(Math.min(depth, MAX_INDENT_DEPTH) * TidyDialect.INDENT);
    }

    private static boolean isPreserved(String name) {
        return PRESERVE.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Whitespace-only and containing a newline: template indentation rather than significant space. */
    private static boolean isFormattingWhitespace(IText text) {
        boolean newline = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                newline = true;
            } else if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return newline;
    }
}
