
package uk.g.h.rules.reload;

import org.kie.api.builder.ReleaseId;
import java.util.Optional;

public interface RulesScanner {
    Optional<ReleaseId> detectUpdate();
}
