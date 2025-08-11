package uk.g.h.rules.watch;

import uk.g.h.rules.toggle.JsonBackedRuleToggleService;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.*;


import uk.g.h.rules.runtime.DroolsService;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.nio.file.StandardWatchEventKinds.*;

import uk.g.h.rules.runtime.DroolsService;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static java.nio.file.StandardWatchEventKinds.*;
package uk.g.h.rules.watch;

import uk.g.h.rules.runtime.DroolsService;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static java.nio.file.StandardWatchEventKinds.*;

public final class UnifiedWatcherService implements Runnable {
    private final Path rulesDir;
    private final Path togglesFile;
    private final DroolsService drools;
    private final JsonBackedRuleToggleService toggles;
    private final Supplier<Object[]> factsSupplier;

    private static final Set<String> RULE_EXTS = Set.of(".drl", ".dsl", ".dslr");
    private static final Set<String> IGNORE_SUFFIX = Set.of("~", ".swp", ".swo", ".tmp", ".part", ".bak",
            ".___jb_tmp___", ".___jb_old___"); // JetBrains safe-write

    // Debounce + throttle
    private static final long QUIET_MS = 600;   // trailing debounce window
    private static final long MIN_INTERVAL_MS = 1200; // throttle across batches

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rules-toggle-batcher");
        t.setDaemon(true);
        return t;
    });

    // Single batch runnable for both rules and toggles
    private volatile ScheduledFuture<?> batchFuture;
    private final Object batchLock = new Object();
    private volatile boolean rulesDirty = false;
    private volatile boolean togglesDirty = false;
    private final AtomicLong lastRun = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public UnifiedWatcherService(Path rulesDir,
                                 Path togglesFile,
                                 DroolsService drools,
                                 JsonBackedRuleToggleService toggles,
                                 Supplier<Object[]> factsSupplier) {
        this.rulesDir = rulesDir;
        this.togglesFile = togglesFile;
        this.drools = drools;
        this.toggles = toggles;
        this.factsSupplier = factsSupplier;
    }

    @Override
    public void run() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            rulesDir.register(ws, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            togglesFile.getParent().register(ws, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

            System.out.println("👀 Watching rules in: " + rulesDir.toAbsolutePath());
            System.out.println("👀 Watching toggles: " + togglesFile.toAbsolutePath());

            for (;;) {
                WatchKey key = ws.take();
                Path watchedDir = (Path) key.watchable();

                for (WatchEvent<?> evt : key.pollEvents()) {
                    if (evt.kind() == OVERFLOW) continue;

                    Path changed = watchedDir.resolve((Path) evt.context());
                    if (isIgnored(changed)) continue;

                    if (isToggleFile(changed)) {
                        markTogglesDirty();
                    } else if (isRuleFile(changed)) {
                        markRulesDirty();
                    }
                }
                if (!key.reset()) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Watcher interrupted, exiting.");
        } catch (IOException e) {
            throw new RuntimeException("Watcher failed", e);
        }
    }

    private void markRulesDirty()  { scheduleBatch(true,  false); }
    private void markTogglesDirty(){ scheduleBatch(false, true ); }

    private void scheduleBatch(boolean rules, boolean togglesChanged) {
        synchronized (batchLock) {
            rulesDirty   |= rules;
            togglesDirty |= togglesChanged;

            // throttle: if we just ran, delay a bit more
            long since = System.currentTimeMillis() - lastRun.get();
            long delay = Math.max(QUIET_MS, MIN_INTERVAL_MS - Math.max(0, since));

            if (batchFuture != null) batchFuture.cancel(false);
            batchFuture = scheduler.schedule(this::runBatch, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void runBatch() {
        if (!running.compareAndSet(false, true)) return; // skip if a batch is already running
        boolean doRules, doToggles;
        synchronized (batchLock) {
            doRules = rulesDirty;
            doToggles = togglesDirty;
            rulesDirty = false;
            togglesDirty = false;
        }
        try {
            System.out.println("🔁 [" + Instant.now() + "] Batch start: rules=" + doRules + ", toggles=" + doToggles);
            if (doRules) {
                drools.rebuildFromFilesystem(rulesDir);
            }
            if (doToggles) {
                try {
                    if (Files.exists(togglesFile)) toggles.loadFromJson(togglesFile);
                    else toggles.putAll(java.util.Map.of());
                } catch (IOException e) {
                    System.err.println("❌ Toggle reload failed: " + e.getMessage());
                }
            }
            // single refire for the whole batch
            Object[] facts = factsSupplier != null ? factsSupplier.get() : new Object[0];
            if (facts == null) facts = new Object[0];
            int fired = drools.fireOnce(facts);
            System.out.println("✅ Batch done. Re-fired; count = " + fired);
            lastRun.set(System.currentTimeMillis());
        } catch (Throwable t) {
            System.err.println("❌ Batch failed: " + t.getMessage());
            t.printStackTrace(System.err);
        } finally {
            running.set(false);
        }
    }

    private static boolean isRuleFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return RULE_EXTS.stream().anyMatch(name::endsWith);
    }

    private boolean isToggleFile(Path p) {
        return p.getFileName().equals(togglesFile.getFileName());
    }

    private static boolean isIgnored(Path p) {
        String n = p.getFileName().toString();
        if (n.startsWith(".")) return true; // hidden
        String lower = n.toLowerCase();
        for (String suf : IGNORE_SUFFIX) {
            if (lower.endsWith(suf)) return true;
        }
        return false;
    }
}
