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

package uk.g.h.app;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uk.g.h.rules.runtime.DroolsService;
import uk.g.h.rules.runtime.CurrentFacts;
import uk.g.h.rules.watch.UnifiedWatcherService;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;

import java.nio.file.Path;

@SpringBootApplication
public class DroolsApplication implements CommandLineRunner {

    private final DroolsService drools;                 // injected
    private final JsonBackedRuleToggleService toggles;  // injected
    private final CurrentFacts facts;                   // injected
    private final String rulesDir;                      // injected
    private final String togglesFile;                   // injected

    public DroolsApplication(
            DroolsService drools,
            JsonBackedRuleToggleService toggles,
            CurrentFacts facts,
            @org.springframework.beans.factory.annotation.Value("${rules.dir:src/main/resources/rules}") String rulesDir,
            @org.springframework.beans.factory.annotation.Value("${toggles.file:config/toggles.json}") String togglesFile
    ) {
        this.drools = drools;
        this.toggles = toggles;
        this.facts = facts;
        this.rulesDir = rulesDir;
        this.togglesFile = togglesFile;
    }

    @Override
    public void run(String... args) throws Exception {
        // start watcher (auto rebuild + refire with latest facts)
        var watcher = new UnifiedWatcherService(
                Path.of(rulesDir),
                Path.of(togglesFile),
                drools,
                toggles,
                facts::get
        );
        Thread t = new Thread(watcher, "rules+toggle-watcher");
        t.setDaemon(true);
        t.start();

        // initial fire if you want
        int fired = drools.fireOnce(facts.get());
        System.out.println("Initial fired = " + fired);
    }

    public static void main(String[] args) {
        SpringApplication.run(DroolsApplication.class, args);
    }
}
