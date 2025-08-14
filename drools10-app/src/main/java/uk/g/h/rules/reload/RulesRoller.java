
package uk.g.h.rules.reload;

import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionPool;

import java.util.concurrent.atomic.AtomicReference;

public class RulesRoller {

    private final KieServices ks;
    private final AtomicReference<KieContainer> containerRef;
    private final AtomicReference<KieSessionPool> poolRef;
    private final int poolSize;

    public RulesRoller(KieServices ks,
                       AtomicReference<KieContainer> containerRef,
                       AtomicReference<KieSessionPool> poolRef,
                       int poolSize) {
        this.ks = ks;
        this.containerRef = containerRef;
        this.poolRef = poolRef;
        this.poolSize = poolSize;
    }

    public boolean reloadTo(ReleaseId rid) {
        try {
            KieContainer green = ks.newKieContainer(rid);
            KieSessionPool greenPool = green.newKieSessionPool(poolSize);
            try (KieSession s = greenPool.newKieSession()) { s.getKieBase(); }
            KieContainer blue = containerRef.getAndSet(green);
            KieSessionPool bluePool = poolRef.getAndSet(greenPool);
            try { blue.dispose(); } catch (Exception ignored) {}
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void withSession(java.util.function.Consumer<KieSession> work) {
        KieSession s = poolRef.get().newKieSession();
        try { work.accept(s); } finally { s.dispose(); }
    }
}
