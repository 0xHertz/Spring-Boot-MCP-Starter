package com.xatlj.mcp.core;

public class McpAuditRecord {
    private String toolName;
    private String username;
    private String status;
    private long durationMillis;
    private String errorCode;

    public McpAuditRecord(String toolName, String username, String status, long durationMillis, String errorCode) {
        this.toolName=toolName; this.username=username; this.status=status;
        this.durationMillis=durationMillis; this.errorCode=errorCode;
    }
    public String getToolName(){return toolName;}
    public String getUsername(){return username;}
    public String getStatus(){return status;}
    public long getDurationMillis(){return durationMillis;}
    public String getErrorCode(){return errorCode;}
}
