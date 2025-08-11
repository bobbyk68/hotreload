package uk.g.h.app;

import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.g.h.rules.runtime.CurrentFacts;
import uk.g.h.rules.runtime.DroolsService;
import uk.g.h.rules.toggle.JsonBackedRuleToggleService;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class AppConfig {

    @Bean
    public KieContainer initialContainer(@Value("${rules.dir:src/main/resources/rules}") String rulesDir) {
        return DroolsService.buildFromRulesDir(Path.of(rulesDir)); // XML-free build
    }

    @Bean
    public JsonBackedRuleToggleService toggleService(
            @Value("${toggles.file:config/toggles.json}") String togglesFile) throws Exception {
        var svc = new JsonBackedRuleToggleService(true);
        Path p = Path.of(togglesFile);
        if (Files.exists(p)) svc.loadFromJson(p);
        return svc;
    }

    @Bean
    public DroolsService droolsService(KieContainer initialContainer, JsonBackedRuleToggleService toggles) {
        return new DroolsService(initialContainer, toggles); // your non-annotated class managed by Spring
    }

    @Bean
    public CurrentFacts currentFacts() { return new CurrentFacts(); }
}
