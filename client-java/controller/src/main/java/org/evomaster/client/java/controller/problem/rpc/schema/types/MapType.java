package org.evomaster.client.java.controller.problem.rpc.schema.types;

import org.evomaster.client.java.controller.api.dto.problem.rpc.TypeDto;
import org.evomaster.client.java.controller.problem.rpc.schema.params.PairParam;

/**
 * map type
 */
public class MapType extends TypeSchema{
    /**
     * template of keys of the map
     */
    private final PairParam template;


    public MapType(String type, String fullTypeName, PairParam template, Class<?> clazz) {
        super(type, fullTypeName, clazz);
        this.template = template;
    }

    public PairParam getTemplate() {
        return template;
    }

    @Override
    public TypeDto getDto() {
        TypeDto dto = super.getDto();
        dto.example = template.getDto();
        return dto;
    }
}
