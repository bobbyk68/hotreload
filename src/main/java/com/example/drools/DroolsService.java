package com.example.drools;

import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import org.springframework.stereotype.Service;

@Service
public class DroolsService {

    private final KieContainer kieContainer;
    private final KieScanner kieScanner;

    public DroolsService() {
        KieServices kieServices = KieServices.Factory.get();

        // Assume your rules are under default group/artifact/version
        ReleaseId releaseId = kieServices.newReleaseId("com.example", "drools-rules", "1.0.0");

        // Create KieContainer from classpath and start the scanner
        kieContainer = kieServices.newKieContainer(releaseId);
        kieScanner = kieServices.newKieScanner(kieContainer);

        // Scan every 5 seconds for updated rules (you can change this)
        kieScanner.start(5000L);

        System.out.println("KieScanner started for release: " + releaseId);
    }

    public void fireAllRules() {
        KieSession kieSession = kieContainer.newKieSession();

        // Sample fact (adjust for your model)
        com.example.drools.model.Customer customer = new com.example.drools.model.Customer();
        customer.setAge(42);
        kieSession.insert(customer);

        kieSession.fireAllRules();
        kieSession.dispose();
    }
}