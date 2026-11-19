package com.xatlj.mcp.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Optional Swagger/OpenAPI bridge without compile-time swagger dependency.
 * Recognizes common annotation simple names: ApiOperation, Operation, ApiParam, Parameter.
 */
public class SwaggerDescriptionResolver {
    public String methodDescription(Method method, String fallback) {
        for (Annotation a : method.getAnnotations()) {
            String n=a.annotationType().getSimpleName();
            if ("ApiOperation".equals(n) || "Operation".equals(n)) {
                String value=readString(a,"value");
                if (value.isEmpty()) value=readString(a,"summary");
                if (value.isEmpty()) value=readString(a,"notes");
                if (!value.isEmpty()) return value;
            }
        }
        return fallback == null ? "" : fallback;
    }

    public String parameterDescription(Parameter p, String fallback) {
        for (Annotation a : p.getAnnotations()) {
            String n=a.annotationType().getSimpleName();
            if ("ApiParam".equals(n) || "Parameter".equals(n)) {
                String value=readString(a,"value");
                if (value.isEmpty()) value=readString(a,"description");
                if (!value.isEmpty()) return value;
            }
        }
        return fallback == null ? "" : fallback;
    }

    private String readString(Annotation a,String method) {
        try {
            Object value=a.annotationType().getMethod(method).invoke(a);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) { return ""; }
    }
}
