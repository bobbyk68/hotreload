package com.example.drools.config;

import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DroolsConfig {

    @Bean
    public KieContainer kieContainer() {
        KieServices kieServices = KieServices.Factory.get();

        ReleaseId releaseId = kieServices.getRepository().getDefaultReleaseId();
        KieContainer kieContainer = kieServices.newKieContainer(releaseId);

        KieScanner kieScanner = kieServices.newKieScanner(kieContainer);
        kieScanner.start(10_000L); // check every 10s

        return kieContainer;
    }
}