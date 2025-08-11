package uk.g.h.rules.watch;

import uk.g.h.rules.toggle.JsonBackedRuleToggleService;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.*;

public final class UnifiedWatcherService implements Runnable {
    private final Path rulesDir;
    private final Path togglesFile;
    private final Runnable onRulesChanged;
    private final JsonBackedRuleToggleService toggleService;

    private final ConcurrentHashMap<Path, Long> lastEvent = new ConcurrentHashMap<>();
    private static final Set<String> RULE_EXTS = Set.of(".drl", ".dsl", ".dslr");

    public UnifiedWatcherService(Path rulesDir,
                                 Path togglesFile,
                                 Runnable onRulesChanged,
                                 JsonBackedRuleToggleService toggleService) {
        this.rulesDir = rulesDir;
               this.togglesFile = togglesFile;
        this.onRulesChanged = onRulesChanged;
        this.toggleService = toggleService;
    }

    @Override
    public void run() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            rulesDir.register(ws, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            Path togglesDir = togglesFile.getParent();
            togglesDir.register(ws, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

            System.out.println("Watching rules in: " + rulesDir);
            System.out.println("Watching toggles: " + togglesFile);

            for (;;) {
                WatchKey key = ws.take();
                boolean rulesChanged = false;
                boolean togglesChanged = false;

                for (WatchEvent<?> evt : key.pollEvents()) {
                    if (evt.kind() == OVERFLOW) continue;
                    Path ctx = (Path) evt.context();
                    Path dir = (Path) key.watchable();
                    Path changed = dir.resolve(ctx);

                    if (!debounced(changed)) continue;

                    if (isRuleFile(changed)) {
                        rulesChanged = true;
                    } else if (isToggleFile(changed)) {
                        togglesChanged = true;
                    }
                }

                if (rulesChanged) {
                    sleep(150);
                    try {
                        System.out.println("Rules changed -> rebuilding KieContainer...");
                        onRulesChanged.run();
                        System.out.println("Rules reloaded successfully.");
                    } catch (Throwable t) {
                        System.err.println("Rule reload failed: " + t.getMessage());
                        t.printStackTrace(System.err);
                    }
                }

                if (togglesChanged) {
                    sleep(150);
                    try {
                        if (Files.exists(togglesFile)) {
                            toggleService.loadFromJson(togglesFile);
                            System.out.println("Reloaded rule toggles from " + togglesFile);
                        } else {
                            toggleService.putAll(java.util.Map.of());
                            System.out.println("Toggle file missing; default enable policy in effect.");
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to reload toggles: " + e.getMessage());
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

    private static boolean isRuleFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return RULE_EXTS.stream().anyMatch(name::endsWith);
    }

    private boolean isToggleFile(Path p) {
        return p.getFileName().equals(togglesFile.getFileName());
    }

    private boolean debounced(Path p) {
        long now = Instant.now().toEpochMilli();
        Long prev = lastEvent.put(p, now);
        return prev == null || (now - prev) > 120;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}