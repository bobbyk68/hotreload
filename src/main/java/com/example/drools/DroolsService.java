// DroolsService.java
package com.example.drools;

import com.example.drools.model.Customer;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DroolsService {
    private KieContainer kieContainer;

    public DroolsService() throws Exception {
        compileAllDslRules();
        watchForChanges(); // Watcher thread
    }

    public void reload() {
        try {
            System.out.println("Reloading rules...");
            compileAllDslRules();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void compileAllDslRules() throws Exception {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();

        // Load all .dsl and .dslr files
        List<Path> ruleFiles = Files.walk(Paths.get("rules"))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".dslr") || p.toString().endsWith(".dsl"))
                .collect(Collectors.toList());

        for (Path path : ruleFiles) {
            String content = Files.readString(path);
            String kfsPath = "src/main/resources/" + path.getFileName();
            kfs.write(kieServices.getResources()
                    .newByteArrayResource(content.getBytes())
                    .setTargetPath(kfsPath));
        }

        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();
        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Build Errors:\n" + kieBuilder.getResults());
        }

        this.kieContainer = kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
    }

    public void fireAllRules() {
        KieSession kieSession = kieContainer.newKieSession();
        kieSession.insert(new Customer(42)); // Optional test insert
        kieSession.fireAllRules();
        kieSession.dispose();
    }

    public void execute() {
        KieSession kieSession = kieContainer.newKieSession();
        Customer customer = new Customer(42);
        kieSession.insert(customer);
        kieSession.fireAllRules();
        kieSession.dispose();
    }

    public void watchForChanges() {
        Thread watcher = new Thread(new WatcherService(this));
        watcher.setDaemon(true);
        watcher.start();
    }
}