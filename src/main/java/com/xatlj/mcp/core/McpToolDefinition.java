package com.xatlj.mcp.core;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/*
每个 McpToolDefinition 保存的信息包括：
- 工具名称
- 工具描述
- Controller Bean
- 可调用 Method
- 参数定义
- JSON Schema
 */
public class McpToolDefinition {
    private final String name, description;
    private final Object bean;
    private final Method method;
    private final List<ParameterDefinition> parameters;
    private final Map<String,Object> inputSchema;
    private final boolean readOnly, destructive;

    public McpToolDefinition(String name,String description,Object bean,Method method,
            List<ParameterDefinition> parameters,Map<String,Object> inputSchema,boolean readOnly,boolean destructive) {
        this.name=name;this.description=description;this.bean=bean;this.method=method;
        this.parameters=parameters;this.inputSchema=inputSchema;this.readOnly=readOnly;this.destructive=destructive;
    }
    public String getName(){return name;} public String getDescription(){return description;}
    public Object getBean(){return bean;} public Method getMethod(){return method;}
    public List<ParameterDefinition> getParameters(){return parameters;}
    public Map<String,Object> getInputSchema(){return inputSchema;}
    public boolean isReadOnly(){return readOnly;} public boolean isDestructive(){return destructive;}

    public static class ParameterDefinition {
        private final String name,description;
        private final boolean required;
        private final Type genericType;
        private final Class<?> rawType;
        public ParameterDefinition(String n,String d,boolean r,Type t,Class<?> c){
            name=n;description=d;required=r;genericType=t;rawType=c;
        }
        public String getName(){return name;} public String getDescription(){return description;}
        public boolean isRequired(){return required;} public Type getGenericType(){return genericType;}
        public Class<?> getRawType(){return rawType;}
    }
}
