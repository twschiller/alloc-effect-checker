package com.toddschiller.checker;

import java.lang.annotation.Annotation;

import com.toddschiller.checker.qual.MayAlloc;
import com.toddschiller.checker.qual.NoAlloc;

/**
 * Represents an allocation effect with the relationship
 * <code>NoAlloc &lt;: MayAlloc</code>.
 * <p>
 * Adapted from the GUI effect type checker's
 * {@link org.checkerframework.checker.guieffect.Effect}.
 * 
 * @author Todd Schiller
 */
public final class Effect implements Comparable<Effect> {

    private final Class<? extends Annotation> clazz;

    public Effect(Class<? extends Annotation> clazz) {
        assert (clazz.equals(NoAlloc.class) || clazz.equals(MayAlloc.class));
        this.clazz = clazz;
    }

    public boolean mayAlloc() {
        return clazz.equals(MayAlloc.class);
    }

    public boolean noAlloc() {
        return clazz.equals(NoAlloc.class);
    }

    public Class<? extends Annotation> getAnnotation() {
        return clazz;
    }

    @Override
    public String toString() {
        return clazz.getSimpleName();
    }

    public boolean equals(Effect other) {
        return clazz.equals(other.clazz);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Effect && this.equals((Effect) other);
    }

    public static Effect min(Effect l, Effect r) {
        return l.compareTo(r) <= 0 ? l : r;
    }

    @Override
    public int compareTo(Effect other) {
        assert (other != null);

        // MayAlloc is the upper-bound
        if (this.mayAlloc() == other.mayAlloc()) {
            return 0;
        } else if (this.mayAlloc()) {
            return 1;
        } else {
            return -1;
        }
    }

    public static final class EffectRange {
        public final Effect min, max;

        public EffectRange(Effect min, Effect max) {
            assert (min != null || max != null);
            // If one is null, fill in with the other
            this.min = (min != null ? min : max);
            this.max = (max != null ? max : min);
        }
    }
}
