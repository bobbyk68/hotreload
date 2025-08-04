package com.example.drools;

import com.example.drools.model.Customer;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class DroolsApplication implements CommandLineRunner {
    private final KieContainer kieContainer;

    public DroolsApplication(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public static void main(String[] args) {
        SpringApplication.run(DroolsApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        KieSession session = kieContainer.newKieSession("ksession-rules");
        Customer c = new Customer();
        c.setAge(42);
        session.insert(c);
        session.fireAllRules();
        session.dispose();

        // keep alive if needed
        Thread.currentThread().join();
    }
}