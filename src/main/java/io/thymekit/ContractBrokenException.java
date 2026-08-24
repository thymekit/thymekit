/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

/**
 * The walk over a triple did not agree: an address resolving to nothing, a key declared and never
 * filled, a template that cannot be read.
 *
 * <p>It is thrown where the walk is run, which is a consumer's own tests, so it fails a build rather
 * than a page. The message carries every disagreement the walk found and not only the first — a
 * contract is read once and fixed once.
 */
public final class ContractBrokenException extends ThymekitException {

    public ContractBrokenException(String where, String detail) {
        super(where, detail);
    }

    public ContractBrokenException(String where, String detail, Throwable cause) {
        super(where, detail, cause);
    }
}
