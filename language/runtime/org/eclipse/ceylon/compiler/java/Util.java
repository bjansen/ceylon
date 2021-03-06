package org.eclipse.ceylon.compiler.java;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;

import org.eclipse.ceylon.common.NonNull;
import org.eclipse.ceylon.common.Nullable;
import org.eclipse.ceylon.compiler.java.language.AbstractArrayIterable;
import org.eclipse.ceylon.compiler.java.language.AbstractIterable;
import org.eclipse.ceylon.compiler.java.language.AbstractIterator;
import org.eclipse.ceylon.compiler.java.language.ObjectArray;
import org.eclipse.ceylon.compiler.java.language.ObjectArrayIterable;
import org.eclipse.ceylon.compiler.java.metadata.Class;
import org.eclipse.ceylon.compiler.java.metadata.Ignore;
import org.eclipse.ceylon.compiler.java.metadata.Name;
import org.eclipse.ceylon.compiler.java.metadata.TypeInfo;
import org.eclipse.ceylon.compiler.java.runtime.metamodel.Metamodel;
import org.eclipse.ceylon.compiler.java.runtime.metamodel.decl.ClassOrInterfaceDeclarationImpl;
import org.eclipse.ceylon.compiler.java.runtime.model.ReifiedType;
import org.eclipse.ceylon.compiler.java.runtime.model.TypeDescriptor;
import org.eclipse.ceylon.compiler.java.runtime.model.TypeDescriptor.Nothing;
import org.eclipse.ceylon.model.cmr.ArtifactResult;

import org.eclipse.ceylon.compiler.java.Util;

import ceylon.language.Array;
import ceylon.language.ArraySequence;
import ceylon.language.AssertionError;
import ceylon.language.Callable;
import ceylon.language.Finished;
import ceylon.language.Integer;
import ceylon.language.Iterable;
import ceylon.language.Iterator;
import ceylon.language.Null;
import ceylon.language.OverflowException;
import ceylon.language.Ranged;
import ceylon.language.Sequence;
import ceylon.language.Sequential;
import ceylon.language.Tuple;
import ceylon.language.empty_;
import ceylon.language.finished_;
import ceylon.language.sequence_;
import ceylon.language.meta.classDeclaration_;
import ceylon.language.meta.typeLiteral_;
import ceylon.language.meta.type_;
import ceylon.language.meta.declaration.ClassOrInterfaceDeclaration;
import ceylon.language.meta.model.ClassOrInterface;
import ceylon.language.meta.model.Type;

/**
 * Helper class for generated Ceylon code that needs to call implementation logic.
 * 
 * @author Stéphane Épardaud <stef@epardaud.fr>
 */
public class Util {

    static {
        // Make sure the rethrow class is loaded if ever we need to rethrow
        // errors such as StackOverflowError, otherwise if we have to rethrow it
        // we will not be able to load that class since we've ran out of stack
        // see https://github.com/ceylon/ceylon.language/issues/311
        ceylon.language.impl.rethrow_.class.toString();
    }
    
    private static final int INIT_ARRAY_SIZE = 10;
    private static final Object[] NO_ELEMENTS = new Object[0];
    
    public static String declClassName(String name) {
        return name.replace("::", ".");
    }

    public static void loadModule(String name, String version, 
    		ArtifactResult result, ClassLoader classLoader) {
        Metamodel.loadModule(name, version, result, classLoader);
    }
    
    public static boolean isReified(java.lang.Object o, TypeDescriptor type) {
        return Metamodel.isReified(o, type);
    }

    private static HashMap<java.lang.Class<?>, Class> classCache
     = new HashMap<>();
    
    private static Class getClassAnnotationForIdentifiableOrBasic(
            final java.lang.Class<? extends Object> klass) {
        if (classCache.containsKey(klass)) {
            return classCache.get(klass);
        }
        Class classAnnotation = klass.getAnnotation(Class.class);
        if(classAnnotation != null) {
            classCache.put(klass, classAnnotation);
            return classAnnotation;
        } else if (klass != null && klass != java.lang.Object.class) {
            // else keep looking up
            Class c = getClassAnnotationForIdentifiableOrBasic(klass.getSuperclass());
            classCache.put(klass, c);
            return c;
        } else {
            return null;
        }
    }

    /**
     * Returns true if the given object satisfies ceylon.language.Identifiable
     */
    public static boolean isIdentifiable(java.lang.Object o) {
        if(o == null)
            return false;
        return isIdentifiable(o.getClass());
    }
    
    public static boolean isIdentifiable(java.lang.Class<?> klazz) {
        Class classAnnotation = getClassAnnotationForIdentifiableOrBasic(klazz);
        // unless marked as NOT identifiable, every instance is Identifiable
        return classAnnotation != null ? classAnnotation.identifiable() : true;
    }
    
    /**
     * Returns true if the given object extends ceylon.language.Basic
     */
    public static boolean isBasic(java.lang.Object o) {
        if (o == null)
            return false;
        return isBasic(o.getClass());
    }

    public static boolean isBasic(java.lang.Class<?> klazz) {
        Class classAnnotation = getClassAnnotationForIdentifiableOrBasic(klazz);
        // unless marked as NOT identifiable, every instance is Basic
        return classAnnotation != null ? classAnnotation.basic() : true;
    }
    
    //
    // Java variadic conversions
    
    /** Return the size of the given iterable if we know it can be computed 
     * efficiently (typically without iterating the iterable)
     */
    private static <T> int fastIterableSize(Iterable<? extends T, ?> iterable) {
        if (iterable instanceof Sequential
                || iterable instanceof AbstractArrayIterable) {
            return toInt(iterable.getSize());
        }
        return -1;
    }
    
    @SuppressWarnings("unchecked")
    private static <T> void fillArray(T[] array, int offset, 
            Iterable<? extends T, ?> iterable) {
        Iterator<?> iterator = iterable.iterator();
        Object o;
        int index = offset;
        while ((o = iterator.next()) != finished_.get_()) {
            array[index] = (T) o;
            index++;
        }
    }
    
    /**
     * Base class for a family of builders for Java native arrays.
     *  
     * Encapsulation has been sacraficed for efficiency.
     * 
     * @param <A> an array type such as int[]
     */
    public static abstract class ArrayBuilder<A> {
        private static final int MIN_CAPACITY = 5;
        private static final int MAX_CAPACITY = java.lang.Integer.MAX_VALUE;
        /** The number of elements in {@link #array}. This is always <= {@link #capacity} */
        protected int size;
        /** The length of {@link #array} */
        protected int capacity;
        /** The array */
        protected A array;
        ArrayBuilder(int initialSize) {
            capacity = Math.max(initialSize, MIN_CAPACITY);
            array = allocate(capacity);
            size = 0;
        }
        /** Append all the elements in the given array */
        final void appendArray(A elements) {
            int increment = size(elements);
            int newsize = this.size + increment;
            ensure(newsize);
            System.arraycopy(elements, 0, array, this.size, increment);
            this.size = newsize;
        }
        /** Ensure the {@link #array} is as big, or bigger than the given capacity */
        protected final void ensure(int requestedCapacity) {
            if (this.capacity >= requestedCapacity) {
                return;
            }
            
            int newcapacity = requestedCapacity+(requestedCapacity>>1);
            if (newcapacity < MIN_CAPACITY) {
                newcapacity = MIN_CAPACITY;
            } else if (newcapacity > MAX_CAPACITY) {
                newcapacity = requestedCapacity;
                if (newcapacity > MAX_CAPACITY) {
                    throw new AssertionError("can't allocate array bigger than " + MAX_CAPACITY);
                }
            }
            
            A newArray = allocate(newcapacity);
            System.arraycopy(this.array, 0, newArray, 0, this.size);
            this.capacity = newcapacity;
            this.array = newArray;
        }
        
        /**
         * Allocate and return an array of the given size
         */
        protected abstract A allocate(int size);
        /**
         * The size of the given array
         */
        protected abstract int size(A array);
        
        /**
         * Returns an array of exactly the right size to contain all the 
         * appended elements.
         */
        A build() {
            if (this.capacity == this.size) {
                return array;
            }
            A result = allocate(this.size);
            System.arraycopy(this.array, 0, result, 0, this.size);
            return result;
        }
    }
    
    /** 
     * An array builder whose result is an {@code Object[]}.
     * @see ReflectingObjectArrayBuilder
     */
    static final class ObjectArrayBuilder 
            extends ArrayBuilder<Object[]> {
        ObjectArrayBuilder(int initialSize) {
            super(initialSize);
        }
        @Override
        protected Object[] allocate(int size) {
            return new Object[size];
        }
        @Override
        protected int size(Object[] array) {
            return array.length;
        }
        
        void appendRef(Object t) {
            ensure(size+1);
            array[size] = t;
            size++;
        }
    }
    /** 
     * An array builder whose result is an array of a given component type, that is, {@code T[]}.
     * The intermediate arrays are {@code Object[]}.
     * @see ObjectArrayBuilder
     */
    public static final class ReflectingObjectArrayBuilder<T> 
            extends ArrayBuilder<T[]> {
        private final java.lang.Class<T> klass;
        public ReflectingObjectArrayBuilder(int initialSize, 
                java.lang.Class<T> klass) {
            super(initialSize);
            this.klass = klass;
        }
        @SuppressWarnings("unchecked")
        @Override
        protected T[] allocate(int size) {
            return (T[])new Object[size];
        }
        @Override
        protected int size(T[] array) {
            return array.length;
        }
        
        public void appendRef(T t) {
            ensure(size+1);
            array[size] = t;
            size++;
        }
        public T[] build() {
            @SuppressWarnings("unchecked")
            T[] result = (T[])java.lang.reflect.Array.newInstance(klass, this.size);
            System.arraycopy(this.array, 0, result, 0, this.size);
            return result;
        }
    }
    /** 
     * An array builder whose result is a {@code int[]}.
     */
    static final class IntArrayBuilder 
            extends ArrayBuilder<int[]> {

        IntArrayBuilder(int initialSize) {
            super(initialSize);
        }

        @Override
        protected int[] allocate(int size) {
            return new int[size];
        }

        @Override
        protected int size(int[] array) {
            return array.length;
        }
        
        void appendInt(int i) {
            ensure(size+1);
            array[size] = i;
            size++;
        }
        
        void appendLong(long i) {
            appendInt(toInt(i));
        }
    }
    
    /** 
     * An array builder whose result is a {@code long[]}.
     */
    static final class LongArrayBuilder 
            extends ArrayBuilder<long[]> {

        LongArrayBuilder(int initialSize) {
            super(initialSize);
        }

        @Override
        protected long[] allocate(int size) {
            return new long[size];
        }

        @Override
        protected int size(long[] array) {
            return array.length;
        }
        
        void appendLong(long i) {
            ensure(size+1);
            array[size] = i;
            size++;
        }
    }
    
    /** 
     * An array builder whose result is a {@ocde boolean[]}.
     */
    static final class BooleanArrayBuilder 
            extends ArrayBuilder<boolean[]> {

        BooleanArrayBuilder(int initialSize) {
            super(initialSize);
        }

        @Override
        protected boolean[] allocate(int size) {
            return new boolean[size];
        }

        @Override
        protected int size(boolean[] array) {
            return array.length;
        }
        
        void appendBoolean(boolean b) {
            ensure(size+1);
            array[size] = b;
            size++;
        }
    }
    
    /** 
     * An array builder whose result is a {@code byte[]}.
     */
    static final class ByteArrayBuilder 
            extends ArrayBuilder<byte[]> {

        ByteArrayBuilder(int initialSize) {
            super(initialSize);
        }

        @Override
        protected byte[] allocate(int size) {
            return new byte[size];
        }

        @Override
        protected int size(byte[] array) {
            return array.length;
        }
        
        void appendByte(byte b) {
            ensure(size+1);
            array[size] = b;
            size++;
        }
        
    }
    
    /** 
     * An array builder whose result is a {@code short[]}.
     */
    static final class ShortArrayBuilder 
            extends ArrayBuilder<short[]> {

        ShortArrayBuilder(int initialSize) {
            super(initialSize);
        }

        @Override
        protected short[] allocate(int size) {
            return new short[size];
        }

        @Override
        protected int size(short[] array) {
            return array.length;
        }
        
        void appendShort(short b) {
            ensure(size+1);
            array[size] = b;
            size++;
        }
        
        void appendLong(long b) {
            appendShort(toShort(b));
        }
    }
    
    /** 
     * An array builder whose result is a {@code double[]}.
     */
    static final class DoubleArrayBuilder 
            extends ArrayBuilder<double[]> {

        DoubleArrayBuilder(int initialSize) {
            super(initialSize);
        }

        @Override
        protected double[] allocate(int size) {
            return new double[size];
        }

        @Override
        protected int size(double[] array) {
            return array.length;
        }
        
        void appendDouble(double i) {
            ensure(size+1);
            array[size] = i;
            size++;
        }
    }
    
    /** 
     * An array builder whose result is a {@code float[]}.
     */
    static final class FloatArrayBuilder 
            extends ArrayBuilder<float[]> {

        FloatArrayBuilder(int initialSize) {
            super(initialSize);
        }

        @Override
        protected float[] allocate(int size) {
            return new float[size];
        }

        @Override
        protected int size(float[] array) {
            return array.length;
        }
        
        void appendFloat(float i) {
            ensure(size+1);
            array[size] = i;
            size++;
        }
        
        void appendDouble(double d) {
            appendFloat((float)d);
        }
    }
    
    /** 
     * An array builder whose result is a {@code char[]}.
     */
    static final class CharArrayBuilder 
            extends ArrayBuilder<char[]> {

        CharArrayBuilder(int initialSize) {
            super(initialSize);
        }

        @Override
        protected char[] allocate(int size) {
            return new char[size];
        }

        @Override
        protected int size(char[] array) {
            return array.length;
        }
        
        void appendChar(char b) {
            ensure(size+1);
            array[size] = b;
            size++;
        }
        
        void appendCodepoint(int codepoint) {
            if (Character.charCount(codepoint) == 1) {
                appendChar((char)codepoint);
            } else {
                appendChar(Character.highSurrogate(codepoint));
                appendChar(Character.lowSurrogate(codepoint));
            }
        }
    }
    
    /** 
     * An array builder whose result is a {@code java.lang.String[]}.
     */
    static final class StringArrayBuilder 
            extends ArrayBuilder<java.lang.String[]> {

        StringArrayBuilder(int initialSize) {
            super(initialSize);
        }

        @Override
        protected java.lang.String[] allocate(int size) {
            return new java.lang.String[size];
        }

        @Override
        protected int size(java.lang.String[] array) {
            return array.length;
        }
        
        void appendString(java.lang.String b) {
            ensure(size+1);
            array[size] = b;
            size++;
        }
        
        void appendCeylonString(ceylon.language.String javaString) {
            appendString(javaString.value);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static boolean[] 
    toBooleanArray(ceylon.language.Iterable<? extends ceylon.language.Boolean, ?> sequence,
            boolean... initialElements) {
        if (sequence instanceof ceylon.language.List) {
            ceylon.language.List<? extends ceylon.language.Boolean> list = 
                    (ceylon.language.List<? extends ceylon.language.Boolean>) sequence;
            return toBooleanArray(list, initialElements);
        }
        BooleanArrayBuilder builder = 
                new BooleanArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        builder.appendArray(initialElements);
        Iterator<? extends ceylon.language.Boolean> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendBoolean(((ceylon.language.Boolean) o).booleanValue());
        }
        return builder.build();
    }

    public static boolean[]
    toBooleanArray(ceylon.language.List<? extends ceylon.language.Boolean> list,
            boolean... initialElements) {
        if (list == null)
            return initialElements;
        int i=initialElements.length;
        boolean[] ret = new boolean[toInt(list.getSize() + i)];
        System.arraycopy(initialElements, 0, ret, 0, i);
        Iterator<? extends ceylon.language.Boolean> iterator = list.iterator();
        Object o;
        while ((o = iterator.next()) != finished_.get_()) {
            ret[i++] = ((ceylon.language.Boolean) o).booleanValue();
        }
        return ret;
    }
    
    @SuppressWarnings("unchecked")
    public static byte[] 
    toByteArray(ceylon.language.Iterable<? extends ceylon.language.Byte, ?> sequence,
            byte... initialElements) {
        if (sequence instanceof ceylon.language.List) {
            ceylon.language.List<? extends ceylon.language.Byte> list = 
                    (ceylon.language.List<? extends ceylon.language.Byte>) sequence;
            return toByteArray(list, initialElements);
        }
        ByteArrayBuilder builder = 
                new ByteArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        int i=0;
        for(;i<initialElements.length;i++) {
            builder.appendByte(initialElements[i]);
        }
        Iterator<? extends ceylon.language.Byte> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendByte(((ceylon.language.Byte) o).byteValue());
        }
        return builder.build();
    }

    public static byte[]
    toByteArray(ceylon.language.List<? extends ceylon.language.Byte> list,
            byte... initialElements) {
        byte[] ret = new byte[(list == null ? 0 : toInt(list.getSize()) + initialElements.length)];
        int i=0;
        for (;i<initialElements.length;i++) {
            ret[i] = initialElements[i];
        }
        if (list != null) {
            Iterator<? extends ceylon.language.Byte> iterator = list.iterator();
            Object o;
            while ((o = iterator.next()) != finished_.get_()) {
                ret[i++] = ((ceylon.language.Byte) o).byteValue();
            }
        }
        return ret;
    }
    
    @SuppressWarnings("unchecked")
    public static short[] 
    toShortArray(ceylon.language.Iterable<? extends ceylon.language.Integer, ?> sequence,
            long... initialElements) {
        if(sequence instanceof ceylon.language.List) {
            ceylon.language.List<? extends ceylon.language.Integer> list = 
                    (ceylon.language.List<? extends ceylon.language.Integer>) sequence;
            return toShortArray(list, initialElements);
        }
        ShortArrayBuilder builder = 
                new ShortArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        int i=0;
        for(;i<initialElements.length;i++) {
            builder.appendLong(initialElements[i]);
        }
        Iterator<? extends ceylon.language.Integer> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendLong(((ceylon.language.Integer) o).longValue());
        }
        return builder.build();
    }

    public static short[]
    toShortArray(ceylon.language.List<? extends ceylon.language.Integer> list,
            long... initialElements) {
        short[] ret = new short[(list == null ? 0 : toInt(list.getSize()) + initialElements.length)];
        int i=0;
        for (;i<initialElements.length;i++) {
            ret[i] = toShort(initialElements[i]);
        }
        if (list != null) {
            Iterator<? extends ceylon.language.Integer> iterator = list.iterator();
            Object o;
            while ((o = iterator.next()) != finished_.get_()) {
                ret[i++] = toShort(((ceylon.language.Integer) o).longValue());
            }
        }
        return ret;
    }
    
    @SuppressWarnings("unchecked")
    public static int[] 
    toIntArray(ceylon.language.Iterable<? extends ceylon.language.Integer, ?> sequence,
            long... initialElements) {
        if(sequence instanceof ceylon.language.List) {
            ceylon.language.List<? extends ceylon.language.Integer> list = 
                    (ceylon.language.List<? extends ceylon.language.Integer>) sequence;
            return toIntArray(list,
                    initialElements);
        }
        IntArrayBuilder builder = 
                new IntArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        int i=0;
        for(;i<initialElements.length;i++) {
            builder.appendLong(initialElements[i]);
        }
        Iterator<? extends ceylon.language.Integer> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendLong(((ceylon.language.Integer) o).longValue());
        }
        return builder.build();
    }

    public static int[]
    toIntArray(ceylon.language.List<? extends ceylon.language.Integer> list,
            long... initialElements) {
        int[] ret = new int[(list == null ? 0 : toInt(list.getSize()) + initialElements.length)];
        int i=0;
        for (;i<initialElements.length;i++) {
            ret[i] = toInt(initialElements[i]);
        }
        if (list != null) {
            Iterator<? extends ceylon.language.Integer> iterator = list.iterator();
            Object o;
            while ((o = iterator.next()) != finished_.get_()) {
                ret[i++] = toInt(((ceylon.language.Integer) o).longValue());
            }
        }
        return ret;
    }
    
    @SuppressWarnings("unchecked")
    public static long[] 
    toLongArray(ceylon.language.Iterable<? extends ceylon.language.Integer, ?> sequence,
            long... initialElements) {
        if(sequence instanceof ceylon.language.List) {
            ceylon.language.List<? extends ceylon.language.Integer> list = 
                    (ceylon.language.List<? extends ceylon.language.Integer>) sequence;
            return toLongArray(list, initialElements);
        }
        LongArrayBuilder builder = 
                new LongArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        builder.appendArray(initialElements);
        Iterator<? extends ceylon.language.Integer> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendLong(((ceylon.language.Integer) o).longValue());
        }
        return builder.build();
    }

    public static long[]
    toLongArray(ceylon.language.List<? extends ceylon.language.Integer> list,
            long... initialElements) {
        if (list == null)
            return initialElements;
        int i=initialElements.length;
        long[] ret = new long[toInt(list.getSize() + i)];
        System.arraycopy(initialElements, 0, ret, 0, i);
        Iterator<? extends ceylon.language.Integer> iterator = list.iterator();
        Object o;
        while ((o = iterator.next()) != finished_.get_()) {
            ret[i++] = ((ceylon.language.Integer) o).longValue();
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public static float[] 
    toFloatArray(ceylon.language.Iterable<? extends ceylon.language.Float, ?> sequence, 
            double... initialElements) {
        if(sequence instanceof ceylon.language.List) {
            ceylon.language.List<? extends ceylon.language.Float> list = 
                    (ceylon.language.List<? extends ceylon.language.Float>) sequence;
            return toFloatArray(list, initialElements);
        }
        FloatArrayBuilder builder = 
                new FloatArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        int i=0;
        for (;i<initialElements.length;i++) {
            builder.appendDouble(initialElements[i]);
        }
        Iterator<? extends ceylon.language.Float> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendDouble(((ceylon.language.Float)o).doubleValue());
        }
        return builder.build();
    }

    public static float[]
    toFloatArray(ceylon.language.List<? extends ceylon.language.Float> list,
            double... initialElements) {
        float[] ret = new float[(list == null ? 0 : toInt(list.getSize()) + initialElements.length)];
        int i=0;
        for (;i<initialElements.length;i++) {
            ret[i] = (float) initialElements[i];
        }
        if (list != null) {
            Iterator<? extends ceylon.language.Float> iterator = list.iterator();
            Object o;
            while ((o = iterator.next()) != finished_.get_()) {
                ret[i++] = (float)((ceylon.language.Float) o).doubleValue();
            }
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public static double[] 
    toDoubleArray(ceylon.language.Iterable<? extends ceylon.language.Float, ?> sequence,
            double... initialElements) {
        if (sequence instanceof ceylon.language.List) {
            ceylon.language.List<? extends ceylon.language.Float> list = 
                    (ceylon.language.List<? extends ceylon.language.Float>) sequence;
            return toDoubleArray(list, initialElements);
        }
        DoubleArrayBuilder builder = 
                new DoubleArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        builder.appendArray(initialElements);
        Iterator<? extends ceylon.language.Float> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendDouble(((ceylon.language.Float) o).doubleValue());
        }
        return builder.build();
    }

    public static double[]
    toDoubleArray(ceylon.language.List<? extends ceylon.language.Float> list,
            double... initialElements) {
        if (list == null)
            return initialElements;
        int i=initialElements.length;
        double[] ret = new double[toInt(list.getSize() + i)];
        System.arraycopy(initialElements, 0, ret, 0, i);
        Iterator<? extends ceylon.language.Float> iterator = list.iterator();
        Object o;
        while ((o = iterator.next()) != finished_.get_()) {
            ret[i++] = ((ceylon.language.Float) o).doubleValue();
        }
        return ret;
    }

    public static char[] 
	toCharArray(ceylon.language.Iterable<? extends ceylon.language.Character, ?> sequence,
	        int... initialElements) {
        // Note the List optimization doesn't work because 
        // the number of codepoints in the sequence
        // doesn't equal the size of the result array.
        CharArrayBuilder builder = 
                new CharArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        int i=0;
        for(;i<initialElements.length;i++) {
            builder.appendCodepoint(initialElements[i]);
            
        }
        Iterator<? extends ceylon.language.Character> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendCodepoint(((ceylon.language.Character) o).codePoint);
        }
        return builder.build();
    }

    public static char[] 
	toCharArray(ceylon.language.List<? extends ceylon.language.Character> sequence,
	        int... initialElements) {
        return toCharArray((ceylon.language.Iterable<? extends ceylon.language.Character, ?>)sequence, initialElements);
    }

    @SuppressWarnings("unchecked")
    public static int[] 
	toCodepointArray(ceylon.language.Iterable<? extends ceylon.language.Character, ?> sequence,
	        int... initialElements) {
        if (sequence instanceof ceylon.language.List) {
            ceylon.language.List<? extends ceylon.language.Character> list = 
                    (ceylon.language.List<? extends ceylon.language.Character>)sequence;
            return toCodepointArray(list, initialElements);
        }
        IntArrayBuilder builder = 
                new IntArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        builder.appendArray(initialElements);
        Iterator<? extends ceylon.language.Character> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendInt(((ceylon.language.Character) o).codePoint);
        }
        return builder.build();
    }

    public static int[]
    toCodepointArray(ceylon.language.List<? extends ceylon.language.Character> list,
            int... initialElements) {
        if (list == null)
            return initialElements;
        int i=initialElements.length;
        int[] ret = new int[toInt(list.getSize() + i)];
        System.arraycopy(initialElements, 0, ret, 0, i);
        Iterator<? extends ceylon.language.Character> iterator = list.iterator();
        Object o;
        while ((o = iterator.next()) != finished_.get_()) {
            ret[i++] = ((ceylon.language.Character)o).intValue();
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public static java.lang.String[] 
    toJavaStringArray(ceylon.language.Iterable<? extends ceylon.language.String, ?> sequence,
            java.lang.String... initialElements) {
        if (sequence instanceof ceylon.language.List) {
            ceylon.language.List<? extends ceylon.language.String> list = 
                    (ceylon.language.List<? extends ceylon.language.String>) sequence;
            return toJavaStringArray(list, initialElements);
        }
        StringArrayBuilder builder = 
                new StringArrayBuilder(initialElements.length+INIT_ARRAY_SIZE);
        builder.appendArray(initialElements);
        Iterator<? extends ceylon.language.String> iterator = sequence.iterator();
        Object o;
        while (!((o = iterator.next()) instanceof Finished)) {
            builder.appendString(((ceylon.language.String)o).value);
        }
        return builder.build();
    }

    public static java.lang.String[]
    toJavaStringArray(ceylon.language.List<? extends ceylon.language.String> list,
            java.lang.String... initialElements) {
        if (list == null)
            return initialElements;
        int i=initialElements.length;
        java.lang.String[] ret = new java.lang.String[toInt(list.getSize() + i)];
        System.arraycopy(initialElements, 0, ret, 0, i);
        Iterator<? extends ceylon.language.String> iterator = list.iterator();
        Object o;
        while ((o = iterator.next()) != finished_.get_()) {
            ret[i++] = ((ceylon.language.String) o).toString();
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(ceylon.language.List<? extends T> sequence,
            T[] ret, T... initialElements) {
        if (sequence == null)
            return initialElements;
        int i;
        if (initialElements == null) {
            i = 1;
            ret[0] = null;
        } else {
            i = initialElements.length;
            System.arraycopy(initialElements, 0, ret, 0, i);
        }
        Iterator<? extends T> iterator = sequence.iterator();
        Object o;
        while ((o = iterator.next()) != finished_.get_()) {
            ret[i++] = (T)o;
        }
        return ret;
    }

    
    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(ceylon.language.Iterable<? extends T, ?> iterable,
            java.lang.Class<T> klass, T... initialElements) {
        if (iterable == null) {
            return initialElements;
        }
        T[] ret;
        int size = fastIterableSize(iterable);
        if (size != -1) {
            ret = (T[]) java.lang.reflect.Array.newInstance(klass, 
                    size + initialElements.length);
            if (initialElements.length != 0) {
                // fast copy of list
                System.arraycopy(initialElements, 0, ret, 0, 
                        initialElements.length);
            }    
            fillArray(ret, initialElements.length, iterable);
        } else {
            ReflectingObjectArrayBuilder<T> builder = 
                    new ReflectingObjectArrayBuilder<T>
                        (initialElements.length+INIT_ARRAY_SIZE, klass);
            builder.appendArray(initialElements);
            Iterator<? extends T> iterator = iterable.iterator();
            Object o;
            while (!((o = iterator.next()) instanceof Finished)) {
                builder.appendRef((T) o);
            }
            ret = builder.build();
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(ceylon.language.List<? extends T> list,
            java.lang.Class<T> klass, T... initialElements) {
        if (list == null)
            return initialElements;
        int i=initialElements.length;
        T[] ret = (T[]) java.lang.reflect.Array.newInstance(klass,
                toInt(list.getSize() + i));
        System.arraycopy(initialElements, 0, ret, 0, i);
        Iterator<? extends T> iterator = list.iterator();
        Object o;
        while ((o = iterator.next()) != finished_.get_()) {
            ret[i++] = (T)o;
        }
        return ret;
    }
    
    public static <T> T checkNull(T t) {
        if(t == null)
            throw new AssertionError("null value returned from native call not assignable to Object");
        return t;
    }
    
    private static String[] args = new String[0];
    
    public static void storeArgs(String[] args) {
        Util.args = args;
    }
    
    public static String[] getArgs() {
        return args;
    }
    
    /** 
     * Generates a default message for when a Throwable lacks a non-null 
     * message of its own. This can only happen for non-Ceylon throwable types, 
     * but due to type erasure it's not possible to know at a catch site which 
     * whether you have a Ceylon throwable
     */
    public static String throwableMessage(java.lang.Throwable t) {
        String message = t.getMessage();
        if (message == null) {
            java.lang.Throwable c = t.getCause();
            if (c != null) {
                message = c.getMessage();
            } else {
                message = "";
            }
        }
        return message;
    }
    
    /**
     * Return a {@link Sequential} wrapping the given elements
     * (subsequent changes to the array will be visible in 
     * the returned {@link Sequential}).
     * @param $reifiedT The reified type parameter
     * @param elements The elements
     * @return A Sequential
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static <T> Sequential<T> 
    sequentialWrapper(TypeDescriptor $reifiedT, T[] elements) {
        if (elements.length == 0) {
            return (Sequential) empty_.get_();
        }
        return new Tuple<T,T,Sequential<? extends T>>($reifiedT, elements);
    }

    /**
     * Return a {@link Sequential} for the given elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).
     * @param $reifiedT The reified type parameter
     * @param elements The elements
     * @return A Sequential
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static <T> Sequential<T> 
    sequentialWrapperCopy(TypeDescriptor $reifiedT, T[] elements) {
        if (elements.length == 0) {
            return (Sequential) empty_.get_();
        }
        return new Tuple<T,T,Sequential<? extends T>>($reifiedT, elements,
                (Sequential<? extends T>) empty_.get_(), true);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;String&gt;} copying the 
     * given {@code java.lang.String[]} elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>Used to obtain a {@code Sequential<String>} from a {@code java.lang.String[]}.</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.String> 
    sequentialWrapperBoxed(java.lang.String[] elements) {
        if (elements.length == 0) {
            return (Sequential) empty_.get_();

        }
        int total = elements.length;
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        for(java.lang.String element : elements) {
            newArray[i++] = ceylon.language.String.instance(element);
        }
        return new Tuple(ceylon.language.String.$TypeDescriptor$, newArray);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;Byte&gt;} copying the 
     * given {@code byte[]} elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>Used to obtain a {@code Sequential<Byte>} from a {@code byte[]}.</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.Integer> 
    sequentialWrapperBoxed(byte[] elements) {
        if (elements.length == 0) {
            return (Sequential)empty_.get_();

        }
        int total = elements.length;
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        for(byte element : elements) {
            newArray[i++] = ceylon.language.Byte.instance(element);
        }
        return new Tuple(ceylon.language.Byte.$TypeDescriptor$, newArray);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;Integer&gt;} copying the 
     * given {@code short[]} elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>Used to obtain a {@code Sequential<Integer>} from a {@code short[]}.</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.Integer> 
    sequentialWrapperBoxed(short[] elements) {
        if (elements.length == 0) {
            return (Sequential)empty_.get_();

        }
        int total = elements.length;
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        for(short element : elements) {
            newArray[i++] = ceylon.language.Integer.instance(element);
        }
        return new Tuple(ceylon.language.Integer.$TypeDescriptor$, newArray);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;Integer&gt;} copying the 
     * given {@code int[]} elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>WARNING: if you use this method for anything else than interop you're likely doind it wrong
     * and want #sequentialWrapperBoxed instead</p>
     * 
     * <p>Used to obtain a {@code Sequential<Integer>} from a {@code int[]}.</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.Integer> 
    sequentialWrapperBoxedForInteger(int[] elements) {
        if (elements.length == 0) {
            return (Sequential)empty_.get_();

        }
        int total = elements.length;
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        for(int element : elements) {
            newArray[i++] = ceylon.language.Integer.instance(element);
        }
        return new Tuple(ceylon.language.Integer.$TypeDescriptor$, newArray);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;Integer&gt;} copying the 
     * given {@code long[]} elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>Used to obtain a {@code Sequential<Integer>} from a {@code long[]}.</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.Integer> 
    sequentialWrapperBoxed(long[] elements) {
        if (elements.length == 0) {
            return (Sequential)empty_.get_();

        }
        int total = elements.length;
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        for(long element : elements) {
            newArray[i++] = ceylon.language.Integer.instance(element);
        }
        return new Tuple(ceylon.language.Integer.$TypeDescriptor$, newArray);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;Character&gt;} copying the 
     * given {@code char[]} elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>Used to obtain a {@code Sequential<Character>} from a {@code char[]}.</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.Character> 
    sequentialWrapperBoxed(char[] elements) {
        if (elements.length == 0) {
            return (Sequential)empty_.get_();

        }
        int total = Character.codePointCount(elements, 0, elements.length);
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        char lastHighSurrogate = 0;
        for(char element : elements) {
            if(lastHighSurrogate != 0){
                if(Character.isLowSurrogate(element)){
                    newArray[i++] = ceylon.language.Character.instance(Character.toCodePoint(lastHighSurrogate, element));
                    lastHighSurrogate = 0;
                }else{
                    throw new AssertionError("Illegal low surrogate value "+element+" after high surrogate value "+lastHighSurrogate);
                }
            }else if(Character.isHighSurrogate(element)){
                lastHighSurrogate = element;
            }else if(Character.isLowSurrogate(element)){
                throw new AssertionError("Illegal low surrogate value "+element+" after no high surrogate value");
            }else{
                newArray[i++] = ceylon.language.Character.instance(element);
            }
        }
        if(lastHighSurrogate != 0)
            throw new AssertionError("Missing low surrogate value after high surrogate value "+lastHighSurrogate);
        return new Tuple(ceylon.language.Character.$TypeDescriptor$, newArray);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;Character&gt;} copying the 
     * given {@code int[]} elements
     * (subsequent changes to the array will be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>WARNING: if you use this method for interop you're likely doind it wrong
     * and want #sequentialWrapperBoxedForInteger instead</p>
     * 
     * <p>Used to obtain a {@code Sequential<Character>} from a {@code int[]} 
     * obtained from an annotation.</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.Character> 
    sequentialWrapperBoxed(int[] elements) {
        if (elements.length == 0) {
            return (Sequential)empty_.get_();

        }
        int total = elements.length;
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        for(int element : elements) {
            newArray[i++] = ceylon.language.Character.instance(element);
        }
        return new Tuple(ceylon.language.Character.$TypeDescriptor$, newArray);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;Boolean&gt;} copying the 
     * given {@code boolean[]} elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>Used to obtain a {@code Sequential<Boolean>} from a {@code boolean[]}.</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.Boolean> 
    sequentialWrapperBoxed(boolean[] elements) {
        if (elements.length == 0) {
            return (Sequential)empty_.get_();

        }
        int total = elements.length;
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        for(boolean element : elements) {
            newArray[i++] = ceylon.language.Boolean.instance(element);
        }
        return new Tuple(ceylon.language.Boolean.$TypeDescriptor$, newArray);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;Float&gt;} copying the 
     * given {@code float[]} elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>Used to obtain a {@code Sequential<String>} from a {@code float[]} .</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.Float> 
    sequentialWrapperBoxed(float[] elements) {
        if (elements.length == 0) {
            return (Sequential)empty_.get_();

        }
        int total = elements.length;
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        for(float element : elements) {
            newArray[i++] = ceylon.language.Float.instance(element);
        }
        return new Tuple(ceylon.language.Float.$TypeDescriptor$, newArray);
    }

    /** 
     * <p>Return a {@link Sequential Sequential&lt;Float&gt;} copying the 
     * given {@code double[]} elements
     * (subsequent changes to the array will not be visible in 
     * the returned {@link Sequential}).</p>
     * 
     * <p>Used to obtain a {@code Sequential<String>} from a {@code double[]} .</p>
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Sequential<? extends ceylon.language.Float> 
    sequentialWrapperBoxed(double[] elements) {
        if (elements.length == 0) {
            return (Sequential)empty_.get_();

        }
        int total = elements.length;
        java.lang.Object[] newArray = new java.lang.Object[total];
        int i = 0;
        for(double element : elements) {
            newArray[i++] = ceylon.language.Float.instance(element);
        }
        return new Tuple(ceylon.language.Float.$TypeDescriptor$, newArray);
    }


    /**
     * Return {@link empty_#getEmpty$ empty} or an {@link ArraySequence}
     * wrapping the given elements, depending on whether the given array 
     * and varargs are empty.
     * 
     * @deprecated because it's too easy to end up evaluating the arguments 
     * in the wrong order. Use {@link #sequentialCopy(TypeDescriptor, Object[], Sequential)}
     * 
     * @param rest The elements at the end of the sequence
     * @param elements the elements at the start of the sequence
     * @return A Sequential
     */
    @Deprecated
    public static <T> Sequential<? extends T> 
    sequentialCopy(TypeDescriptor $reifiedT, Sequential<? extends T> rest, 
            Object... elements) {
        return sequentialCopy($reifiedT, 0, 
                elements.length, elements, rest);
    }
    
    /**
     * Return {@link empty_#getEmpty$ empty} or an {@link ArraySequence}
     * wrapping the given elements, depending on whether the given array 
     * and varargs are empty.
     * 
     * @param rest The elements at the end of the sequence
     * @param elements the elements at the start of the sequence
     * @return A Sequential
     */
    public static <T> Sequential<? extends T> 
    sequentialCopy(TypeDescriptor $reifiedT, Object[] initialElements,
            Sequential<? extends T> rest) {
        return sequentialCopy($reifiedT, 0, 
                initialElements.length, initialElements, rest);
    }
        
    /**
     * Returns a Sequential made by concatenating the {@code length} elements 
     * of {@code elements} starting from {@code state} with the elements of
     * {@code rest}: <code> {*elements[start:length], *rest}</code>. 
     * 
     * <strong>This method does not copy {@code elements} unless it has to</strong>
     */
    public static <T> Sequential<? extends T> sequentialCopy(
            TypeDescriptor $reifiedT,  
            int start, int length, Object[] elements, 
            Sequential<? extends T> rest) {
        return sequentialCopy($reifiedT, start, length, elements, rest, false);
    }
    
    /**
     * Returns a Sequential made by concatenating the {@code length} elements 
     * of {@code elements} starting from {@code state} with the elements of
     * {@code rest}: <code> {*elements[start:length], *rest}</code>. 
     * 
     * <strong>This method does not copy {@code elements} unless it has to
     * or unless forceCopy is true</strong>
     */
    @SuppressWarnings("unchecked")
    public static <T> Sequential<? extends T> sequentialCopy(
            TypeDescriptor $reifiedT,  
            int start, int length, Object[] elements, 
            Sequential<? extends T> rest,
            boolean forceCopy) {
        if (length == 0) {
            if(rest.getEmpty()) {
                return (Sequential<T>)empty_.get_();
            }
            // otherwise we MUST copy it
            if(!forceCopy)
                return rest;
        }
        // elements is not empty
        if(rest.getEmpty()) {
            return new ObjectArrayIterable<T>($reifiedT, elements)
                    .skip(start).take(length).sequence();
        }
        // we have both (or must copy), let's find the total size
        int total = toInt(rest.getSize() + length);
        java.lang.Object[] newArray = new java.lang.Object[total];
        System.arraycopy(elements, start, newArray, 0, length);
        Iterator<? extends T> iterator = rest.iterator();
        int i = length;
        for(Object elem; (elem = iterator.next()) != finished_.get_(); i++) {
            newArray[i] = elem;
        }
        return new ObjectArrayIterable<T>($reifiedT, (T[])newArray).sequence();
    }

    /** 
     * <p>Equivalent to the Ceylon {@code sequential(iterable) else []}, this
     * converts an {@code Iterable} to a {@code Sequential} without calling 
     * {@code Iterable.sequence()}.</p>
     * 
     * <p>This is used for spread arguments in 
     * tuple literals: {@code [*foo]}</p>
     * a 
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Sequential sequentialOf(TypeDescriptor reified$Element, 
            final Iterable iterable) {
        Object result = sequence_.sequence(reified$Element,
                Null.$TypeDescriptor$,
                iterable);
        if (result == null) {
            return (Sequential)empty_.get_();
        }
        return (Sequential)result;
    }
    
    
    /**
     * Returns a runtime exception. To be used by implementors 
     * of mixin methods used to access super-interfaces $impl fields
     * for final classes that don't and will never need them
     */
    public static RuntimeException makeUnimplementedMixinAccessException() {
        return new RuntimeException("Internal error: should never be called");
    }

    /**
     * Specialised version of Tuple.spanFrom for when the
     * typechecker determines that it can do better than the 
     * generic one that returns a Sequential. Here we return 
     * a Tuple, although our type signature hides this.
     */
    public static Sequential<?> tuple_spanFrom(Ranged<?,?,?> tuple, 
    		ceylon.language.Integer index) {
        Sequential<?> seq = (Sequential<?>)tuple;
        long i = index.longValue();
        while(i-- > 0) {
            seq = seq.getRest();
        }
        return seq;
    }
    
    /** Called to initialize an {@code BooleanArray} when one is instantiated */
    public static boolean[] fillArray(boolean[] array, boolean val) {
        Arrays.fill(array, val);
        return array;
    }
    
    /** Called to initialize a {@code ByteArray} when one is instantiated */
    public static byte[] fillArray(byte[] array, byte val) {
        Arrays.fill(array, val);
        return array;
    }
    
    /** Called to initialize an {@code ShortArray} when one is instantiated */
    public static short[] fillArray(short[] array, short val) {
        Arrays.fill(array, val);
        return array;
    }
    
    /** Called to initialize an {@code IntArray} when one is instantiated */
    public static int[] fillArray(int[] array, int val) {
        Arrays.fill(array, val);
        return array;
    }
    
    /** Called to initialize a {@code LongArray} when one is instantiated */
    public static long[] fillArray(long[] array, long val) {
        Arrays.fill(array, val);
        return array;
    }
    
    /** Called to initialize a {@code FloatArray} when one is instantiated */
    public static float[] fillArray(float[] array, float val) {
        Arrays.fill(array, val);
        return array;
    }
    
    /** Called to initialize a {@code DoubleArray} when one is instantiated */
    public static double[] fillArray(double[] array, double val) {
        Arrays.fill(array, val);
        return array;
    }
    
    /** Called to initialize a {@code CharArray} when one is instantiated */
    public static char[] fillArray(char[] array, char val) {
        Arrays.fill(array, val);
        return array;
    }
    
    /** Called to initialize an {@code ObjectArray<T>} when one it instantiated */
    public static <T> T[] fillArray(T[] array, T val) {
        Arrays.fill(array, val);
        return array;
    }
    
    private static void checkArrayElementType(TypeDescriptor $reifiedElement) {
        if ($reifiedElement instanceof TypeDescriptor.Composite) {
            throw new AssertionError("cannot create Java array with union or intersection element type "
                    + $reifiedElement.toString());
        }
        else if ($reifiedElement instanceof TypeDescriptor.Nothing) {
            throw new AssertionError("cannot create Java array with bottom element type Nothing");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] makeArray(TypeDescriptor $reifiedElement, int size) {
        checkArrayElementType($reifiedElement);
        return (T[]) java.lang.reflect.Array.newInstance($reifiedElement.getArrayElementClass(), size);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] makeArray(TypeDescriptor $reifiedElement, int... dimensions) {
        checkArrayElementType($reifiedElement);
        return (T[]) java.lang.reflect.Array.newInstance($reifiedElement.getArrayElementClass(), dimensions);
    }

    /**
     * Returns a runtime exception. To be used by implementors 
     * of Java array methods used to make sure they are never 
     * called
     */
    public static RuntimeException makeJavaArrayWrapperException() {
        return new RuntimeException("Internal error: should never be called");
    }

    /**
     * Throws an exception without having to declare it. This 
     * uses a Ceylon helper that does this because Ceylon does 
     * not have checked exceptions. This is merely to avoid a 
     * javac check wrt. checked exceptions.
     * Stef tried using Unsafe.throwException() but 
     * Unsafe.getUnsafe() throws if we have a ClassLoader, and 
     * the only other way is using reflection to get to it, 
     * which starts to smell real bad when we can just use a 
     * Ceylon helper.
     */
    public static void rethrow(final Throwable t) {
        ceylon.language.impl.rethrow_.rethrow(t);
    }

    /**
     * Null-safe equals.
     */
    public static boolean eq(Object a, Object b) {
        if(a == null)
            return b == null;
        if(b == null)
            return false;
        return a.equals(b);
    }

    /**
     * Applies the given function to the given arguments. The 
     * argument types are assumed to be correct and will not 
     * be checked. This method will properly deal with variadic 
     * functions. The arguments are expected to be spread in the 
     * given sequential, even in the case of variadic functions, 
     * which means that there will be no spreading of any 
     * Sequential instance in the given arguments. On the 
     * contrary, a portion of the given arguments may be packaged 
     * into a Sequential if the given function is variadic.
     * 
     * This version trusts the type of the Sequential used for any variadic parameter
     * and will not copy it.
     * 
     * @param function the function to apply
     * @param arguments the argument values to pass to the function
     * @return the function's return value
     */
    public static <Return> Return apply(Callable<? extends Return> function, 
                                        Sequential<? extends Object> arguments) {
        return apply(function, arguments, null);
    }

    /**
     * Applies the given function to the given arguments. The 
     * argument types are assumed to be correct and will not 
     * be checked. This method will properly deal with variadic 
     * functions. The arguments are expected to be spread in the 
     * given sequential, even in the case of variadic functions, 
     * which means that there will be no spreading of any 
     * Sequential instance in the given arguments. On the 
     * contrary, a portion of the given arguments may be packaged 
     * into a Sequential if the given function is variadic.
     * 
     * Any variadic parameter will be copied into a new sequence
     * if variadicElementType is specified. Use this if you don't
     * trust the arguments type (or its span types).
     * 
     * @param function the function to apply
     * @param arguments the argument values to pass to the function
     * @param variadicElementType if non-null, the type of variadic sequential elements
     *        to pass to the receiver, copied from the given arguments list. Use this if
     *        you do not trust the arguments sequence to be of the right type
     * @return the function's return value
     */
    public static <Return> Return apply(Callable<? extends Return> function, 
    		                            Sequential<? extends Object> arguments,
    		                            TypeDescriptor variadicElementType) {
        int variadicParameterIndex = function.$getVariadicParameterIndex$();
        switch (toInt(arguments.getSize())) {
        case 0:
            // even if the function is variadic it will overload $call so we're good
            return function.$call$();
        case 1:
            // if the first param is variadic, just pass the sequence along
            if(variadicParameterIndex == 0)
                return function.$callvariadic$(safeSpanFrom(arguments, 0, variadicElementType));
            return function.$call$(arguments.getFromFirst(0));
        case 2:
            switch(variadicParameterIndex) {
            // pass the sequence along
            case 0: return function.$callvariadic$(safeSpanFrom(arguments, 0, variadicElementType));
            // extract the first, pass the rest
            case 1: return function.$callvariadic$(arguments.getFromFirst(0), 
                                                   safeSpanFrom(arguments, 1, variadicElementType));
            // no variadic param, or after we run out of elements to pass
            default:
                return function.$call$(arguments.getFromFirst(0), 
                                       arguments.getFromFirst(1));
            }
        case 3:
            switch(variadicParameterIndex) {
            // pass the sequence along
            case 0: return function.$callvariadic$(safeSpanFrom(arguments, 0, variadicElementType));
            // extract the first, pass the rest
            case 1: return function.$callvariadic$(arguments.getFromFirst(0), 
                                                   safeSpanFrom(arguments, 1, variadicElementType));
            // extract the first and second, pass the rest
            case 2: return function.$callvariadic$(arguments.getFromFirst(0),
                                                   arguments.getFromFirst(1),
                                                   safeSpanFrom(arguments, 2, variadicElementType));
            // no variadic param, or after we run out of elements to pass
            default:
            return function.$call$(arguments.getFromFirst(0), 
                                   arguments.getFromFirst(1), 
                                   arguments.getFromFirst(2));
            }
        default:
            switch(variadicParameterIndex) {
            // pass the sequence along
            case 0: return function.$callvariadic$(safeSpanFrom(arguments, 0, variadicElementType));
            // extract the first, pass the rest
            case 1: return function.$callvariadic$(arguments.getFromFirst(0), 
                                                   safeSpanFrom(arguments, 1, variadicElementType));
            // extract the first and second, pass the rest
            case 2: return function.$callvariadic$(arguments.getFromFirst(0),
                                                   arguments.getFromFirst(1),
                                                   safeSpanFrom(arguments, 2, variadicElementType));
            case 3: return function.$callvariadic$(arguments.getFromFirst(0),
                                                   arguments.getFromFirst(1),
                                                   arguments.getFromFirst(2),
                                                   safeSpanFrom(arguments, 3, variadicElementType));
            // no variadic param
            case -1:
                java.lang.Object[] args = Util.toArray(arguments, 
                		new java.lang.Object[toInt(arguments.getSize())]);
                return function.$call$(args);
            // we have a variadic param in there bothering us
            default:
                // we stuff everything before the variadic into an array
                int beforeVariadic = Math.min(toInt(arguments.getSize()), 
                		variadicParameterIndex);
                boolean needsVariadic = beforeVariadic < arguments.getSize();
                args = new java.lang.Object[beforeVariadic + (needsVariadic ? 1 : 0)];
                Iterator<?> iterator = arguments.iterator();
                java.lang.Object it;
                int i=0;
                while(i < beforeVariadic && 
                		(it = iterator.next()) != finished_.get_()) {
                    args[i++] = it;
                }
                // add the remainder as a variadic arg if required
                if(needsVariadic) {
                    args[i] = safeSpanFrom(arguments, beforeVariadic, variadicElementType);
                    return function.$callvariadic$(args);
                }
                return function.$call$(args);
            }
        }
    }

    /**
     * Makes sure the sub-sequence at arguments[start...] is copied in a sequence of the given type if it's given
     * @param arguments total list of arguments (flat)
     * @param start index of the start of the arguments we want to slice
     * @param variadicElementType element type of the varargs sequence we need, or null if we don't care and trust it
     * @return a new sequence or the original sequence's span
     */
    private static Sequential<?> safeSpanFrom(Sequential<? extends Object> arguments, int start, 
            TypeDescriptor variadicElementType) {
        if(variadicElementType == null){
            if(start == 0)
                return arguments;
            return arguments.spanFrom(Integer.instance(start));
        }
        // we don't trust it, we need to copy it
        Sequential<?> ret;
        if(start == 0)
            ret = arguments;
        else
            ret = arguments.spanFrom(Integer.instance(start));
        return sequentialCopy(variadicElementType, 0, 0, NO_ELEMENTS, ret, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> java.lang.Class<T> 
    getJavaClassForDescriptor(TypeDescriptor descriptor) {
        if(descriptor == TypeDescriptor.NothingType
           || descriptor == ceylon.language.Object.$TypeDescriptor$
           || descriptor == ceylon.language.Anything.$TypeDescriptor$
           || descriptor == ceylon.language.Basic.$TypeDescriptor$
           || descriptor == ceylon.language.Null.$TypeDescriptor$
           || descriptor == ceylon.language.Identifiable.$TypeDescriptor$)
            return (java.lang.Class<T>) Object.class;
        if(descriptor instanceof TypeDescriptor.Class)
            return (java.lang.Class<T>) ((TypeDescriptor.Class) descriptor).getKlass();
        if(descriptor instanceof TypeDescriptor.Member)
            return getJavaClassForDescriptor(((TypeDescriptor.Member) descriptor).getMember());
        if(descriptor instanceof TypeDescriptor.Intersection)
            return (java.lang.Class<T>) Object.class;
        if(descriptor instanceof TypeDescriptor.Union) {
            TypeDescriptor.Union union = (TypeDescriptor.Union) descriptor;
            TypeDescriptor[] members = union.getMembers();
            // special case for optional types
            if(members.length == 2) {
                if(members[0] == ceylon.language.Null.$TypeDescriptor$)
                    return getJavaClassForDescriptor(members[1]);
                if(members[1] == ceylon.language.Null.$TypeDescriptor$)
                    return getJavaClassForDescriptor(members[0]);
            }
            return (java.lang.Class<T>) Object.class;
        }
        return (java.lang.Class<T>) Object.class;
    }
    
    public static int arrayLength(Object array) {
        //TODO: wouldn't it be faster to just use java.lang.reflect.Array.getLength() ?
        if (array instanceof Object[]) return ((Object[])array).length;
        else if (array instanceof long[]) return ((long[])array).length;
        else if (array instanceof double[]) return ((double[])array).length;
        else if (array instanceof byte[]) return ((byte[])array).length;
        else if (array instanceof int[]) return ((int[])array).length;
        else if (array instanceof boolean[]) return ((boolean[])array).length;
        else if (array instanceof short[]) return ((short[])array).length;
        else if (array instanceof float[]) return ((float[])array).length;
        else if (array instanceof char[]) return ((char[])array).length;
        throw new ClassCastException(notArrayType(array));
    }
    
    /**
     * Used by the JVM backend to get unboxed items from an Array&lt;Integer> backing array
     */
    public static long getIntegerArray(Object array, int index) {
        if (array instanceof long[]) return ((long[])array)[index];
        if (array instanceof int[]) return ((int[])array)[index];
        if (array instanceof short[]) return ((short[])array)[index];
        throw new ClassCastException(notArrayType(array));
    }
    
    /**
     * Used by the JVM backend to get unboxed items from an Array&lt;Float> backing array
     */
    public static double getFloatArray(Object array, int index) {
        if (array instanceof double[]) return ((double[])array)[index];
        if (array instanceof float[]) return ((float[])array)[index];
        throw new ClassCastException(notArrayType(array));
    }
    
    /**
     * Used by the JVM backend to get unboxed items from an Array&lt;Character> backing array
     */
    public static int getCharacterArray(Object array, int index) {
        if (array instanceof int[]) return ((int[])array)[index];
        throw new ClassCastException(notArrayType(array));
    }
    
    /**
     * Used by the JVM backend to get unboxed items from an Array&lt;Byte> backing array
     */
    public static byte getByteArray(Object array, int index) {
        if (array instanceof byte[]) return ((byte[])array)[index];
        throw new ClassCastException(notArrayType(array));
    }
    
    /**
     * Used by the JVM backend to get unboxed items from an Array&lt;Boolean> backing array
     */
    public static boolean getBooleanArray(Object array, int index) {
        if (array instanceof boolean[]) return ((boolean[])array)[index];
        throw new ClassCastException(notArrayType(array));
    }
    
    /**
     * Used by the JVM backend to get unboxed items from an Array&lt;Integer> backing array
     */
    public static java.lang.String getStringArray(Object array, int index) {
        if (array instanceof java.lang.String[])
            return ((java.lang.String[])array)[index];
        throw new ClassCastException(notArrayType(array));
    }
    
    /**
     * Used by the JVM backend to get items from an ArraySequence object. Beware: do not use that
     * for Array&lt;Object> as there's too much magic in there.
     */
    public static Object getObjectArray(Object array, int index) {
        if (array instanceof Object[])
            return ((Object[])array)[index];
        else if (array instanceof boolean[])
            return ceylon.language.Boolean.instance(((boolean[])array)[index]);
        else if (array instanceof float[])
            return ceylon.language.Float.instance(((float[])array)[index]);
        else if (array instanceof double[])
            return ceylon.language.Float.instance(((double[])array)[index]);
        else if (array instanceof char[])
            return ceylon.language.Character.instance(((char[])array)[index]);
        else if (array instanceof byte[])
            return ceylon.language.Byte.instance(((byte[])array)[index]);
        else if (array instanceof short[])
            return ceylon.language.Integer.instance(((short[])array)[index]);
        else if (array instanceof int[])
            return ceylon.language.Integer.instance(((int[])array)[index]);
        else if (array instanceof long[])
            return ceylon.language.Integer.instance(((long[])array)[index]);
        throw new ClassCastException(notArrayType(array));
    }
    
    private static String notArrayType(Object obj) {
        return (obj == null ? "null" : obj.getClass().getName()) + " is not an array type";
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Sequential<? extends java.lang.Throwable> suppressedExceptions(
            @Name("exception")
            @TypeInfo("ceylon.language::Exception")
            final java.lang.Throwable exception) {
        java.lang.Throwable[] sup = exception.getSuppressed();
        if (sup.length > 0) {
            return new ObjectArrayIterable<java.lang.Throwable>
                (TypeDescriptor.klass(java.lang.Throwable.class), sup)
                .sequence();
        } else {
            return (Sequential) empty_.get_();
        }
    }
    
    public static <T> Sequence<T> asSequence(Sequential<T> sequential) {
        if (sequential instanceof Sequence) {
            return (Sequence<T>) sequential;
        } else {
            throw new AssertionError("Assertion failed: Sequence expected");
        }
    }

    /** 
     * <p>Typecast a {@code long} to an {@code int}, or throw if the {@code long} 
     * cannot be safely converted.</p>
     *   
     * <p>We need to do this:</p>
     *  <ul>
     *  <li>when creating or indexing into an array,</li>
     *  <li>when invoking a Java method which takes an {@code int},</li>
     *  <li>when assigning to a Java {@code int} field.</li>
     *  <ul>
     *  @throws ceylon.language.OverflowException
     */
    public static int toInt(long value) {
        int result = (int) value;
        if (result != value) {
            throw new OverflowException(value + " cannot be safely converted into a 32-bit integer");
        }
        return result;
    }
    
    /** 
     * <p>Typecast a {@code long} to a {@code short}, or throw if the {@code long}
     * cannot be safely converted.</p>
     *   
     * <p>We need to do this:</p>
     *  <ul>
     *  <li>when invoking a Java method which takes a {@code short},</li>
     *  <li>when assigning to a Java {@code short} field.</li>
     *  <ul>
     *  @throws ceylon.language.OverflowException
     */
    public static short toShort(long value) {
        short result = (short) value;
        if (result != value) {
            throw new OverflowException(value + " cannot be safely converted into a 16-bit integer");
        }
        return result;
    }
    
    /** 
     * <p>Typecast a {@code long} to a {@code byte}, or throw if the {@code long} 
     * cannot be safely converted.</p>
     *   
     * <p>We need to do this:</p>
     *  <ul>
     *  <li>when creating or indexing into an array,</li>
     *  <li>when invoking a Java method which takes a {@code byte},</li>
     *  <li>when assigning to a Java {@code byte} field.</li>
     *  <ul>
     *  @throws ceylon.language.OverflowException
     */
    public static byte toByte(long value) {
        byte result = (byte) value;
        if (result != value) {
            throw new OverflowException(value + " cannot be safely converted into a 8-bit integer");
        }
        return result;
    }

    /**
     * Used during deserialization to obtain a MethodHandle used for resetting final fields.
     * @param lookup The lookup on the class containing the field
     * @param fieldName The name of the field whose value is to be set 
     * @return The method handle
     * @throws ReflectiveOperationException
     */
    public static MethodHandle setter(MethodHandles.Lookup lookup, String fieldName) throws ReflectiveOperationException {
        // Should this be an instance method of some thing passed to the derserialize method
        Field field = lookup.lookupClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        MethodHandle handle = lookup.unreflectSetter(field);
        return handle;
    }

    /**
     * Return a {@link Tuple} wrapping the given elements
     * @param $reifiedT The reified type parameter
     * @param elements The elements
     * @return A Sequential
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static <T> Sequential<T> sequentialToTuple(TypeDescriptor $reifiedT, Sequential<T> elements) {
        if (elements.getSize() == 0) {
            return (Sequential) empty_.get_();
        }
        Object[] array= new Object[(int)elements.getSize()];
        Iterator<? extends T> iterator = elements.iterator();
        Object val;
        int i=0;
        while((val = iterator.next()) != finished_.get_()){
            array[i++] = val;
        }
        return new Tuple<T,T,Sequential<? extends T>>($reifiedT, array);
    }
    
    @NonNull
    public static <T> T assertExists(T instance) {
        if (instance == null) {
            throw new AssertionError("violated exists assertion");
        }
        return instance;
    }
    
    /** Convert a java iterable to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static <Element> ceylon.language.Iterable<Element,java.lang.Object> toIterable(
            @NonNull
            final TypeDescriptor $reified$Element,
            @NonNull
            final java.lang.Iterable<Element> jit) {
        return new AbstractIterable<Element, java.lang.Object>($reified$Element, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends Element> iterator() {
                return new AbstractIterator<Element>($reified$Element) {
                    private static final long serialVersionUID = 1L;
                    private final java.util.Iterator<Element> it = jit.iterator();

                    @Override
                    public java.lang.Object next() {
                        if (it.hasNext()) {
                            return it.next();
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    /** Convert a java array to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static <Element> ceylon.language.Iterable<Element,java.lang.Object> toIterable(
            @NonNull
            final TypeDescriptor $reified$Element,
            @NonNull
            final Element[] jit) {
        return new AbstractIterable<Element, java.lang.Object>($reified$Element, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends Element> iterator() {
                return new AbstractIterator<Element>($reified$Element) {
                    private static final long serialVersionUID = 1L;
                    private int index = 0;

                    @Override
                    public java.lang.Object next() {
                        if (index < jit.length) {
                            return jit[index++];
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    /** Convert a ceylon iterable to a ceylon iterable **/
    @NonNull
    public static <Element> ceylon.language.Iterable<Element,java.lang.Object> toIterable(
            @NonNull
            final TypeDescriptor $reified$Element,
            @NonNull
            final ceylon.language.Iterable<? extends Element,java.lang.Object> it) {
        return new AbstractIterable<Element, java.lang.Object>($reified$Element, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends Element> iterator() {
                return it.iterator();
            }
        };
    }
    
    /** Convert a java array to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static ceylon.language.Iterable<ceylon.language.Boolean,java.lang.Object> toIterable(
            @NonNull
            final boolean[] jit) {
        return new AbstractIterable<ceylon.language.Boolean, java.lang.Object>(ceylon.language.Boolean.$TypeDescriptor$, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends ceylon.language.Boolean> iterator() {
                return new AbstractIterator<ceylon.language.Boolean>(ceylon.language.Boolean.$TypeDescriptor$) {
                    private static final long serialVersionUID = 1L;
                    private int index = 0;

                    @Override
                    public java.lang.Object next() {
                        if (index < jit.length) {
                            return ceylon.language.Boolean.instance(jit[index++]);
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    /** Convert a java array to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static ceylon.language.Iterable<ceylon.language.Integer,java.lang.Object> toIterable(
            @NonNull
            final short[] jit) {
        return new AbstractIterable<ceylon.language.Integer, java.lang.Object>(ceylon.language.Integer.$TypeDescriptor$, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends ceylon.language.Integer> iterator() {
                return new AbstractIterator<ceylon.language.Integer>(ceylon.language.Integer.$TypeDescriptor$) {
                    private static final long serialVersionUID = 1L;
                    private int index = 0;

                    @Override
                    public java.lang.Object next() {
                        if (index < jit.length) {
                            return ceylon.language.Integer.instance(jit[index++]);
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    /** Convert a java array to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static ceylon.language.Iterable<ceylon.language.Integer,java.lang.Object> toIterable(
            @NonNull
            final int[] jit) {
        return new AbstractIterable<ceylon.language.Integer, java.lang.Object>(ceylon.language.Integer.$TypeDescriptor$, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends ceylon.language.Integer> iterator() {
                return new AbstractIterator<ceylon.language.Integer>(ceylon.language.Integer.$TypeDescriptor$) {
                    private static final long serialVersionUID = 1L;
                    private int index = 0;

                    @Override
                    public java.lang.Object next() {
                        if (index < jit.length) {
                            return ceylon.language.Integer.instance(jit[index++]);
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    /** Convert a java array to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static ceylon.language.Iterable<ceylon.language.Integer,java.lang.Object> toIterable(
            @NonNull
            final long[] jit) {
        return new AbstractIterable<ceylon.language.Integer, java.lang.Object>(ceylon.language.Integer.$TypeDescriptor$, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends ceylon.language.Integer> iterator() {
                return new AbstractIterator<ceylon.language.Integer>(ceylon.language.Integer.$TypeDescriptor$) {
                    private static final long serialVersionUID = 1L;
                    private int index = 0;

                    @Override
                    public java.lang.Object next() {
                        if (index < jit.length) {
                            return ceylon.language.Integer.instance(jit[index++]);
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    /** Convert a java array to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static ceylon.language.Iterable<ceylon.language.Float,java.lang.Object> toIterable(
            @NonNull
            final float[] jit) {
        return new AbstractIterable<ceylon.language.Float, java.lang.Object>(ceylon.language.Float.$TypeDescriptor$, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends ceylon.language.Float> iterator() {
                return new AbstractIterator<ceylon.language.Float>(ceylon.language.Float.$TypeDescriptor$) {
                    private static final long serialVersionUID = 1L;
                    private int index = 0;

                    @Override
                    public java.lang.Object next() {
                        if (index < jit.length) {
                            return ceylon.language.Float.instance(jit[index++]);
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    /** Convert a java array to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static ceylon.language.Iterable<ceylon.language.Float,java.lang.Object> toIterable(
            @NonNull
            final double[] jit) {
        return new AbstractIterable<ceylon.language.Float, java.lang.Object>(ceylon.language.Float.$TypeDescriptor$, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends ceylon.language.Float> iterator() {
                return new AbstractIterator<ceylon.language.Float>(ceylon.language.Float.$TypeDescriptor$) {
                    private static final long serialVersionUID = 1L;
                    private int index = 0;

                    @Override
                    public java.lang.Object next() {
                        if (index < jit.length) {
                            return ceylon.language.Float.instance(jit[index++]);
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    /** Convert a java array to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static ceylon.language.Iterable<ceylon.language.Character,java.lang.Object> toIterable(
            @NonNull
            final char[] jit) {
        return new AbstractIterable<ceylon.language.Character, java.lang.Object>(ceylon.language.Character.$TypeDescriptor$, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends ceylon.language.Character> iterator() {
                return new AbstractIterator<ceylon.language.Character>(ceylon.language.Character.$TypeDescriptor$) {
                    private static final long serialVersionUID = 1L;
                    private int index = 0;

                    @Override
                    public java.lang.Object next() {
                        if (index < jit.length) {
                            return ceylon.language.Character.instance(jit[index++]);
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    /** Convert a java array to a ceylon iterable (to support java iterables in spread arguments*/
    @NonNull
    public static ceylon.language.Iterable<ceylon.language.Byte,java.lang.Object> toIterable(
            @NonNull
            final byte[] jit) {
        return new AbstractIterable<ceylon.language.Byte, java.lang.Object>(ceylon.language.Byte.$TypeDescriptor$, Null.$TypeDescriptor$) {
            private static final long serialVersionUID = 1L;
            @Override
            public Iterator<? extends ceylon.language.Byte> iterator() {
                return new AbstractIterator<ceylon.language.Byte>(ceylon.language.Byte.$TypeDescriptor$) {
                    private static final long serialVersionUID = 1L;
                    private int index = 0;

                    @Override
                    public java.lang.Object next() {
                        if (index < jit.length) {
                            return ceylon.language.Byte.instance(jit[index++]);
                        }
                        return finished_.get_();
                    }
                    
                };
            }
        };
    }
    
    // Used by the code generator
    // Copied from ceylon.interop.java
    @SuppressWarnings("unchecked")
    public static <T> java.lang.Class<? extends T> javaClassForModel(ClassOrInterface<? extends T> model) {
        ClassOrInterfaceDeclaration decl = model.getDeclaration();
        if(decl instanceof ClassOrInterfaceDeclarationImpl){
            ClassOrInterfaceDeclarationImpl ci =
                    (ClassOrInterfaceDeclarationImpl) decl;
            String prefix = null;
            while (ci.getJavaClass().equals(ObjectArray.class)) {
                model = ((ClassOrInterface)model.getTypeArgumentList().getFromFirst(0));
                decl = model.getDeclaration();
                ci = (ClassOrInterfaceDeclarationImpl) decl;
                prefix = prefix == null ? "[" : prefix+"[";
            }
            java.lang.Class result = (java.lang.Class<? extends T>) classErasure(ci.getJavaClass());
            if (prefix != null) {
                String name = result.isArray()
                        && result.getComponentType().isPrimitive() 
                        ? prefix+result.getName()
                        : prefix+"L" + result.getName() + ";";
                try {
                    result = java.lang.Class.forName(name, false, result.getClassLoader());
                } catch (ClassNotFoundException e1) {
                    rethrow(e1);
                }
            }
            return result;
        }
        throw new ceylon.language.AssertionError("Unsupported declaration type: "+decl);
    }

    // Used by the code generator
    // Copied from ceylon.interop.java
    @SuppressWarnings("unchecked")
    public static <T> java.lang.Class<? extends T> classErasure(java.lang.Class<? extends T> klass){
      // dirty but keeps the logic in one place
      return (java.lang.Class<? extends T>)
              TypeDescriptor.klass(klass)
                  .getArrayElementClass();
    }
    
    private static boolean hasReified(Object instance) {
        return instance == null || instance instanceof ReifiedType;
    }
    
    private static String typeDescription(Object instance) {
        if (hasReified(instance)) {
            return "type " + type_.type(Nothing.NothingType, instance).toString();
        } else {
            return classDeclaration_.classDeclaration(instance).toString();
        }
    }
    
    /**
     * Returns part of the error message for an AssertionError
     * thrown for a failed {@code is} assertion.
     */
    public static String assertIsFailed(boolean negated, TypeDescriptor $reifiedType, @Nullable Object operand) {
        String typeDescription = typeDescription(operand);
        Type<? extends Object> givenType = typeLiteral_.typeLiteral($reifiedType);
        String message = System.lineSeparator()+"\t\texpression has "+typeDescription;
        if (!(hasReified(operand) 
                && type_.type(Nothing.NothingType, operand).exactly(givenType))) {
            message += " which is "+(negated ? "": "not")+" a subtype of "+ givenType;
        }
        return message;
    }
    
    public static String assertBinOpFailed( 
            @Nullable Object lhs, 
            @Nullable Object rhs) {
        String message = System.lineSeparator()+"\t\tleft-hand expression is " + (lhs == null ? "«null»" : lhs);
        message += System.lineSeparator()+"\t\tright-hand expression is " + (rhs == null ? "«null»" : rhs);
        if (!(hasReified(lhs) 
                && hasReified(rhs) 
                && type_.type(Nothing.NothingType, lhs).exactly(type_.type(Nothing.NothingType, rhs)))) {
            message += System.lineSeparator()+"\t\tleft-hand expression has " + typeDescription(lhs);
            message += System.lineSeparator()+"\t\tright-hand expression has " + typeDescription(rhs);
        }
        return message;
    }
    
    public static String assertWithinOpFailed(
            @NonNull Object lhs,
            @NonNull Object middle, 
            @NonNull Object rhs) {
        String message = System.lineSeparator()+"\t\tleft-hand expression is " + (lhs == null ? "«null»" : lhs);
        message += System.lineSeparator()+"\t\tmiddle expression is " + (rhs == null ? "«null»" : middle);
        message += System.lineSeparator()+"\t\tright-hand expression is " + (rhs == null ? "«null»" : rhs);
        return message;
    }
    
    public static long[] unwrapLongArray(Object array) {
        Object a = ((Array)array).toArray();
        if (a instanceof long[]) {
            return (long[])a;
        } else {
            throw new ceylon.language.AssertionError("unexpected array "+a.getClass().getComponentType().getName()+"[]");
        }
    }
    
    public static int[] unwrapIntArray(Object array) {
        Object a = ((Array)array).toArray();
        if (a instanceof int[]) {
            return (int[])a;
        } else {
            throw new ceylon.language.AssertionError("unexpected array "+a.getClass().getComponentType().getName()+"[]");
        }
    }
    
    public static short[] unwrapShortArray(Array<? extends java.lang.Short> array) {
        Object a = array.toArray();
        if (a instanceof short[]) {
            return (short[])a;
        } else {
            throw new ceylon.language.AssertionError("unexpected array "+a.getClass().getComponentType().getName()+"[]");
        }
    }
    
    public static double[] unwrapDoubleArray(Object array) {
        Object a = ((Array)array).toArray();
        if (a instanceof double[]) {
            return (double[])a;
        } else {
            throw new ceylon.language.AssertionError("unexpected array "+a.getClass().getComponentType().getName()+"[]");
        }
    }
    
    public static float[] unwrapFloatArray(Array<? extends java.lang.Float> array) {
        Object a = array.toArray();
        if (a instanceof float[]) {
            return (float[])a;
        } else {
            throw new ceylon.language.AssertionError("unexpected array "+a.getClass().getComponentType().getName()+"[]");
        }
    }
    
    public static char[] unwrapCharArray(Object array) {
        Object a = ((Array)array).toArray();
        if (a instanceof char[]) {
            return (char[])a;
        } else {
            throw new ceylon.language.AssertionError("unexpected array "+a.getClass().getComponentType().getName()+"[]");
        }
    }

    public static byte[] unwrapByteArray(Object array) {
        Object a = ((Array)array).toArray();
        if (a instanceof byte[]) {
            return (byte[])a;
        } else {
            throw new ceylon.language.AssertionError("unexpected array "+a.getClass().getComponentType().getName()+"[]");
        }
    }

    public static boolean[] unwrapBooleanArray(Object array) {
        Object a = ((Array)array).toArray();
        if (a instanceof boolean[]) {
            return (boolean[])a;
        } else {
            throw new ceylon.language.AssertionError("unexpected array "+a.getClass().getComponentType().getName()+"[]");
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T[] unwrapObjectArray(
            @SuppressWarnings("unused") @Ignore TypeDescriptor $reified$T, 
            Array<T> array) {
        Object a = array.toArray();
        if (a instanceof Object[]) {
            return (T[])a;
        }
        throw new ceylon.language.AssertionError("unexpected array "+a.getClass().getComponentType().getName()+"[] with type "+ $reified$T);
        
    }
}
