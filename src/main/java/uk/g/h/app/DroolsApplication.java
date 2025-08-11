package uk.g.h.app;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.kie.api.runtime.KieContainer;
import uk.g.h.rules.runtime.DroolsService;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;
import uk.g.h.rules.watch.UnifiedWatcherService;

import java.nio.file.Path;
import java.nio.file.Files;

@SpringBootApplication
public class DroolsApplication implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        Path rulesDir = Path.of("src/main/resources/rules");
        Path toggles  = Path.of("config/toggles.json");

        if (!Files.exists(rulesDir)) {
            throw new IllegalStateException("Rules dir not found: " + rulesDir.toAbsolutePath());
        }

        // Build initial container PROGRAMMATICALLY (no kmodule.xml)
        KieContainer initial = DroolsService.buildFromRulesDir(rulesDir);

        // Load toggles
        var toggleService = new JsonBackedRuleToggleService(true);
        if (Files.exists(toggles)) {
            toggleService.loadFromJson(toggles);
        }

        // Non-annotated service, constructed manually
        var drools = new DroolsService(initial, toggleService);

        // Start unified watcher (rebuilds on DRL change, reloads toggles on JSON change)
        var watcher = new UnifiedWatcherService(
                rulesDir,
                toggles,
                () -> drools.rebuildFromFilesystem(rulesDir),
                toggleService
        );
        Thread t = new Thread(watcher, "rules+toggle-watcher");
        t.setDaemon(true);
        t.start();

        // Demo fire
        int fired = drools.fireOnce();
        System.out.println("Initial fireAllRules() fired = " + fired);
    }

    public static void main(String[] args) {
        SpringApplication.run(DroolsApplication.class, args);
    }
}
