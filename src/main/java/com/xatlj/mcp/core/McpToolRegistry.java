package com.xatlj.mcp.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 核心：工具注册表
/*
内部维护工具名称与工具定义之间的映射关系：
query_device       -> McpToolDefinition
query_device_list  -> McpToolDefinition
query_alarm_list   -> McpToolDefinition
query_device_stats -> McpToolDefinition
 */
public class McpToolRegistry {
    private final Map<String,McpToolDefinition> tools=new ConcurrentHashMap<String,McpToolDefinition>();
    public void register(McpToolDefinition d){
        if(tools.putIfAbsent(d.getName(),d)!=null)throw new IllegalStateException("Duplicate MCP tool: "+d.getName());
    }
    public McpToolDefinition get(String n){return tools.get(n);}
    public List<McpToolDefinition> list(){
        List<McpToolDefinition> r=new ArrayList<McpToolDefinition>(tools.values());
        Collections.sort(r,new Comparator<McpToolDefinition>(){public int compare(McpToolDefinition a,McpToolDefinition b){return a.getName().compareTo(b.getName());}});
        return r;
    }
}
