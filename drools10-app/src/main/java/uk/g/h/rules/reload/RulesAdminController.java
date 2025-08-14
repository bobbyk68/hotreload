
package uk.g.h.rules.reload;

import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/rules")
public class RulesAdminController {

    private final AtomicReference<KieContainer> containerRef;
    private final RepoScanScheduler scheduler;
    private final RulesRoller roller;

    public RulesAdminController(AtomicReference<KieContainer> containerRef,
                                RepoScanScheduler scheduler,
                                RulesRoller roller) {
        this.containerRef = containerRef;
        this.scheduler = scheduler;
        this.roller = roller;
    }

    @GetMapping("/version")
    public Map<String, String> version() {
        ReleaseId rid = containerRef.get().getReleaseId();
        return Map.of("groupId", rid.getGroupId(),
                      "artifactId", rid.getArtifactId(),
                      "version", rid.getVersion());
    }

    @PostMapping("/scan-now")
    public Map<String, Object> scanNow() {
        return Map.of("changed", scheduler.scanOnce());
    }

    @PostMapping("/flip-to")
    public Map<String, Object> flipTo(@RequestParam String gav) {
        String[] p = gav.split(":");
        ReleaseId rid = KieServices.Factory.get().newReleaseId(p[0], p[1], p[2]);
        boolean ok = roller.reloadTo(rid);
        return Map.of("reloaded", ok, "target", gav);
    }
}
