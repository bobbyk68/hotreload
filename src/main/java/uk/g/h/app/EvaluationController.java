package uk.g.h.app;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.g.h.rules.runtime.CurrentFacts;
import uk.g.h.rules.runtime.DroolsService;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EvaluationController {

    private final DroolsService drools;
    private final JsonBackedRuleToggleService toggles;
    private final CurrentFacts currentFacts;
    private final Path rulesDir;
    private final Path togglesPath;

    public EvaluationController(
            DroolsService drools,
            JsonBackedRuleToggleService toggles,
            CurrentFacts currentFacts,
            @Value("${rules.dir:src/main/resources/rules}") String rulesDir,
            @Value("${toggles.file:config/toggles.json}") String togglesFile
    ) {
        this.drools = drools;
        this.toggles = toggles;
        this.currentFacts = currentFacts;
        this.rulesDir = Path.of(rulesDir);
        this.togglesPath = Path.of(togglesFile);
    }

    // ---------- DTOs that bind from XML or JSON ----------

    @JacksonXmlRootElement(localName = "EvaluationRequest")
    public static final class EvaluationRequest {
        /** Optional flags; can come from XML or query string */
        @JsonProperty("reloadRules")   public boolean reloadRules;
        @JsonProperty("reloadToggles") public boolean reloadToggles;

        /** Accept both <declaration> or <Declaration> etc. */
        @JsonAlias({"declaration","Declaration"})
        public Declaration declaration;

        @JsonAlias({"submission","Submission","submit"})
        public Submission submission;

        @JsonCreator public EvaluationRequest(
                @JsonProperty("reloadRules")   Boolean reloadRules,
                @JsonProperty("reloadToggles") Boolean reloadToggles,
                @JsonProperty("declaration")   Declaration declaration,
                @JsonProperty("submission")    Submission submission) {
            this.reloadRules   = reloadRules   != null && reloadRules;
            this.reloadToggles = reloadToggles != null && reloadToggles;
            this.declaration = declaration;
            this.submission  = submission;
        }
        public EvaluationRequest() {}
    }

    /** Map-backed fact for Declaration XML. */
    @JacksonXmlRootElement(localName = "Declaration")
    public static final class Declaration {
        public final Map<String, Object> fields = new HashMap<>();
        @JsonAnySetter public void put(String k, Object v) { fields.put(k, v); }
        public Object get(String k) { return fields.get(k); }
        @Override public String toString() { return "Declaration" + fields; }
    }

    /** Map-backed fact for Submission XML. */
    @JacksonXmlRootElement(localName = "Submission")
    public static final class Submission {
        public final Map<String, Object> fields = new HashMap<>();
        @JsonAnySetter public void put(String k, Object v) { fields.put(k, v); }
        public Object get(String k) { return fields.get(k); }
        @Override public String toString() { return "Submission" + fields; }
    }

    @JacksonXmlRootElement(localName = "EvaluationResponse")
    public static final class EvaluationResponse {
        @JsonProperty("fired") public int fired;
        public EvaluationResponse() {}
        public EvaluationResponse(int fired) { this.fired = fired; }
    }

    // ---------- Endpoint that accepts XML or JSON and fires ----------

    /**
     * POST /api/evaluate   (XML or JSON)
     * Query params (optional): ?reloadRules=true&reloadToggles=true
     * Body (XML example):
     * <EvaluationRequest>
     *   <reloadRules>true</reloadRules>
     *   <reloadToggles>true</reloadToggles>
     *   <declaration><id>D-1001</id><tier>Bronze</tier><amount>180</amount></declaration>
     *   <submission><submittedBy>user-42</submittedBy><channel>web</channel></submission>
     * </EvaluationRequest>
     */
    @PostMapping(
            value = "/evaluate",
            consumes = { MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE },
            produces = { MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE }
    )
    public ResponseEntity<EvaluationResponse> evaluate(
            @RequestParam(name = "reloadRules", required = false, defaultValue = "false") boolean reloadRulesQ,
            @RequestParam(name = "reloadToggles", required = false, defaultValue = "false") boolean reloadTogglesQ,
            @RequestBody EvaluationRequest req) throws IOException {

        boolean doReloadRules   = reloadRulesQ   || (req != null && req.reloadRules);
        boolean doReloadToggles = reloadTogglesQ || (req != null && req.reloadToggles);

        if (doReloadRules) {
            System.out.println("Rebuilding KieContainer from " + rulesDir.toAbsolutePath());
            drools.rebuildFromFilesystem(rulesDir);
        }
        if (doReloadToggles && Files.exists(togglesPath)) {
            toggles.loadFromJson(togglesPath);
            System.out.println("Reloaded toggles from " + togglesPath.toAbsolutePath());
        }

        // Turn XML payload into facts (map-backed) and store as "current"
        Object declFact = (req != null && req.declaration != null) ? req.declaration : new Declaration();
        Object submFact = (req != null && req.submission  != null) ? req.submission  : new Submission();
        currentFacts.set(declFact, submFact);

        int fired = drools.fireOnce(currentFacts.get());
        return ResponseEntity.ok(new EvaluationResponse(fired));
    }
}
