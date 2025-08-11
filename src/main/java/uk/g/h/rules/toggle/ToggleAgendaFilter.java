package uk.g.h.rules.toggle;

import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.Match;

public final class ToggleAgendaFilter implements AgendaFilter {
    private final RuleToggleService toggles;

    public ToggleAgendaFilter(RuleToggleService toggles) {
        this.toggles = toggles;
    }

    @Override
    public boolean accept(Match match) {
        Rule r = match.getRule();
        Object key = r.getMetaData().get("RuleKey");
        if (key == null) key = r.getMetaData().get("RuleId");
        if (key == null) key = r.getName();
        return toggles.isEnabled(String.valueOf(key));
    }
}