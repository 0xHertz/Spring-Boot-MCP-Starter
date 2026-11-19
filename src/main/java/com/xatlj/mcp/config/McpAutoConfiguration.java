package com.xatlj.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xatlj.mcp.core.*;
import com.xatlj.mcp.transport.McpEndpoint;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.DispatcherServlet;
import javax.validation.Validator;

@Configuration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix="mcp.server",name="enabled",havingValue="true",matchIfMissing=true)
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {
    @Bean @ConditionalOnMissingBean public McpToolRegistry mcpToolRegistry(){return new McpToolRegistry();}
    @Bean @ConditionalOnMissingBean public SwaggerDescriptionResolver swaggerDescriptionResolver(){return new SwaggerDescriptionResolver();}
    @Bean @ConditionalOnMissingBean public JsonSchemaBuilder mcpJsonSchemaBuilder(ObjectMapper mapper){return new JsonSchemaBuilder(mapper);}
    @Bean @ConditionalOnMissingBean public McpAuditLogger mcpAuditLogger(){return new Slf4jMcpAuditLogger();}
    @Bean @ConditionalOnMissingBean
    public McpToolExecutor mcpToolExecutor(ObjectMapper mapper, ObjectProvider<Validator> validator){
        return new McpToolExecutor(mapper,validator.getIfAvailable());
    }
    @Bean public McpToolScanner mcpToolScanner(ApplicationContext c,McpToolRegistry r,JsonSchemaBuilder s,
                                               McpProperties p,SwaggerDescriptionResolver sw){
        return new McpToolScanner(c,r,s,p,sw);
    }
    @Bean public McpEndpoint mcpEndpoint(ObjectMapper m,McpToolRegistry r,McpToolExecutor e,
                                         McpProperties p,McpAuditLogger a){return new McpEndpoint(m,r,e,p,a);}
}
