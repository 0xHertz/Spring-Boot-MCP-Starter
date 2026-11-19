package com.xatlj.mcp.core;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.type.TypeFactory;
import javax.validation.constraints.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

// 核心：参数结构生成
public class JsonSchemaBuilder {
    private final ObjectMapper mapper;
    public JsonSchemaBuilder(ObjectMapper mapper){this.mapper=mapper;}

    public Map<String,Object> build(List<McpToolDefinition.ParameterDefinition> params) {
        Map<String,Object> root=map("type","object");
        Map<String,Object> props=new LinkedHashMap<String,Object>();
        List<String> required=new ArrayList<String>();
        for(McpToolDefinition.ParameterDefinition p:params) {
            Map<String,Object> s=schema(TypeFactory.defaultInstance().constructType(p.getGenericType()),new HashSet<String>());
            if(!p.getDescription().isEmpty())s.put("description",p.getDescription());
            props.put(p.getName(),s);
            if(p.isRequired())required.add(p.getName());
        }
        root.put("properties",props);
        if(!required.isEmpty())root.put("required",required);
        root.put("additionalProperties",false);
        return root;
    }

    private Map<String,Object> schema(JavaType t,Set<String> visiting) {
        Class<?> raw=t.getRawClass();
        if(raw==String.class || raw==Character.class || raw==char.class || raw.isEnum()) {
            Map<String,Object> m=map("type","string");
            if(raw.isEnum()){List<String> e=new ArrayList<String>();for(Object x:raw.getEnumConstants())e.add(String.valueOf(x));m.put("enum",e);}
            return m;
        }
        if(raw==Boolean.class || raw==boolean.class)return map("type","boolean");
        if(Number.class.isAssignableFrom(raw)||raw.isPrimitive())return map("type",
                (raw==Float.class||raw==Double.class||raw==float.class||raw==double.class)?"number":"integer");
        if(raw.isArray())return map("type","array","items",schema(TypeFactory.defaultInstance().constructType(raw.getComponentType()),visiting));
        if(Collection.class.isAssignableFrom(raw)) {
            JavaType content=t.getContentType();
            return map("type","array","items",content==null?map("type","object"):schema(content,visiting));
        }
        if(Map.class.isAssignableFrom(raw))return map("type","object","additionalProperties",true);
        String key=raw.getName();
        if(visiting.contains(key))return map("type","object");
        visiting.add(key);
        Map<String,Object> m=map("type","object");
        Map<String,Object> props=new LinkedHashMap<String,Object>();
        List<String> required=new ArrayList<String>();
        BeanDescription desc=mapper.getDeserializationConfig().introspect(t);
        for(BeanPropertyDefinition p:desc.findProperties()) {
            if(!p.couldDeserialize())continue;
            JavaType pt=p.getPrimaryType();
            Map<String,Object> ps=schema(pt,visiting);
            AnnotatedElement member=p.getPrimaryMember()==null?null:(AnnotatedElement)p.getPrimaryMember().getMember();
            if(member!=null) {
                for(Annotation a:member.getAnnotations()) {
                    String n=a.annotationType().getSimpleName();
                    if("NotNull".equals(n)||"NotBlank".equals(n)||"NotEmpty".equals(n))required.add(p.getName());
                    if("Size".equals(n)) {
                        Object min=invoke(a,"min"),max=invoke(a,"max");
                        if(min instanceof Integer && ((Integer)min)>0)ps.put("minLength",min);
                        if(max instanceof Integer && ((Integer)max)<Integer.MAX_VALUE)ps.put("maxLength",max);
                    }
                }
            }
            props.put(p.getName(),ps);
        }
        visiting.remove(key);
        m.put("properties",props);
        if(!required.isEmpty())m.put("required",required);
        m.put("additionalProperties",false);
        return m;
    }
    private Object invoke(Annotation a,String name){try{return a.annotationType().getMethod(name).invoke(a);}catch(Exception e){return null;}}
    private Map<String,Object> map(Object... kv){Map<String,Object> m=new LinkedHashMap<String,Object>();for(int i=0;i<kv.length;i+=2)m.put(String.valueOf(kv[i]),kv[i+1]);return m;}
}
