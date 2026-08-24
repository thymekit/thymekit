/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

/**
 * The page does not add up: more than one title, a gap in the heading levels, two things answering to
 * one name.
 *
 * <p>Alone among the three, this one can arrive from <b>data</b> — two children sharing a slug is a
 * state of a database, not a mistake in code — so what to do about it is the consumer's to choose:
 * fail, degrade, or send the visitor elsewhere. Alerting on it as if it were a defect would wake
 * somebody for a row somebody else typed.
 */
public final class UnsoundPageException extends ThymekitException {

    public UnsoundPageException(String where, String detail) {
        super(where, detail);
    }

    public UnsoundPageException(String where, String detail, Throwable cause) {
        super(where, detail, cause);
    }
}
