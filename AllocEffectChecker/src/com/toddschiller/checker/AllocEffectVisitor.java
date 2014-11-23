package com.toddschiller.checker;

import java.util.Stack;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.TreeUtils;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
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

    private final Stack<Effect> effects;
    private final Stack<MethodTree> methods;

    public AllocEffectVisitor(BaseTypeChecker checker) {
        super(checker);

        debugSpew = checker.getLintOption("debugSpew", false);

        if (debugSpew)
            System.err.println("Running AllocEffectChecker");

        effects = new Stack<Effect>();
        methods = new Stack<MethodTree>();
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

    private void checkEffect(Effect callerEffect, Effect targetEffect, Tree node) {

        if (debugSpew) {
            System.err.println("Caller effect: " + callerEffect + " Target effect: " + targetEffect);
        }

        if (targetEffect.compareTo(callerEffect) > 0) {
            checker.report(Result.failure("call.invalid.alloc", targetEffect, callerEffect), node);
        }
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
        if (debugSpew) {
            System.err.println("For invocation " + node + " in " + methods.peek().getName());
        }

        ExecutableElement methodElt = TreeUtils.elementFromUse(node);

        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());

        if (callerTree == null) {
            // XXX: static initializer; need to check for the Alloc effect
            return super.visitMethodInvocation(node, p);
        }

        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);
        Effect targetEffect = atypeFactory.getDeclaredEffect(methodElt);

        // Sanity check for the caller effect
        Effect callerEffect = atypeFactory.getDeclaredEffect(callerElt);
        assert (callerEffect.equals(effects.peek()));

        checkEffect(callerEffect, targetEffect, node);

        return super.visitMethodInvocation(node, p);
    }

    @Override
    public Void visitNewArray(NewArrayTree node, Void p) {
        if (debugSpew) {
            System.err.println("For new array " + node + " in " + methods.peek().getName());
        }

        Effect targetEffect = new Effect(MayAlloc.class);

        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);

        if (callerTree == null) {
            // XXX: static initializer; need to check for the Alloc effect
            return super.visitNewArray(node, p);
        }

        // Sanity check for the caller effect
        Effect callerEffect = atypeFactory.getDeclaredEffect(callerElt);
        assert (callerEffect.equals(effects.peek()));

        checkEffect(callerEffect, targetEffect, node);

        return super.visitNewArray(node, p);
    }

    @Override
    protected boolean checkConstructorInvocation(AnnotatedDeclaredType dt, AnnotatedExecutableType constructor, Tree src) {

        if (debugSpew) {
            System.err.println("For constructor " + src + " in " + methods.peek().getName());
        }

        Effect targetEffect = new Effect(MayAlloc.class);

        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);

        if (callerTree == null) {
            // XXX: static initializer; need to check for the Alloc effect
            return super.checkConstructorInvocation(dt, constructor, src);
        }

        // Sanity check for the caller effect
        Effect callerEffect = atypeFactory.getDeclaredEffect(callerElt);
        assert (callerEffect.equals(effects.peek()));

        checkEffect(callerEffect, targetEffect, src);

        return super.checkConstructorInvocation(dt, constructor, src);
    }

    @Override
    public Void visitMethod(MethodTree node, Void p) {
        ExecutableElement methElt = TreeUtils.elementFromDeclaration(node);

        if (debugSpew)
            System.err.println("\nVisiting method " + methElt);

        AnnotationMirror mayAlloc = atypeFactory.getDeclAnnotation(methElt, MayAlloc.class);
        AnnotationMirror noAlloc = atypeFactory.getDeclAnnotation(methElt, NoAlloc.class);

        if (mayAlloc != null && noAlloc != null) {
            checker.report(Result.failure("annotations.conflicts"), node);
        }

        @SuppressWarnings("unused")
        // call has side-effects
        Effect.EffectRange range = atypeFactory.findInheritedEffectRange(((TypeElement) methElt.getEnclosingElement()),
                methElt, true, node);

        if (mayAlloc == null && noAlloc == null) {
            // implicitly annotate the method with the LUB of the effects of the
            // methods it overrides
            atypeFactory.fromElement(methElt).addAnnotation(atypeFactory.getDeclaredEffect(methElt).getAnnotation());
        }

        methods.push(node);
        effects.push(atypeFactory.getDeclaredEffect(methElt));

        if (debugSpew){
            System.err.println("Pushing " + effects.peek() + " onto the stack when checking " + methElt);
        }
            
        Void ret = super.visitMethod(node, p);

        methods.pop();
        effects.pop();

        return ret;
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        // TODO: Same effect checks as for methods
        return super.visitMemberSelect(node, p);
    }

    @Override
    public Void visitClass(ClassTree node, Void p) {
        methods.push(null);
        effects.push(new Effect(MayAlloc.class));

        Void ret = super.visitClass(node, p);

        methods.pop();
        effects.pop();
        return ret;
    }
}
