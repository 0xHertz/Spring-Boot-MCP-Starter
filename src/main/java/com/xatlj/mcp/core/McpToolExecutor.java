package com.xatlj.mcp.core;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.TypeFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.*;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.*;
import java.util.*;

// 核心：工具执行器
public class McpToolExecutor {
    private final ObjectMapper mapper;
    private final Validator validator;

    public McpToolExecutor(ObjectMapper mapper,Validator validator){this.mapper=mapper;this.validator=validator;}

    public Object execute(McpToolDefinition tool, Map<String,Object> args, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if(args==null)args=Collections.emptyMap();
        Set<String> known=new HashSet<String>();
        Object[] values = new Object[tool.getMethod().getParameterCount()];
        // 两个独立索引
        int businessIndex = 0;
        java.lang.reflect.Parameter[] methodParameters =
                tool.getMethod().getParameters();
        for(int methodIndex=0;methodIndex<methodParameters.length;methodIndex++){
            Class<?> parameterType = methodParameters[methodIndex].getType();
            // 1. Servlet Request
            if (ServletRequest.class.isAssignableFrom(parameterType)) {
                if (!parameterType.isInstance(request)) {
                    throw new IllegalArgumentException(
                            "Unsupported ServletRequest parameter type: "
                                    + parameterType.getName());
                }
                values[methodIndex] = request;
                continue;
            }
            // 2. Servlet Response
            if (ServletResponse.class.isAssignableFrom(parameterType)) {
                if (!parameterType.isInstance(response)) {
                    throw new IllegalArgumentException(
                            "Unsupported ServletResponse parameter type: "
                                    + parameterType.getName());
                }
                values[methodIndex] = response;
                continue;
            }
            // 3. HttpSession
            if (HttpSession.class.isAssignableFrom(parameterType)) {
                HttpSession session = request.getSession(false);

                if (session != null && !parameterType.isInstance(session)) {
                    throw new IllegalArgumentException(
                            "Unsupported HttpSession parameter type: "
                                    + parameterType.getName());
                }

                values[methodIndex] = session;
                continue;
            }
            // 4. MCP 业务参数
            if (businessIndex >= tool.getParameters().size()) {
                throw new IllegalStateException(
                        "MCP parameter definition mismatch: " + tool.getMethod());
            }

            McpToolDefinition.ParameterDefinition p=tool.getParameters().get(businessIndex);
            known.add(p.getName());
            Object raw=args.get(p.getName());
            if(raw==null&&p.isRequired())throw new McpValidationException("Missing required argument: "+p.getName());
            JavaType jt=TypeFactory.defaultInstance().constructType(p.getGenericType());
            values[methodIndex]=raw==null?null:mapper.convertValue(raw,jt);
            businessIndex++;
        }
        // 检查参数定义数量是否匹配
        if (businessIndex != tool.getParameters().size()) {
            throw new IllegalStateException(
                    "MCP parameter definition count mismatch: " + tool.getMethod());
        }
        for(String key:args.keySet())if(!known.contains(key))throw new McpValidationException("Unknown argument: "+key);

        if (validator != null) {
            ExecutableValidator ev = validator.forExecutables();
            // 校验 Java 方法的全部参数，包含 Servlet 参数位置
            Set<ConstraintViolation<Object>> violations =
                    (Set) ev.validateParameters(
                            tool.getBean(),
                            tool.getMethod(),
                            values
                    );
            if (!violations.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ConstraintViolation<Object> v : violations) {
                    if (sb.length() > 0) {
                        sb.append("; ");
                    }
                    sb.append(v.getPropertyPath())
                            .append(": ")
                            .append(v.getMessage());
                }
                throw new McpValidationException(sb.toString());
            }
            // 只对 MCP 业务参数进行对象级校验
            for (int i = 0; i < methodParameters.length; i++) {
                Class<?> parameterType = methodParameters[i].getType();

                boolean servletParameter =
                        ServletRequest.class.isAssignableFrom(parameterType)
                                || ServletResponse.class.isAssignableFrom(parameterType)
                                || HttpSession.class.isAssignableFrom(parameterType);
                if (servletParameter) {
                    continue;
                }
                Object value = values[i];

                if (value != null) {
                    Set<ConstraintViolation<Object>> nested =
                            (Set) validator.validate(value);
                    if (!nested.isEmpty()) {
                        StringBuilder sb = new StringBuilder();

                        for (ConstraintViolation<Object> v : nested) {
                            if (sb.length() > 0) {
                                sb.append("; ");
                            }
                            sb.append(v.getPropertyPath())
                                    .append(": ")
                                    .append(v.getMessage());
                        }
                        throw new McpValidationException(sb.toString());
                    }
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
