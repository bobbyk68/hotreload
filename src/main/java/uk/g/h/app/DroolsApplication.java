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

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.kie.api.runtime.KieContainer;
import uk.g.h.rules.runtime.DroolsService;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;
import uk.g.h.rules.watch.UnifiedWatcherService;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.function.Supplier;

@SpringBootApplication
public class DroolsApplication implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        Path rulesDir = Path.of("src/main/resources/rules");
        Path toggles  = Path.of("config/toggles.json");

        if (!Files.exists(rulesDir)) {
            throw new IllegalStateException("Rules dir not found: " + rulesDir.toAbsolutePath());
        }

        // Build initial container PROGRAMMATICALLY (we removed kmodule.xml)
        KieContainer initial = uk.g.h.rules.runtime.DroolsService.buildFromRulesDir(rulesDir);

        // Load toggles
        var toggleService = new JsonBackedRuleToggleService(true);
        if (Files.exists(toggles)) {
            toggleService.loadFromJson(toggles);
        }

        // Construct service manually (non-annotated)
        var drools = new DroolsService(initial, toggleService);

        // Supply the facts to (re)fire with after ANY change.
        // Replace this supplier with how you actually gather current facts.
        Supplier<Object[]> factsSupplier = () -> new Object[] {
                // e.g., fetch from DB/cache, or hold an AtomicReference<Object[]> updated elsewhere
                // new Order("A123", 180.0), new Customer("C42", "Bronze")
        };

        // Start watcher: rebuild on DRL/DSL/DSLR change, reload toggles on JSON change, and AUTO RE-FIRE
        var watcher = new UnifiedWatcherService(
                rulesDir,
                toggles,
                drools,
                toggleService,
                factsSupplier
        );
        Thread t = new Thread(watcher, "rules+toggle-watcher");
        t.setDaemon(true);
        t.start();

        // Initial fire
        int fired = drools.fireOnce(factsSupplier.get());
        System.out.println("Initial fireAllRules() fired = " + fired);
    }

    public static void main(String[] args) {
        SpringApplication.run(DroolsApplication.class, args);
    }
}
