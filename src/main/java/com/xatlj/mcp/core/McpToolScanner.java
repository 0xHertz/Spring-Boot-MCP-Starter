package com.xatlj.mcp.core;

import com.xatlj.mcp.annotation.*;
import com.xatlj.mcp.config.McpProperties;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import java.lang.reflect.*;
import java.util.*;

// 接口扫描器
public class McpToolScanner implements ApplicationRunner {
    private final ApplicationContext context;
    private final McpToolRegistry registry;
    private final JsonSchemaBuilder schemas;
    private final McpProperties properties;
    private final SwaggerDescriptionResolver swagger;

    public McpToolScanner(ApplicationContext c,McpToolRegistry r,JsonSchemaBuilder s,McpProperties p,SwaggerDescriptionResolver sw){
        context=c;registry=r;schemas=s;properties=p;swagger=sw;
    }

    @Override public void run(ApplicationArguments args) {
        for(Map.Entry<String,Object> entry:context.getBeansOfType(Object.class).entrySet()) {
            Object bean=entry.getValue();
            Class<?> target=AopUtils.getTargetClass(bean);
            if(!isController(target)||!packageAllowed(target.getPackage().getName()))continue;
            for(Method candidate:target.getMethods()) {
                Method targetMethod=BridgeMethodResolver.findBridgedMethod(candidate);
                McpTool ann=AnnotatedElementUtils.findMergedAnnotation(targetMethod,McpTool.class);
                if(ann==null||!ann.enabled())continue;
                if(!ann.name().matches("[A-Za-z0-9_.-]{1,128}"))
                    throw new IllegalStateException("Tool name must be header-safe ASCII: " + ann.name());
                if(!properties.getAllowedToolNames().isEmpty()&&!properties.getAllowedToolNames().contains(ann.name()))continue;
                Method invocable;
                try { invocable=AopUtils.selectInvocableMethod(targetMethod,bean.getClass()); }
                catch(IllegalStateException ex) {
                    throw new IllegalStateException("MCP method must be invocable through Spring proxy (do not invoke target directly): "+targetMethod,ex);
                }
                List<McpToolDefinition.ParameterDefinition> params=new ArrayList<McpToolDefinition.ParameterDefinition>();
                Parameter[] javaParams=targetMethod.getParameters();
                for(Parameter p:javaParams) {
                    McpParam mp=p.getAnnotation(McpParam.class);
                    if(mp==null)throw new IllegalStateException("Every MCP method parameter requires @McpParam: "+targetMethod);
                    if(isWebInfrastructureType(p.getType()))throw new IllegalStateException("Unsupported servlet parameter in MCP tool: "+p.getType().getName());
                    String desc=swagger.parameterDescription(p,mp.description());
                    params.add(new McpToolDefinition.ParameterDefinition(mp.name(),desc,mp.required(),p.getParameterizedType(),p.getType()));
                }
                String description=swagger.methodDescription(targetMethod,ann.description());
                registry.register(new McpToolDefinition(ann.name(),description,bean,invocable,params,
                        schemas.build(params),ann.readOnly(),ann.destructive()));
            }
        }
    }
    private boolean isController(Class<?> c){return AnnotatedElementUtils.hasAnnotation(c,RestController.class)||AnnotatedElementUtils.hasAnnotation(c,Controller.class);}
    private boolean packageAllowed(String name){
        List<String> roots=properties.getScanBasePackages();
        if(roots==null||roots.isEmpty())return true;
        for(String root:roots)if(name.equals(root)||name.startsWith(root+"."))return true;
        return false;
    }
    private boolean isWebInfrastructureType(Class<?> c){
        return javax.servlet.ServletRequest.class.isAssignableFrom(c)||javax.servlet.ServletResponse.class.isAssignableFrom(c)
                ||javax.servlet.http.HttpSession.class.isAssignableFrom(c);
    }
}
