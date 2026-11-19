package com.xatlj.mcp.annotation;

import java.lang.annotation.*;

// 描述方法参数，告诉 Agent 每个参数是什么
// 每个参数必须显式声明 @McpParam
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpParam {
    String name();
    String description() default "";
    boolean required() default true;
}
