/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

/**
 * The call was written wrong: a value the kit will not take, an argument that was not there, a state it
 * will not go into.
 *
 * <p>It should never reach production, and a consumer who treats a five hundred as something to be
 * woken up for is right to alert on this one — the fix is a line of their code, and the place says
 * which. Public, and meant to be thrown: a guard of yours refusing a bad argument is the same kind of
 * event as a guard of ours, and routes the same way for whoever handles it.
 */
public final class MisuseException extends ThymekitException {

    public MisuseException(String where, String detail) {
        super(where, detail);
    }

    public MisuseException(String where, String detail, Throwable cause) {
        super(where, detail, cause);
    }
}
