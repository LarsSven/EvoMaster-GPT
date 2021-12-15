package org.evomaster.client.java.controller.problem.rpc.schema;

import org.evomaster.client.java.controller.api.dto.problem.rpc.RPCActionDto;
import org.evomaster.client.java.controller.api.dto.problem.rpc.RPCInterfaceSchemaDto;
import org.evomaster.client.java.controller.api.dto.problem.rpc.RPCType;
import org.evomaster.client.java.controller.problem.rpc.schema.params.NamedTypedValue;
import org.evomaster.client.java.controller.problem.rpc.schema.types.CycleObjectType;
import org.evomaster.client.java.controller.problem.rpc.schema.types.TypeSchema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * schema dto of the RCP service
 */
public final class InterfaceSchema{
    /**
     * name of the interface
     */
    private final String name;

    /**
     * name of the client
     */
    private final String clientInfo;

    /**
     * a list of available endpoints in the service
     */
    private List<EndpointSchema> endpoints;

    /**
     * key is the full name of type
     * value is its type schema
     */
    private Map<String, TypeSchema> typeCollections = new HashMap<>();

    /**
     * key is the full name of type
     * value is one example of param with the TypeSchema
     */
    private Map<String, NamedTypedValue> objParamCollections = new HashMap<>();

    /**
     * type of the RPC
     */
    private final RPCType rpcType;

    public InterfaceSchema(String name, List<EndpointSchema> endpoints, String client, RPCType rpcType) {
        this.name = name;
        this.endpoints = endpoints;
        this.clientInfo = client;
        this.rpcType = rpcType;
    }

    public void registerType(TypeSchema type, NamedTypedValue param){
        String typeName = type.getFullTypeName();
        if (!(type instanceof CycleObjectType)){
            typeCollections.put(typeName, type);
        }
        if (!(param.getType() instanceof CycleObjectType))
            objParamCollections.put(param.getType().getFullTypeName(), param);
    }

    public Map<String, NamedTypedValue> getObjParamCollections() {
        return objParamCollections;
    }

    public TypeSchema getTypeOrNull(String name){
        return typeCollections.get(name);
    }

    public List<EndpointSchema> getEndpoints(){
        return endpoints;
    }

    public RPCType getRpcType() {
        return rpcType;
    }

    /**
     * @param name is a name of an endpoint
     * @return a list of endpoints based on the specified name
     */
    public List<EndpointSchema> findEndpoints(String name){
        return endpoints.stream().filter(s-> s.getName().equals(name)).collect(Collectors.toList());
    }

    /**
     *
     * @param dto is a rpc action dto
     * @return one endpoint based on an action dto
     * note that there should only exist one endpoint which conforms with the specified dto.
     */
    public EndpointSchema getOneEndpoint(RPCActionDto dto){
        List<EndpointSchema> list = endpoints.stream().filter(s-> s.sameEndpoint(dto)).collect(Collectors.toList());

        if (list.size() == 1)
            return list.get(0);

        if (list.size() > 1)
            throw new RuntimeException("ERROR: there exists more than 1 endpoint which conforms with the specified dto");

        throw new RuntimeException("ERROR: there does not exist any endpoint which conforms with the specified dto");
    }

    public String getName() {
        return name;
    }

    public String getClientInfo(){
        return clientInfo;
    }

    public Map<String, TypeSchema> getTypeCollections() {
        return typeCollections;
    }

    public RPCInterfaceSchemaDto getDto(){
        RPCInterfaceSchemaDto dto = new RPCInterfaceSchemaDto();
        dto.interfaceId = this.getName();
        dto.clientInfo = this.getClientInfo();
        dto.types = objParamCollections.values().stream().map(NamedTypedValue::getDto).collect(Collectors.toList());
        dto.endpoints = endpoints.stream().map(EndpointSchema::getDto).collect(Collectors.toList());
        dto.endpoints.forEach(e-> {
            e.interfaceId = dto.interfaceId;
            e.clientInfo = dto.clientInfo;
        });
        return dto;
    }
}
