package com.toddschiller.checker;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.ListTreeAnnotator;
import org.checkerframework.framework.type.TreeAnnotator;

import com.sun.source.tree.Tree;
import com.toddschiller.checker.qual.EffectType;
import com.toddschiller.checker.qual.MayAlloc;
import com.toddschiller.checker.qual.NoAlloc;

/**
 * A type factory that introduces the allocation type qualifiers
 * {@link MayAllocType} and {@link EffectType} based on the declared effects
 * {@link MayAlloc} and {@link NoAlloc} in the source code.
 * 
 * @author Todd Schiller
 */
public class AllocEffectTypeFactory extends BaseAnnotatedTypeFactory {

    protected final boolean debugSpew;

    public AllocEffectTypeFactory(BaseTypeChecker checker, boolean spew) {
        // use true for flow inference
        super(checker, false);

        debugSpew = spew;
        this.postInit();
    }

    @Override
    protected TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(super.createTreeAnnotator(), new AllocEffectsTreeAnnotator());
    }

    public ExecutableElement findJavaOverride(ExecutableElement overrider, TypeMirror parentType) {
        if (debugSpew)
            System.err.println("Searching for overridden methods from " + parentType);

        if (parentType.getKind() != TypeKind.NONE) {

            TypeElement overriderClass = (TypeElement) overrider.getEnclosingElement();
            TypeElement elem = (TypeElement) ((DeclaredType) parentType).asElement();

            for (Element e : elem.getEnclosedElements()) {
                if (e.getKind() == ElementKind.METHOD || e.getKind() == ElementKind.CONSTRUCTOR) {
                    ExecutableElement ex = (ExecutableElement) e;
                    boolean overrides = elements.overrides(overrider, ex, overriderClass);
                    if (overrides) {
                        return ex;
                    }
                }
            }
        }
        return null;
    }

    public Effect getDeclaredEffect(ExecutableElement methodElt) {
        AnnotationMirror targetNoAlloc = getDeclAnnotation(methodElt, NoAlloc.class);
        AnnotationMirror targetMayAlloc = getDeclAnnotation(methodElt, MayAlloc.class);

        if (targetNoAlloc != null) {
            return new Effect(NoAlloc.class);
        } else if (targetMayAlloc != null) {
            return new Effect(MayAlloc.class);
        }

        TypeElement targetClassElt = (TypeElement) methodElt.getEnclosingElement();

        Effect.EffectRange r = findInheritedEffectRange(targetClassElt, methodElt);
        // By default, methods may allocate memory
        return (r != null ? Effect.min(r.min, r.max) : new Effect(MayAlloc.class));
    }

    public Effect.EffectRange findInheritedEffectRange(TypeElement declaringType, ExecutableElement overridingMethod) {
        // Only the visitMethod call should pass true for warnings
        return findInheritedEffectRange(declaringType, overridingMethod, false, null);
    }

    public Effect.EffectRange findInheritedEffectRange(TypeElement declaringType, ExecutableElement overridingMethod,
            boolean issueConflictWarning, Tree errorNode) {

        assert (declaringType != null);
        ExecutableElement alloc_override = null;
        ExecutableElement safe_override = null;

        // Whether the overriding method is declared with MayAlloc
        boolean isAlloc = getDeclAnnotation(overridingMethod, MayAlloc.class) != null
                && getDeclAnnotation(overridingMethod, NoAlloc.class) == null;

        TypeMirror superclass = declaringType.getSuperclass();
        while (superclass != null && superclass.getKind() != TypeKind.NONE) {
            ExecutableElement overrides = findJavaOverride(overridingMethod, superclass);
            if (overrides != null) {
                Effect eff = getDeclaredEffect(overrides);
                assert (eff != null);
                if (eff.noAlloc()) {
                    // found a no_alloc override
                    safe_override = overrides;
                    if (isAlloc && issueConflictWarning)
                        checker.report(Result.failure("override.effect.invalid", overridingMethod, declaringType,
                                safe_override, superclass), errorNode);
                } else if (eff.mayAlloc()) {
                    // found an may_alloc override
                    alloc_override = overrides;
                } else {
                    assert false;
                }
            }
            DeclaredType decl = (DeclaredType) superclass;
            superclass = ((TypeElement) decl.asElement()).getSuperclass();
        }

        AnnotatedTypeMirror.AnnotatedDeclaredType annoDecl = fromElement(declaringType);
        for (AnnotatedTypeMirror.AnnotatedDeclaredType ty : annoDecl.directSuperTypes()) {
            ExecutableElement overrides = findJavaOverride(overridingMethod, ty.getUnderlyingType());
            if (overrides != null) {
                Effect eff = getDeclaredEffect(overrides);
                if (eff.noAlloc()) {
                    // found a no_alloc override
                    safe_override = overrides;
                    if (isAlloc && issueConflictWarning)
                        checker.report(Result.failure("override.effect.invalid", overridingMethod, declaringType,
                                safe_override, ty), errorNode);
                } else if (eff.noAlloc()) {
                    // found a may_alloc override
                    alloc_override = overrides;
                } else {
                    assert false;
                }
            }
        }

        if (alloc_override != null && safe_override != null && issueConflictWarning) {
            // There may be more than two parent methods, but for now it's
            // enough to know there are at least 2 in conflict
            checker.report(Result.warning("override.effect.warning.inheritance", overridingMethod, declaringType,
                    alloc_override.toString(), alloc_override.getEnclosingElement().asType().toString(),
                    safe_override.toString(), safe_override.getEnclosingElement().asType().toString()), errorNode);
        }

        Effect min = (safe_override != null ? new Effect(NoAlloc.class) : (alloc_override != null ? new Effect(
                MayAlloc.class) : null));
        Effect max = (alloc_override != null ? new Effect(MayAlloc.class) : (safe_override != null ? new Effect(
                NoAlloc.class) : null));

        if (debugSpew){
            System.err.println("Found " + declaringType + "." + overridingMethod + " to have inheritance pair (" + min
                    + "," + max + ")");
        }
            
        if (min == null && max == null){
            return null;
        }else{
            return new Effect.EffectRange(min, max);
        }
    }

    private class AllocEffectsTreeAnnotator extends TreeAnnotator {
        // We don't need to annotate the tree with type annotations because the EffectType
        // is the default annotation.
        
        public AllocEffectsTreeAnnotator() {
            super(AllocEffectTypeFactory.this);
        }
    }
}
