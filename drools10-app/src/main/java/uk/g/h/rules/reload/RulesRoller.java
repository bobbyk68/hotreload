package uk.g.h.rules.reload;

import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieContainerSessionsPool;
import org.kie.api.runtime.KieSession;

import java.util.concurrent.atomic.AtomicReference;

public class RulesRoller {

    private final KieServices ks;
    private final AtomicReference<KieContainer> containerRef;
    private final AtomicReference<KieContainerSessionsPool> poolRef;
    private final int poolSize;

    public RulesRoller(KieServices ks,
                       AtomicReference<KieContainer> containerRef,
                       AtomicReference<KieContainerSessionsPool> poolRef,
                       int poolSize) {
        this.ks = ks;
        this.containerRef = containerRef;
        this.poolRef = poolRef;
        this.poolSize = poolSize;
    }

    /** Build "green", warm up, flip atomically, dispose "blue". */
    public boolean reloadTo(ReleaseId rid) {
        try {
            // Build the new container + pool
            KieContainer green = ks.newKieContainer(rid);
            KieContainerSessionsPool greenPool = green.newKieSessionsPool(poolSize);

            // Warm up (no try-with-resources; KieSession isn't AutoCloseable)
            KieSession s = greenPool.newKieSession();
            try {
                s.getKieBase(); // force initialization/compilation now
                // optionally: set globals, insert smoke fact, or s.fireAllRules(0);
            } finally {
                s.dispose();
            }

            // Flip pointers
            KieContainer blue = containerRef.getAndSet(green);
            KieContainerSessionsPool bluePool = poolRef.getAndSet(greenPool);

            // Dispose old container (sessions checked out from old pool should be allowed to finish elsewhere)
            try { blue.dispose(); } catch (Exception ignored) {}

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Helper to run work with the current pool. */
    public void withSession(java.util.function.Consumer<KieSession> work) {
        KieSession ksess = poolRef.get().newKieSession();
        try { work.accept(ksess); } finally { ksess.dispose(); }
    }
}
