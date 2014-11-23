package com.toddschiller.checker;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.TypeQualifiers;
import org.checkerframework.framework.source.SupportedLintOptions;

import com.toddschiller.checker.qual.EffectType;
import com.toddschiller.checker.qual.MayAlloc;
import com.toddschiller.checker.qual.NoAlloc;

/**
 * A checker that enforces that allocations may not occur in areas marked as
 * {@link NoAlloc}.
 * <p>
 * Note that the checker claims the {@link EffectType} type qualifier as opposed to 
 * the method annotations {@link MayAlloc} and {@link NoAlloc} that the developer will 
 * actually write in practice. The {@link AllocEffectTypeFactory} will add the type 
 * annotations when checking the code. 
 * <p>
 * For the basic effect system implemented here, only the method annotations are checked; 
 * the type annotation exists because the Checker Framework expects to see a type annotation
 * on each type.
 * <p>
 * If we wanted to to support effect polymorphism like the GUI effect
 * checker does, {@link org.checkerframework.checker.guieffect.GuiEffectChecker}, we need to track
 * the expression types with type annotations, e.g., <tt>MayAllocType</tt> and <tt>NoAllocType</tt>.
 * 
 * @author Todd Schiller
 */
@SupportedLintOptions({ "debugSpew" })
@TypeQualifiers({ EffectType.class })
public class AllocEffectChecker extends BaseTypeChecker {
}