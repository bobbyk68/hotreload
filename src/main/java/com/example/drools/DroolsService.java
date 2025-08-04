// DroolsService.java
package com.example.drools;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import com.example.drools.dsl.DslExpander;
import com.example.drools.model.Customer;
import com.example.drools.dsl.DslRule;
import com.example.drools.WatcherService;
import java.nio.charset.StandardCharsets;
@Service
public class DroolsService {
    private KieContainer kieContainer;

    @PostConstruct
    public void init() throws IOException {
        compileAllDslRules();
        watchForChanges();
    }


    public void executeRules(Customer fact) {
        KieSession kieSession = kieContainer.newKieSession();
        kieSession.insert(fact);
        kieSession.fireAllRules();
        kieSession.dispose();
    }

    private void compileAllDslRules() throws IOException {
    DslExpander expander = new DslExpander();

    // 🔍 Print all loaded DSL lines from every .dsl file
    Files.walk(Paths.get("rules"))
            .filter(f -> f.toString().endsWith(".dsl"))
            .forEach(path -> {
                try {
                    System.out.println("\nLoaded DSL lines from: " + path.getFileName());
                    Files.readAllLines(path).forEach(System.out::println);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

    List<Path> dslrs = Files.walk(Paths.get("rules"))
            .filter(f -> f.toString().endsWith(".dslr"))
            .toList();

    KieServices ks = KieServices.Factory.get();
    KieFileSystem kfs = ks.newKieFileSystem();

    for (Path dslr : dslrs) {
        Path dslFile = Paths.get("rules/sample.dsl");
    
        String dslrContent = Files.readString(dslr, StandardCharsets.UTF_8);
        List<DslRule> dslRules = DslExpander.parseDslRules(dslFile); // assuming you already have dslFile
        String drl = expander.expand(dslrContent, dslRules);

        //String drl = expander.expand(dslr);
        // 🔍 Print the expanded DRL for debugging
        System.out.println("\nExpanded DRL for file: " + dslr.getFileName());
        System.out.println("-----------------------------------");
        System.out.println(drl);
        System.out.println("-----------------------------------");

        String rulePath = "src/main/resources/rules/" + dslr.getFileName().toString().replace(".dslr", ".drl");
        kfs.write(rulePath, drl);
    }

    KieBuilder kieBuilder = ks.newKieBuilder(kfs).buildAll();
    Results results = kieBuilder.getResults();
    if (results.hasMessages(Message.Level.ERROR)) {
        throw new RuntimeException("Build Errors:\n" + results.getMessages());
    }

    kieContainer = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
}


    private void watchForChanges() {
        Thread watcher = new Thread(new WatcherService(this));
        watcher.setDaemon(true);
        watcher.start();
    }

    public void reload() {
        try {
            System.out.println("\n====== File Change Detected at " + java.time.LocalTime.now() + " ======");
            System.out.println("Reloading DSL and DSLR files...");

            compileAllDslRules(); // this already prints DRL if your expander logs are active

            System.out.println("Reload complete.");
            System.out.println("============================================\n");
        } catch (Exception e) {
            System.err.println("Error during reload:");
            e.printStackTrace();
        }
    }
}

