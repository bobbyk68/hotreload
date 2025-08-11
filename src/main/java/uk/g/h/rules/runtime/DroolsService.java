package uk.g.h.rules.runtime;

import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;
import uk.g.h.rules.toggle.ToggleAgendaFilter;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class DroolsService {
    private final AtomicReference<KieContainer> kcRef = new AtomicReference<>();
    private final JsonBackedRuleToggleService toggles;

    public DroolsService(KieContainer initial, JsonBackedRuleToggleService toggles) {
        this.kcRef.set(initial);
        this.toggles = toggles;
    }

    public void rebuildFromFilesystem(Path rulesDir) {
        try {
            KieServices ks = KieServices.Factory.get();
            KieFileSystem kfs = ks.newKieFileSystem();

            try (Stream<Path> files = Files.walk(rulesDir)) {
                files.filter(Files::isRegularFile).forEach(p -> {
                    String rel = rulesDir.relativize(p).toString().replace('\\', '/');
                    Resource res = ks.getResources().newFileSystemResource(p.toFile());
                    kfs.write("src/main/resources/" + rel, res);
                });
            }

            KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
            Results results = kb.getResults();
            if (results.hasMessages(Message.Level.ERROR)) {
                throw new IllegalStateException("Build errors: " + results.getMessages());
            }

            KieContainer fresh = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
            kcRef.set(fresh);
        } catch (IOException e) {
            throw new RuntimeException("Failed reading rules from " + rulesDir, e);
        }
    }

    public int fireOnce(Object... facts) {
        KieSession ksession = kcRef.get().newKieSession();
        try {
            for (Object f : facts) ksession.insert(f);
            return ksession.fireAllRules(new ToggleAgendaFilter(toggles));
        } finally {
            ksession.dispose();
        }
    }

    public KieContainer current() {
        return kcRef.get();
    }
}