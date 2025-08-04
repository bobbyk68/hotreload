
package com.example.drools;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.example.drools.model.Customer;
import com.example.drools.DroolsService;

@SpringBootApplication
public class DroolsApplication implements CommandLineRunner {

    private final DroolsService droolsService;

    public DroolsApplication(DroolsService droolsService) {
        this.droolsService = droolsService;
    }

    public static void main(String[] args) {
        SpringApplication.run(DroolsApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        droolsService.executeRules(new Customer("Alice", "VIP", 30));
    }
}
