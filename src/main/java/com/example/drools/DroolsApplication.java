package com.example.drools;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DroolsApplication implements CommandLineRunner {

    private final DroolsService droolsService;

    public DroolsApplication(DroolsService droolsService) {
        this.droolsService = droolsService;
    }

    @Override
    public void run(String... args) {
        // Start the file watcher
        droolsService.watchForChanges();

        // Optional: Fire rules once on startup
        droolsService.fireAllRules();

        // Block main thread to keep app running
        try {
            Thread.currentThread().join(); // <--- This prevents the JVM from exiting
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(DroolsApplication.class, args);
    }
}