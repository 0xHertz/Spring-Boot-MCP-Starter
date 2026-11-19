package com.xatlj.mcp.core;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.TypeFactory;
import javax.validation.*;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.*;
import java.util.*;

// 核心：工具执行器
public class McpToolExecutor {
    private final ObjectMapper mapper;
    private final Validator validator;

    public McpToolExecutor(ObjectMapper mapper,Validator validator){this.mapper=mapper;this.validator=validator;}

    public Object execute(McpToolDefinition tool,Map<String,Object> args) throws Exception {
        if(args==null)args=Collections.emptyMap();
        Set<String> known=new HashSet<String>();
        Object[] values=new Object[tool.getParameters().size()];
        for(int i=0;i<tool.getParameters().size();i++){
            McpToolDefinition.ParameterDefinition p=tool.getParameters().get(i);
            known.add(p.getName());
            Object raw=args.get(p.getName());
            if(raw==null&&p.isRequired())throw new McpValidationException("Missing required argument: "+p.getName());
            JavaType jt=TypeFactory.defaultInstance().constructType(p.getGenericType());
            values[i]=raw==null?null:mapper.convertValue(raw,jt);
        }
        for(String key:args.keySet())if(!known.contains(key))throw new McpValidationException("Unknown argument: "+key);

        if(validator!=null){
            ExecutableValidator ev=validator.forExecutables();
            @SuppressWarnings("unchecked")
            Set<ConstraintViolation<Object>> violations=(Set)ev.validateParameters(tool.getBean(),tool.getMethod(),values);
            if(!violations.isEmpty()){
                List<String> messages=new ArrayList<String>();
                for(ConstraintViolation<?> v:violations)messages.add(v.getPropertyPath()+": "+v.getMessage());
                throw new McpValidationException(join(messages));
            }
            for(Object value:values)if(value!=null){
                Set<ConstraintViolation<Object>> nested=(Set)validator.validate(value);
                if(!nested.isEmpty()){
                    List<String> messages=new ArrayList<String>();
                    for(ConstraintViolation<?> v:nested)messages.add(v.getPropertyPath()+": "+v.getMessage());
                    throw new McpValidationException(join(messages));
                }
            }
        }

        try {
            // Invoke the Spring proxy, not the unwrapped target, so method-security/AOP advice can run.
            return tool.getMethod().invoke(tool.getBean(),values);
        } catch(InvocationTargetException e) {
            Throwable t=e.getTargetException();
            if(t instanceof Exception)throw (Exception)t;
            throw new RuntimeException(t);
        }
    }
    private String join(List<String> s){StringBuilder b=new StringBuilder();for(String x:s){if(b.length()>0)b.append("; ");b.append(x);}return b.toString();}
}
