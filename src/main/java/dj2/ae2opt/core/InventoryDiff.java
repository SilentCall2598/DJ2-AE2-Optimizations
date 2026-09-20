package dj2.ae2opt.core;

import java.util.List;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

public final class InventoryDiff {

    public interface DeltaFactory<T> {
        T delta(T identitySource, long deltaAmount);
    }

    private InventoryDiff() {
    }

    public static <T> void compute(Iterable<T> newEntries, UnaryOperator<T> oldLookup,
                                    Iterable<T> oldEntries, UnaryOperator<T> newLookup,
                                    ToLongFunction<T> sizeOf, DeltaFactory<T> deltaFactory,
                                    List<T> changesOut) {
        for (T n : newEntries) {
            final T old = oldLookup.apply(n);
            final long oldSize = old == null ? 0L : sizeOf.applyAsLong(old);
            final long newSize = sizeOf.applyAsLong(n);
            if (newSize != oldSize) {
                changesOut.add(deltaFactory.delta(n, newSize - oldSize));
            }
        }
        for (T o : oldEntries) {
            if (newLookup.apply(o) != null) {
                continue;
            }
            final long oldSize = sizeOf.applyAsLong(o);
            if (oldSize != 0L) {
                changesOut.add(deltaFactory.delta(o, -oldSize));
            }
        }
    }
}
