
package uk.g.h.rules.reload;

import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSessionPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
@EnableScheduling
public class DroolsReloadConfig {

    @Value("${rules.gav}")
    private String gav;

    @Value("${rules.remote.metadata-uri}")
    private String metadataUri;

    @Value("${rules.session.pool-size:16}")
    private int poolSize;

    @Value("${rules.repo.user:}")
    private String repoUser;

    @Value("${rules.repo.pass:}")
    private String repoPass;

    @Bean
    public KieServices kieServices() {
        return KieServices.Factory.get();
    }

    @Bean
    public ReleaseId initialRid(KieServices ks) {
        String[] p = gav.split(":");
        return ks.newReleaseId(p[0], p[1], p[2]);
    }

    @Bean
    public KieContainer initialContainer(KieServices ks, ReleaseId rid) {
        return ks.newKieContainer(rid);
    }

    @Bean
    public AtomicReference<KieContainer> containerRef(KieContainer c) {
        return new AtomicReference<>(c);
    }

    @Bean
    public AtomicReference<KieSessionPool> sessionPoolRef(KieContainer c) {
        return new AtomicReference<>(c.newKieSessionPool(poolSize));
    }

    @Bean
    public RulesRoller rulesRoller(KieServices ks,
                                   AtomicReference<KieContainer> cRef,
                                   AtomicReference<KieSessionPool> poolRef) {
        return new RulesRoller(ks, cRef, poolRef, poolSize);
    }

    @Bean
    public RulesScanner remoteScanner(KieServices ks) throws Exception {
        String[] p = gav.split(":");
        return new RemoteMavenScanner(
                ks, p[0], p[1], p[2],
                new URI(metadataUri),
                repoUser, repoPass
        );
    }

    @Bean
    public RepoScanScheduler repoScanScheduler(RulesScanner scanner,
                                               RulesRoller roller,
                                               KieServices ks) {
        return new RepoScanScheduler(scanner, roller, ks);
    }
}

// imports
import java.net.URI;
import java.nio.file.Paths;
import org.kie.api.KieServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@Value("${rules.scanner.mode:local-m2}")
private String scannerMode;

@Value("${rules.local.m2:}")
private String localM2;

@Value("${rules.remote.metadata-uri:}")
private String metadataUri;   // keep optional for local mode

@Bean
public RulesScanner scanner(KieServices ks) throws Exception {
    String[] p = gav.split(":"); // g:a:v
    if ("local-m2".equalsIgnoreCase(scannerMode)) {
        var m2 = (localM2 == null || localM2.isBlank()) ? null : Paths.get(localM2);
        return new LocalMavenScanner(ks, p[0], p[1], p[2], m2);
    } else {
        return new RemoteMavenScanner(ks, p[0], p[1], p[2], new URI(metadataUri), repoUser, repoPass);
    }
}
