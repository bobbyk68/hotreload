package uk.g.h.rules.runtime;

import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.io.Resource;
import org.kie.api.runtime.*;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;
import uk.g.h.rules.toggle.ToggleAgendaFilter;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class DroolsService {
    private static final String KBASE_NAME    = "rules-base";
    private static final String KSESSION_NAME = "ksession-rules";
    // Use "*" to avoid package mismatches across DRLs; set to "rules" if you prefer explicit.
    private static final String RULES_PACKAGE = "*";

    private final AtomicReference<KieContainer> kcRef;
    private final JsonBackedRuleToggleService toggles;

    public DroolsService(KieContainer initial, JsonBackedRuleToggleService toggles) {
        this.kcRef  = new AtomicReference<>(Objects.requireNonNull(initial, "initial KieContainer must not be null"));
        this.toggles = Objects.requireNonNull(toggles, "toggles must not be null");
    }

    /** Build a KieContainer from a filesystem directory (no kmodule.xml). */
    public static KieContainer buildFromRulesDir(Path rulesDir) {
        try {
            KieServices ks = KieServices.Factory.get();

            // Programmatic kmodule (replaces kmodule.xml)
            KieModuleModel kmodule = ks.newKieModuleModel();
            KieBaseModel kbase = kmodule.newKieBaseModel(KBASE_NAME)
                    .setDefault(true)
                    .addPackage(RULES_PACKAGE);
            kbase.newKieSessionModel(KSESSION_NAME)
                    .setType(KieSessionModel.KieSessionType.STATEFUL)
                    .setDefault(true);

            KieFileSystem kfs = ks.newKieFileSystem();
            kfs.writeKModuleXML(kmodule.toXML());

            // Add rule resources
            try (Stream<Path> files = Files.walk(rulesDir)) {
                files.filter(Files::isRegularFile)
                        .filter(DroolsService::isRuleResource)
                        .forEach(p -> {
                            String rel = rulesDir.relativize(p).toString().replace('\\', '/');
                            Resource res = ks.getResources().newFileSystemResource(p.toFile());
                            kfs.write("src/main/resources/" + rel, res);
                            System.out.println("Adding rule file: " + rel);
                        });
            }

            KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
            Results results = kb.getResults();
            if (results.hasMessages(Message.Level.ERROR)) {
                results.getMessages().forEach(m -> System.err.println("BUILD " + m.getLevel() + ": " + m));
                throw new IllegalStateException("Drools build errors; see log above.");
            }

            KieContainer container = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
            dumpContainer(container); // optional visibility
            return container;
        } catch (IOException e) {
            throw new RuntimeException("Failed reading rules from " + rulesDir, e);
        }
    }

    /** Hot rebuild and atomic swap (invoke from your watcher). */
    public void rebuildFromFilesystem(Path rulesDir) {
        kcRef.set(buildFromRulesDir(rulesDir));
    }

    public int fireOnce(Object... facts) {
        KieSession ks = createSession(kcRef.get());
        try {
            for (Object f : facts) ks.insert(f);
            return ks.fireAllRules(new ToggleAgendaFilter(toggles));
        } finally {
            ks.dispose();
        }
    }

    public KieContainer current() { return kcRef.get(); }

    // ---- helpers ----

    private static boolean isRuleResource(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".drl") || n.endsWith(".dsl") || n.endsWith(".dslr"); // add ".xlsx" if needed
    }

    private static void dumpContainer(KieContainer c) {
        System.out.println("KieBases: " + c.getKieBaseNames());
        for (String kb : c.getKieBaseNames()) {
            System.out.println("  " + kb + " sessions=" + c.getKieSessionNamesInKieBase(kb));
        }
    }

    /** Triple fallback so a session is always created. */
    private static KieSession createSession(KieContainer c) {
        KieSession ks = null;
        try { ks = c.newKieSession(KSESSION_NAME); } catch (Throwable ignored) {}
        if (ks == null) { try { ks = c.newKieSession(); } catch (Throwable ignored) {} }
        if (ks == null) {
            KieServices srv = KieServices.Factory.get();
            KieBase base = c.getKieBase();
            ks = base.newKieSession(srv.newKieSessionConfiguration(), srv.newEnvironment());
        }
        if (ks == null) throw new IllegalStateException("No KieSession available; check rules added & package.");
        return ks;
    }
}
