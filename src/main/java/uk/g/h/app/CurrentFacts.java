package uk.g.h.app;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe holder for the "current" facts used on auto re-fire. */
public final class CurrentFacts {
    private final AtomicReference<Object[]> ref = new AtomicReference<>(new Object[0]);
    public void set(Object... facts) { ref.set(Objects.requireNonNullElseGet(facts, () -> new Object[0])); }
    public Object[] get() { return Objects.requireNonNullElseGet(ref.get(), () -> new Object[0]); }
}
