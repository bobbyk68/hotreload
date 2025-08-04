package com.example.drools.config;

import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.builder.KieScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DroolsConfig {

    @Bean
    public KieContainer kieContainer() {
        KieServices ks = KieServices.Factory.get();

        // Use the KJAR coordinates
        ReleaseId releaseId = ks.newReleaseId("com.example", "drools10-kjar", "1.0-SNAPSHOT");
        KieContainer kieContainer = ks.newKieContainer(releaseId);

        KieScanner scanner = ks.newKieScanner(kieContainer);
        scanner.start(5000L); // poll every 5 seconds for updates

        return kieContainer;
    }
}