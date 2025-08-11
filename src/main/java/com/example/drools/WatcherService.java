// WatcherService.java
package com.example.drools;

import java.io.IOException;
import java.nio.file.*;


import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardWatchEventKinds.*;

public class WatcherService implements Runnable {
    private final DroolsService droolsService;
    private final Path rulesPath = Paths.get("rules");

    // debounce state
    private final ScheduledExecutorService debounceExec = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();
    // optional: ignore identical saves
    private final Set<String> lastHashes = ConcurrentHashMap.newKeySet();

    public WatcherService(DroolsService droolsService) {
        this.droolsService = droolsService;
    }

    @Override
    public void run() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            rulesPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                boolean relevant = false;

                for (WatchEvent<?> event : key.pollEvents()) {
                    Path rel = (Path) event.context();
                    String name = rel.getFileName().toString().toLowerCase();

                    // watch dsl, dslr, and header drl (but NOT any generated/expanded drl)
                    if (name.endsWith(".dsl") || name.endsWith(".dslr") || name.equals("_header.drl")) {
                        relevant = true;
                    }
                }

                key.reset();

                if (!relevant) continue;

                // debounce: coalesce a burst of events (IDE safe-write etc)
                ScheduledFuture<?> prev = pending.getAndSet(
                        debounceExec.schedule(this::rebuildAndFire, 600, TimeUnit.MILLISECONDS)
                );
                if (prev != null && !prev.isDone()) prev.cancel(false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            debounceExec.shutdownNow();
        }
    }

    private void rebuildAndFire() {
        try {
            // Optional: content-hash guard to skip no-op saves
            // (cheap: rely on versioned ReleaseId to force full rebuild anyway)
            droolsService.reload();
            droolsService.fireAllRules();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
