package com.toddschiller.checker;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.TreeUtils;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.Tree;
import com.toddschiller.checker.qual.MayAlloc;
import com.toddschiller.checker.qual.NoAlloc;

/**
 * Enforce that no memory allocations are performed in methods marked
 * {@link NoAlloc}.
 * <p>
 * Adapted from the GUI effect checker's
 * {@link org.checkerframework.checker.guieffect.GuiEffectVisitor}.
 * 
 * @author Todd Schiller
 */
public final class AllocEffectVisitor extends BaseTypeVisitor<AllocEffectTypeFactory> {

    private final boolean debugSpew;

    public AllocEffectVisitor(BaseTypeChecker checker) {
        super(checker);

        debugSpew = checker.getLintOption("debugSpew", false);

        if (debugSpew){
            System.err.println("Running AllocEffectChecker");
        }
    }

    @Override
    protected AllocEffectTypeFactory createTypeFactory() {
        return new AllocEffectTypeFactory(checker, debugSpew);
    }

    @Override
    public boolean isValidUse(AnnotatedDeclaredType declarationType, AnnotatedDeclaredType useType, Tree tree) {
        // In our basic effect system, we're only checking method annotations
        return true;
    }

    @Override
    protected boolean checkOverride(MethodTree overriderTree, AnnotatedDeclaredType overridingType,
            AnnotatedExecutableType overridden, AnnotatedDeclaredType overriddenType, Void p) {

        // Method override validity is checked manually by the type factory
        // during visitation
        return true;
    }

    /**
     * Emit an error if {@code targerEffect} is a supertype of {@code callerEffect}.
     */
    private void checkEffect(Effect callerEffect, Effect targetEffect, Tree node) {
        if (debugSpew) {
            System.err.println("Caller effect: " + callerEffect + " Target effect: " + targetEffect);
        }

        if (targetEffect.compareTo(callerEffect) > 0) {
            // The target effect is a supertype of the effect of the enclosing method
            checker.report(Result.failure("call.invalid.alloc", targetEffect, callerEffect), node);
        }
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());

        if (debugSpew) {
            System.err.println("For invocation " + node + " in " + callerTree.getName());
        }
       
        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);
        Effect callerEffect = atypeFactory.getDeclaredEffect(callerElt);
       
        ExecutableElement targetElt = TreeUtils.elementFromUse(node);
        Effect targetEffect = atypeFactory.getDeclaredEffect(targetElt);

        checkEffect(callerEffect, targetEffect, node);

        return super.visitMethodInvocation(node, p);
    }

    @Override
    public Void visitNewArray(NewArrayTree node, Void p) {
        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
      
        if (debugSpew) {
            System.err.println("For new array " + node + " in " + callerTree.getName());
        }

        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);
        Effect callerEffect = atypeFactory.getDeclaredEffect(callerElt);

        Effect targetEffect = new Effect(MayAlloc.class);

        checkEffect(callerEffect, targetEffect, node);

        return super.visitNewArray(node, p);
    }

    @Override
    protected boolean checkConstructorInvocation(AnnotatedDeclaredType dt, AnnotatedExecutableType constructor, Tree src) {
        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
        
        if (debugSpew) {
            System.err.println("For constructor " + src + " in " + callerTree.getName());
        }

        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);
        Effect callerEffect = atypeFactory.getDeclaredEffect(callerElt);

        Effect targetEffect = new Effect(MayAlloc.class);
        
        checkEffect(callerEffect, targetEffect, src);

        return super.checkConstructorInvocation(dt, constructor, src);
    }

    @Override
    public Void visitMethod(MethodTree node, Void p) {
        ExecutableElement methElt = TreeUtils.elementFromDeclaration(node);

        if (debugSpew){
            System.err.println("\nVisiting method " + methElt);
        }
            
        AnnotationMirror mayAlloc = atypeFactory.getDeclAnnotation(methElt, MayAlloc.class);
        AnnotationMirror noAlloc = atypeFactory.getDeclAnnotation(methElt, NoAlloc.class);

        if (mayAlloc != null && noAlloc != null) {
            checker.report(Result.failure("annotations.conflicts"), node);
        }

        @SuppressWarnings("unused") // call has side-effects
        Effect.EffectRange range = atypeFactory.findInheritedEffectRange(((TypeElement) methElt.getEnclosingElement()),
                methElt, true, node);

        if (mayAlloc == null && noAlloc == null) {
            // implicitly annotate the method with the LUB of the effects of the
            // methods it overrides
            atypeFactory.fromElement(methElt).addAnnotation(atypeFactory.getDeclaredEffect(methElt).getAnnotation());
        }

        return super.visitMethod(node, p);
    }
}
