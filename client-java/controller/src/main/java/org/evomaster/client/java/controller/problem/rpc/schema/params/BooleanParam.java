package org.evomaster.client.java.controller.problem.rpc.schema.params;

import org.evomaster.client.java.controller.api.dto.problem.rpc.ParamDto;
import org.evomaster.client.java.controller.api.dto.problem.rpc.RPCSupportedDataType;
import org.evomaster.client.java.controller.problem.rpc.schema.types.PrimitiveOrWrapperType;

/**
 * boolean param
 */
public class BooleanParam extends PrimitiveOrWrapperParam<Boolean> {
    public BooleanParam(String name, String type, String fullTypeName, Class<?> clazz) {
        super(name, type, fullTypeName, clazz);
    }

    public BooleanParam(String name, PrimitiveOrWrapperType type) {
        super(name, type);
    }

    @Override
    public ParamDto getDto() {
        ParamDto dto = super.getDto();
        if (getValue() != null)
            dto.jsonValue = getValue().toString();
        if (getType().isWrapper)
            dto.type.type = RPCSupportedDataType.BOOLEAN;
        else
            dto.type.type = RPCSupportedDataType.P_BOOLEAN;
        return dto;
    }

    @Override
    public BooleanParam copyStructure() {
        return new BooleanParam(getName(), getType());
    }


    @Override
    public void setValueBasedOnDto(ParamDto dto) {
        try {
            if (dto.jsonValue != null)
                setValue(Boolean.parseBoolean(dto.jsonValue));
        }catch (NumberFormatException e){
            throw new RuntimeException("ERROR: fail to convert "+dto.jsonValue+" as boolean value");
        }
    }

    @Override
    protected void setValueBasedOnValidInstance(Object instance) {
        setValue((Boolean) instance);
    }

    @Override
    public boolean isValidInstance(Object instance) {
        return instance instanceof Boolean;
    }
}
