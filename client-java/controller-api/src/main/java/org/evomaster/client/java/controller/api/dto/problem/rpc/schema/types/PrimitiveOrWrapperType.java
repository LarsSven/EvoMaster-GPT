package org.evomaster.client.java.controller.api.dto.problem.rpc.schema.types;

import java.util.Arrays;
import java.util.List;

/**
 * primitive types
 */
public class PrimitiveOrWrapperType extends TypeSchema {

    /**
     * represent if the type is wrapper
     * for instance, isWrapper for Integer is true and for int is false
     */
    private final boolean isWrapper;

    public PrimitiveOrWrapperType(String type, String fullTypeName, boolean isWrapper) {
        super(type, fullTypeName);
        if (!isPrimitiveOrTypes(type))
            throw new IllegalStateException("the type is not Primitive Or Wrapper class: "+ type);
        this.isWrapper = isWrapper;
    }

    public PrimitiveOrWrapperType(String type, String fullTypeName){
        this(type, fullTypeName, types.indexOf(type) >=8);
    }

    private final static List<String> types = Arrays.asList("int","byte","short","long","float","double","boolean","char","Integer","Byte","Short","Long","Float","Double","Boolean","Character");

    public static boolean isPrimitiveOrTypes(String type){
        return types.contains(type);
    }

    public static boolean isPrimitiveOrTypes(Class<?> clazz){
        if (clazz.isPrimitive()) return true;
        return  clazz == Integer.class || clazz == Byte.class || clazz == Short.class || clazz == Long.class ||
                clazz== Float.class || clazz == Double.class || clazz == Boolean.class || clazz == Character.class;
    }
}
