package com.xatlj.mcp.core;

// 核心：调用审计
public interface McpAuditLogger {
    void log(McpAuditRecord record);
}
