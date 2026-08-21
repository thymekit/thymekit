/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The canon of the looks, as code. Java rules cannot see a stylesheet and the element contract cannot
 * see who owns a handle, so what holds the CSS side of the triple together is written here.
 *
 * <p>The manifest is the source of the list: {@code ui.css} names every file of the kit, so nothing
 * below repeats that list by hand — a file added to the kit is checked because it was imported, not
 * because somebody remembered to add it in a second place.
 */
class CssCanonTest {

    private static final String DIR = "static/thymekit/";
    private static final Pattern IMPORT = Pattern.compile("@import url\\(\"([a-z0-9-]+\\.css)\"\\)");
    private static final Pattern READS = Pattern.compile("var\\((--tk-[a-z0-9-]+)");
    private static final Pattern STOCK_SCOPE = Pattern.compile("(?s)\\.tk-defaults[^{]*\\{([^}]*)}");
    private static final Pattern ASSIGNS = Pattern.compile("(--tk-[a-z0-9-]+)\\s*:");

    /** Every element file of the kit, in the order the manifest names them. */
    static List<String> manifest() {
        List<String> files = new ArrayList<>();
        Matcher m = IMPORT.matcher(source("ui.css"));
        while (m.find()) {
            files.add(m.group(1));
        }
        return files;
    }

    /** The kit's stylesheets as classpath resources — the manifest first, so a walk can be given them all. */
    static String[] stylesheets() {
        List<String> all = new ArrayList<>(List.of(DIR + "ui.css"));
        manifest().stream().distinct().map(file -> DIR + file).forEach(all::add);
        return all.toArray(String[]::new);
    }

    private static String source(String file) {
        try (InputStream in = CssCanonTest.class.getClassLoader().getResourceAsStream(DIR + file)) {
            assertThat(in).as("stylesheet %s", file).isNotNull();
            // a name inside a comment styles nothing and owns nothing
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/", " ");
        } catch (IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }

    private static Set<String> handlesRead(String css) {
        Set<String> read = new LinkedHashSet<>();
        Matcher m = READS.matcher(css);
        while (m.find()) {
            read.add(m.group(1));
        }
        return read;
    }

    /** What a file resets in the stock scope: the handles it says are its own. */
    private static Set<String> handlesReset(String css) {
        Set<String> reset = new LinkedHashSet<>();
        Matcher scope = STOCK_SCOPE.matcher(css);
        while (scope.find()) {
            Matcher assigned = ASSIGNS.matcher(scope.group(1));
            while (assigned.find()) {
                reset.add(assigned.group(1));
            }
        }
        return reset;
    }

    /** One file per element, named once: a manifest that says a thing twice has lost count of it. */
    @Test
    void theManifestNamesEachFileOnce() {
        assertThat(manifest()).doesNotHaveDuplicates();
    }

    /** A file in the directory and not in the manifest is served by nobody. */
    @Test
    void everyElementFileIsInTheManifest() throws Exception {
        var dir = java.nio.file.Path.of(getClass().getResource("/" + DIR).toURI());
        try (var files = java.nio.file.Files.list(dir)) {
            List<String> onDisk = files.map(f -> f.getFileName().toString())
                .filter(name -> name.endsWith(".css"))
                .filter(name -> !name.equals("ui.css") && !name.equals("demo.css"))   // manifest and showcase theme
                .sorted().toList();
            assertThat(manifest()).containsAll(onDisk);
        }
    }

    /**
     * A handle belongs to the element whose file resets it in the stock scope — that reset is the claim
     * of ownership. So the file that makes the claim reads the handle as well: one that resets a handle
     * it never reads is holding the stock scope of somebody else's rule, and the day that rule moves,
     * nothing says so.
     *
     * <p>The other direction is deliberately free: a host reads the handles of what it hosts — the hero
     * and the markdown block dress the headings inside them — and that is the tier the readme calls the
     * host's, not a mistake.
     */
    @Test
    void aFileResetsOnlyWhatItReads() {
        var orphans = new TreeMap<String, Set<String>>();
        for (String file : manifest().stream().distinct().toList()) {
            String css = source(file);
            Set<String> stray = new LinkedHashSet<>(handlesReset(css));
            stray.removeAll(handlesRead(css));
            if (!stray.isEmpty()) {
                orphans.put(file, stray);
            }
        }
        assertThat(orphans).as("handles reset in a file that never reads them").isEmpty();
    }

    /** And nothing is read that no file of the kit resets: an unreset handle leaks through the stock scope. */
    @Test
    void everyHandleReadIsResetByItsOwner() {
        Set<String> reset = new LinkedHashSet<>();
        Set<String> read = new LinkedHashSet<>();
        for (String file : manifest().stream().distinct().toList()) {
            reset.addAll(handlesReset(source(file)));
            read.addAll(handlesRead(source(file)));
        }
        assertThat(read).as("handles read by the kit and reset by no file of it").isSubsetOf(reset);
    }
}
