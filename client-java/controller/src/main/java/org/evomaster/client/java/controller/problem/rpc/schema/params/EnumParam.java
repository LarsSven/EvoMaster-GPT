package org.evomaster.client.java.controller.problem.rpc.schema.params;

import org.evomaster.client.java.controller.api.dto.problem.rpc.ParamDto;
import org.evomaster.client.java.controller.problem.rpc.schema.types.EnumType;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * enum parameter
 */
public class EnumParam extends NamedTypedValue<EnumType, Integer> {


    public EnumParam(String name, EnumType type) {
        super(name, type);
    }

    @Override
    public Object newInstance() throws ClassNotFoundException {
        Class <? extends Enum> clazz = (Class < ? extends Enum >) Class.forName(getType().getFullTypeName());
        String value = getType().getItems()[getValue()];
        return Enum.valueOf(clazz, value);
    }

    @Override
    public ParamDto getDto() {
        ParamDto dto = super.getDto();
        if (getValue() != null)
            dto.jsonValue = getValue().toString();
        return dto;
    }

    @Override
    public EnumParam copyStructure() {
        return new EnumParam(getName(), getType());
    }

    @Override
    public void setValueBasedOnDto(ParamDto dto) {
        try {
            if (dto.jsonValue != null)
                setValue(Integer.parseInt(dto.jsonValue));
        }catch (NumberFormatException e){
            throw new RuntimeException("ERROR: fail to convert "+dto.jsonValue+" as int value for setting enum");
        }
    }

    @Override
    protected void setValueBasedOnValidInstance(Object instance) {
        Method m = null;
        try {
            m = instance.getClass().getMethod("ordinal");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("ERROR: fail to process setValueBasedOnValidInstance, with error msg:"+e.getMessage());
        }
        m.setAccessible(true);
        try {
            setValue((int) m.invoke(instance));
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("ERROR: fail to process setValueBasedOnValidInstance, with error msg:"+e.getMessage());
        }
    }
}
