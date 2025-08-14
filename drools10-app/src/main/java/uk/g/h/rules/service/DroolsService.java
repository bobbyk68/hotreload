
package uk.g.h.rules.service;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionPool;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class DroolsService {

    private final AtomicReference<KieSessionPool> poolRef;
    private final AtomicReference<KieContainer> containerRef;

    public DroolsService(AtomicReference<KieSessionPool> poolRef,
                         AtomicReference<KieContainer> containerRef) {
        this.poolRef = poolRef;
        this.containerRef = containerRef;
    }

    public int fireAll(Object... facts) {
        KieSession s = poolRef.get().newKieSession();
        try {
            for (Object f : facts) s.insert(f);
            return s.fireAllRules();
        } finally {
            s.dispose();
        }
    }

    public String currentGav() {
        var rid = containerRef.get().getReleaseId();
        return rid.getGroupId() + ":" + rid.getArtifactId() + ":" + rid.getVersion();
    }
}
