package uk.g.h.app;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import uk.g.h.rules.runtime.DroolsService;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;
import uk.g.h.rules.watch.UnifiedWatcherService;

import java.nio.file.Path;

@SpringBootApplication
public class DroolsApplication implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        Path rulesDir = Path.of("src/main/resources/rules");
        Path toggles = Path.of("config/toggles.json");

        KieServices ks = KieServices.Factory.get();
        KieContainer initial = ks.getKieClasspathContainer();

        var toggleService = new JsonBackedRuleToggleService(true);
        toggleService.loadFromJson(toggles);

        var drools = new uk.g.h.rules.runtime.DroolsService(initial, toggleService);

        var watcher = new UnifiedWatcherService(
                rulesDir,
                toggles,
                () -> drools.rebuildFromFilesystem(rulesDir),
                toggleService
        );
        new Thread(watcher, "rules+toggle-watcher").start();

        int fired = drools.fireOnce();
        System.out.println("Initial fireAllRules() fired = " + fired);
    }

    public static void main(String[] args) {
        SpringApplication.run(DroolsApplication.class, args);
    }
}