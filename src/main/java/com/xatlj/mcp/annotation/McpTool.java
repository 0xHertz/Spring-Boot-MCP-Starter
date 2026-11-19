package com.xatlj.mcp.annotation;

import java.lang.annotation.*;

// 标记一个方法，使其成为 MCP 工具
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {
    String name();
    String description() default "";
    boolean enabled() default true;
    boolean readOnly() default true;
    boolean destructive() default false;
}
