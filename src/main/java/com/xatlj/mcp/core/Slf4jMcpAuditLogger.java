package com.xatlj.mcp.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jMcpAuditLogger implements McpAuditLogger {
    private static final Logger log = LoggerFactory.getLogger("MCP_AUDIT");
    @Override public void log(McpAuditRecord r) {
        log.info("tool={}, user={}, status={}, durationMs={}, errorCode={}",
                r.getToolName(), r.getUsername(), r.getStatus(), r.getDurationMillis(), r.getErrorCode());
    }
}
