package com.xatlj.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "mcp.server")
public class McpProperties {
    private boolean enabled = true;
    private String path = "/mcp";
    private String name = "spring-boot-mcp-server";
    private String version = "2.0.0";
    private List<String> allowedOrigins = new ArrayList<String>();
    private List<String> allowedToolNames = new ArrayList<String>();
    private List<String> scanBasePackages = new ArrayList<String>();
    private int maxRequestBytes = 1048576;
    private boolean sseEnabled = true;
    private boolean exposeErrorDetails = false;

    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getPath(){return path;} public void setPath(String v){path=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getVersion(){return version;} public void setVersion(String v){version=v;}
    public List<String> getAllowedOrigins(){return allowedOrigins;} public void setAllowedOrigins(List<String> v){allowedOrigins=v;}
    public List<String> getAllowedToolNames(){return allowedToolNames;} public void setAllowedToolNames(List<String> v){allowedToolNames=v;}
    public List<String> getScanBasePackages(){return scanBasePackages;} public void setScanBasePackages(List<String> v){scanBasePackages=v;}
    public int getMaxRequestBytes(){return maxRequestBytes;} public void setMaxRequestBytes(int v){maxRequestBytes=v;}
    public boolean isSseEnabled(){return sseEnabled;} public void setSseEnabled(boolean v){sseEnabled=v;}
    public boolean isExposeErrorDetails(){return exposeErrorDetails;} public void setExposeErrorDetails(boolean v){exposeErrorDetails=v;}
}
