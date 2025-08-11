package uk.g.h.rules.toggle;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface RuleToggleService {
    boolean isEnabled(String ruleKey);
    void putAll(Map<String, Boolean> flags);
}

public final class JsonBackedRuleToggleService implements RuleToggleService {
    private final ConcurrentHashMap<String, Boolean> flags = new ConcurrentHashMap<>();
    private final boolean defaultEnabled;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonBackedRuleToggleService(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    @Override
    public boolean isEnabled(String ruleKey) {
        return flags.getOrDefault(ruleKey, defaultEnabled);
    }

    @Override
    public void putAll(Map<String, Boolean> newFlags) {
        flags.clear();
        flags.putAll(newFlags);
    }

    public void loadFromJson(Path jsonPath) throws IOException {
        byte[] bytes = Files.readAllBytes(jsonPath);
        Map<String, Boolean> m = mapper.readValue(
            bytes, mapper.getTypeFactory().constructMapType(Map.class, String.class, Boolean.class)
        );
        putAll(m);
    }
}