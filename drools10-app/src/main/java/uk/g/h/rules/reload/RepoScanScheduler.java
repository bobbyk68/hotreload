
package uk.g.h.rules.reload;

import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Optional;

public class RepoScanScheduler {

    private final RulesScanner scanner;
    private final RulesRoller roller;
    @SuppressWarnings("unused") private final KieServices ks;

    public RepoScanScheduler(RulesScanner scanner, RulesRoller roller, KieServices ks) {
        this.scanner = scanner;
        this.roller = roller;
        this.ks = ks;
    }

    @Scheduled(fixedDelayString = "${rules.scan.period-ms:10000}")
    public void scan() {
        Optional<ReleaseId> update = scanner.detectUpdate();
        update.ifPresent(roller::reloadTo);
    }

    public boolean scanOnce() {
        Optional<ReleaseId> update = scanner.detectUpdate();
        return update.map(roller::reloadTo).orElse(false);
    }
}
