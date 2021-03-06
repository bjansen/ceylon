package org.eclipse.ceylon.compiler.java.runtime.metamodel.decl;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import org.eclipse.ceylon.compiler.java.metadata.Ceylon;
import org.eclipse.ceylon.compiler.java.metadata.Class;
import org.eclipse.ceylon.compiler.java.metadata.Ignore;
import org.eclipse.ceylon.compiler.java.metadata.SatisfiedTypes;
import org.eclipse.ceylon.compiler.java.runtime.metamodel.AnnotationBearing;
import org.eclipse.ceylon.compiler.java.runtime.metamodel.Metamodel;
import org.eclipse.ceylon.compiler.java.runtime.metamodel.Reflection;
import org.eclipse.ceylon.compiler.java.runtime.model.TypeDescriptor;
import org.eclipse.ceylon.model.loader.NamingBase;

import org.eclipse.ceylon.compiler.java.runtime.metamodel.decl.NestableDeclarationImpl;
import org.eclipse.ceylon.compiler.java.runtime.metamodel.decl.SetterDeclarationImpl;
import org.eclipse.ceylon.compiler.java.runtime.metamodel.decl.ValueDeclarationImpl;

import ceylon.language.meta.declaration.OpenType;
import ceylon.language.meta.declaration.SetterDeclaration;
import ceylon.language.meta.declaration.SetterDeclaration$impl;
import ceylon.language.meta.declaration.ValueDeclaration;

@Ceylon(major = 8)
@Class
@SatisfiedTypes({"ceylon.language.meta.declaration::SetterDeclaration"})
public class SetterDeclarationImpl 
        extends NestableDeclarationImpl
        implements SetterDeclaration, AnnotationBearing {

    @Ignore
    public static final TypeDescriptor $TypeDescriptor$ = TypeDescriptor.klass(SetterDeclarationImpl.class);
    
    private ValueDeclarationImpl value;
    
    private Method declaredSetter;

    public SetterDeclarationImpl(org.eclipse.ceylon.model.typechecker.model.Setter setter) {
        super(setter);
    }

    private void checkInit(){
        // must be deferred because getter/setter refer to one another
        if(value == null){
            synchronized(Metamodel.getLock()){
                if(value == null){
                    org.eclipse.ceylon.model.typechecker.model.Setter setter = ((org.eclipse.ceylon.model.typechecker.model.Setter)declaration);
                    this.value = (ValueDeclarationImpl) Metamodel.getOrCreateMetamodel(setter.getGetter());
                    java.lang.Class<?> javaClass = Metamodel.getJavaClass(value.declaration);
                    String setterName = NamingBase.getSetterName(value.declaration);
                    this.declaredSetter = Reflection.getDeclaredSetter(javaClass, setterName);
                }
            }
        }
    }
    
    @Ignore
    @Override
    public SetterDeclaration$impl $ceylon$language$meta$declaration$SetterDeclaration$impl() {
        return null;
    }

    @Override
    @Ignore
    public Annotation[] $getJavaAnnotations$() {
        checkInit();
        return declaredSetter != null ? declaredSetter.getDeclaredAnnotations() : AnnotationBearing.NONE;
    }
    
    @Override
    @Ignore
    public boolean $isAnnotated$(java.lang.Class<? extends java.lang.annotation.Annotation> annotationType) {
        checkInit();
        final AnnotatedElement element = declaredSetter;
        return element != null ? element.isAnnotationPresent(annotationType) : false;
    }
    
    @Override
    public <AnnotationType extends java.lang.annotation.Annotation> boolean annotated(TypeDescriptor reifed$AnnotationType) {
        return Metamodel.isAnnotated(reifed$AnnotationType, this);
    }
    
    @Override
    public ValueDeclaration getVariable() {
        checkInit();
        return value;
    }

    @Override
    public OpenType getOpenType() {
        checkInit();
        return value.getOpenType();
    }

    @Override
    public int hashCode() {
        return Metamodel.hashCode(this, "setter");
    }
    
    @Override
    public boolean equals(Object obj) {
        if(obj == null)
            return false;
        if(obj == this)
            return true;
        if(obj instanceof SetterDeclarationImpl == false)
            return false;
        return Metamodel.equalsForSameType(this, (SetterDeclarationImpl)obj);
    }

    @Ignore
    @Override
    public TypeDescriptor $getType$() {
        return $TypeDescriptor$;
    }
    
    @Override
    public String toString() {
        return "assign "+super.toString();
    }
}
