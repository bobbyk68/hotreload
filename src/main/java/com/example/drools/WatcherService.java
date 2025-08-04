// WatcherService.java
package com.example.drools;

import java.io.IOException;
import java.nio.file.*;

public class WatcherService implements Runnable {
    private final DroolsService droolsService;

    public WatcherService(DroolsService droolsService) {
        this.droolsService = droolsService;
    }

    @Override
    public void run() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path rulesPath = Paths.get("rules");
            rulesPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = (Path) event.context();
                    String filename = changed.toString();
                    if (filename.endsWith(".dsl") || filename.endsWith(".dslr") || filename.endsWith(".drl")) {
                        System.out.println("Detected change in: " + filename);
                        droolsService.reload();
                        droolsService.fireAllRules();
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
