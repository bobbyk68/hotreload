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


import org.drools.compiler.compiler.DSLTokenizedMappingFile;
import org.drools.compiler.lang.dsl.DefaultExpander;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DroolsService {

    // Use AtomicReference so swaps are atomic/safe during reload
    private final AtomicReference<KieContainer> containerRef = new AtomicReference<>();

    // Adjust if your rules dir differs
    private final Path rulesDir = Paths.get("rules");

    public DroolsService() throws Exception {
        compileAllDslRules();  // cold start = same pipeline as reload
        watchForChanges();     // Watcher thread (unchanged if you already have one)
    }

    /** External hook to trigger reloads manually. */
    public void reload() {
        try {
            System.out.println("Reloading rules...");
            compileAllDslRules();
            System.out.println("✅ Reload complete");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void fireAllRules() {
        KieSession ks = kieContainer.newKieSession();
        try {
            System.out.println("fireAllRules() session=" + System.identityHashCode(ks));

            // Log activations and matches
            ks.addEventListener(new org.kie.api.event.rule.DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(org.kie.api.event.rule.AfterMatchFiredEvent e) {
                    System.out.println("FIRED: " + e.getMatch().getRule().getName());
                }
                @Override
                public void matchCreated(org.kie.api.event.rule.MatchCreatedEvent e) {
                    System.out.println("MATCH: " + e.getMatch().getRule().getName());
                }
            });

            // Insert exactly one fact
            Customer c = new Customer(42);
            ks.insert(c);
            System.out.println("Inserted facts: " + ks.getObjects().size());

            // Fire rules
            ks.fireAllRules();
        } finally {
            ks.dispose(); // clean up session
        }
    }



    /** === The important bit: compile ALL rules from DSL/DSLR → DRL, then build === */
    private void compileAllDslRules() throws Exception {
        // 1) Read header (shared package/imports/globals)
        String header = readIfExists(rulesDir.resolve("_header.drl"));
        String pkg = (header != null) ? extractPackage(header) : "rules";

        // 2) Load ALL .dsl mappings
        List<DSLTokenizedMappingFile> mappings = Files.walk(rulesDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".dsl"))
                .map(this::loadDslMapping)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 3) Expand EVERY .dslr to DRL (prepend header so each DRL is self-contained)
        Map<String, String> expandedDrls = new LinkedHashMap<>();
        List<Path> dslrFiles = Files.walk(rulesDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".dslr"))
                .sorted()
                .collect(Collectors.toList());

        DefaultExpander expander = new DefaultExpander();
        for (var m : mappings) expander.addDSLMapping(m.getMapping());

        for (Path dslr : dslrFiles) {
            String dslrText = Files.readString(dslr, StandardCharsets.UTF_8);
            String drlBody = expander.expand(dslrText);
            String drl = mergeHeader(ensureSingleTopPackage(drlBody, pkg),
                    header != null ? ensureSingleTopPackage(header, pkg) : null);
            // give a deterministic in-memory path
            String kfsPath = "src/main/resources/rules/_expanded_" + dslr.getFileName().toString().replace(".dslr", ".drl");
            expandedDrls.put(kfsPath, drl);
        }

        // 4) Include any static .drl files you keep in the folder (besides _header.drl)
        List<Path> staticDrls = Files.walk(rulesDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".drl"))
                .filter(p -> !p.getFileName().toString().equals("_header.drl"))
                .sorted()
                .collect(Collectors.toList());
        for (Path drl : staticDrls) {
            String text = Files.readString(drl, StandardCharsets.UTF_8);
            String normalized = ensureSingleTopPackage(text, pkg);
            String kfsPath = "src/main/resources/rules/" + drl.getFileName();
            expandedDrls.put(kfsPath, normalized);
        }

        // 5) Build with a fresh ReleaseId (prevents stale cache)
        KieServices ks = KieServices.Factory.get();
        String version = "1.0." + System.currentTimeMillis();
        ReleaseId rid = ks.newReleaseId("com.example", "hotrules", version);
        KieFileSystem kfs = ks.newKieFileSystem().generateAndWritePomXML(rid);

        // write all DRLs
        for (var e : expandedDrls.entrySet()) {
            kfs.write(ks.getResources()
                    .newByteArrayResource(e.getValue().getBytes(StandardCharsets.UTF_8))
                    .setTargetPath(e.getKey()));
        }

        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
        Results results = kb.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            System.err.println("❌ Build errors:");
            results.getMessages(Message.Level.ERROR).forEach(msg -> System.err.println(" - " + msg));
            throw new RuntimeException("Rule build failed");
        }

        // 6) Swap container atomically
        KieContainer newContainer = ks.newKieContainer(rid);
        KieContainer old = containerRef.getAndSet(newContainer);
        if (old != null) old.dispose();
        System.out.println("✅ Rules compiled: " + rid);
    }

    // ---------- Watcher (reuse your existing WatcherService if you prefer) ----------
    public void watchForChanges() {
        Thread watcher = new Thread(new WatcherService(this));
        watcher.setDaemon(true);
        watcher.start();
    }

    // ---------- Helpers ----------
    private DSLTokenizedMappingFile loadDslMapping(Path dslPath) {
        try (var reader = Files.newBufferedReader(dslPath, StandardCharsets.UTF_8)) {
            DSLTokenizedMappingFile f = new DSLTokenizedMappingFile();
            f.parseAndLoad(reader);
            return f;
        } catch (Exception e) {
            System.err.println("Failed to load DSL: " + dslPath + " -> " + e.getMessage());
            return null;
        }
    }

    private static String readIfExists(Path p) {
        try { return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : null; }
        catch (Exception e) { return null; }
    }

    private static final Pattern PKG = Pattern.compile("(?m)^\\s*package\\s+([\\w\\.]+)\\s*$");

    private static String extractPackage(String drl) {
        var m = PKG.matcher(drl);
        return m.find() ? m.group(1) : "rules";
    }

    /** Ensure exactly one `package X` at the very top, no semicolon, blank line after. */
    private static String ensureSingleTopPackage(String drl, String pkg) {
        String s = stripBom(drl).stripLeading();
        // remove all package lines
        s = s.replaceAll("(?m)^\\s*package\\s+[^\\r\\n]+\\s*$", "").stripLeading();
        return "package " + pkg + "\n\n" + s;
    }

    /** If header provided, put header first; otherwise return body as-is. */
    private static String mergeHeader(String bodyWithPkg, String headerWithPkgOrNull) {
        if (headerWithPkgOrNull == null) return bodyWithPkg;
        // remove the package line from body so header's package stays first
        String body = bodyWithPkg.replaceFirst("(?s)^\\s*package\\s+[^\\r\\n]+\\s*", "").stripLeading();
        return headerWithPkgOrNull.stripTrailing() + "\n\n" + body;
    }

    private static String stripBom(String s) {
        return (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') ? s.substring(1) : s;
    }
}
