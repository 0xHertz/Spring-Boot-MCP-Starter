package com.xatlj.mcp.transport;

import com.fasterxml.jackson.databind.*;
import com.xatlj.mcp.config.McpProperties;
import com.xatlj.mcp.core.*;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@RestController
public class McpEndpoint {
    private static final Logger log=LoggerFactory.getLogger(McpEndpoint.class);
    private static final String PROTOCOL="2026-07-28";
    private final ObjectMapper mapper;
    private final McpToolRegistry registry;
    private final McpToolExecutor executor;
    private final McpProperties props;
    private final McpAuditLogger audit;

    public McpEndpoint(ObjectMapper m,McpToolRegistry r,McpToolExecutor e,McpProperties p,McpAuditLogger a){
        mapper=m;registry=r;executor=e;props=p;audit=a;
    }

    @RequestMapping(value="${mcp.server.path:/mcp}",method=RequestMethod.POST,consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> post(@RequestBody JsonNode body, HttpServletRequest request, HttpServletResponse response,
                                  @RequestHeader(value="Accept",required=false) String accept) {
        if(!originAllowed(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if(body==null||!body.isObject()) return protocolError(null,-32600,"Invalid JSON-RPC request",HttpStatus.BAD_REQUEST);

        JsonNode id=body.get("id");
        JsonNode methodNode=body.get("method");
        if(methodNode==null||!methodNode.isTextual()) return protocolError(id,-32600,"Missing method",HttpStatus.BAD_REQUEST);
        String method=methodNode.asText();
        JsonNode params=body.path("params");

        // MCP 2026-07-28: stateless request metadata is carried on every POST.
        String protocolHeader=request.getHeader("MCP-Protocol-Version");
        String methodHeader=request.getHeader("Mcp-Method");
        if(!PROTOCOL.equals(protocolHeader))
            return protocolError(id,-32021,"Unsupported or missing MCP-Protocol-Version; supported: "+PROTOCOL,HttpStatus.BAD_REQUEST);
        if(!method.equals(methodHeader))
            return protocolError(id,-32020,"Mcp-Method header does not match JSON-RPC method",HttpStatus.BAD_REQUEST);
        JsonNode meta=params.path("_meta");
        String bodyProtocol=meta.path("io.modelcontextprotocol/protocolVersion").asText("");
        if(!PROTOCOL.equals(bodyProtocol))
            return protocolError(id,-32020,"Protocol version metadata is missing or mismatched",HttpStatus.BAD_REQUEST);

        if("tools/call".equals(method)){
            String bodyName=params.path("name").asText("");
            String nameHeader=request.getHeader("Mcp-Name");
            if(!bodyName.equals(nameHeader))
                return protocolError(id,-32020,"Mcp-Name header does not match tool name",HttpStatus.BAD_REQUEST);
        }

        if(method.startsWith("notifications/")) return ResponseEntity.accepted().build();

        try {
            Map<String,Object> result;
            if("server/discover".equals(method)) result=discover();
            else if("tools/list".equals(method)) result=toolsList();
            else if("tools/call".equals(method)) result=toolsCall(params, request, response);
            else return protocolError(id,-32601,"Method not found",HttpStatus.NOT_FOUND);

            Map<String,Object> mresponse=success(id,result);
            if(props.isSseEnabled()&&accept!=null&&accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)){
                String data=mapper.writeValueAsString(mresponse);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .header("X-Accel-Buffering","no")
                        .body("event: message\ndata: "+data+"\n\n");
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mresponse);
        } catch(McpValidationException e) {
            return protocolError(id,-32602,e.getMessage(),HttpStatus.BAD_REQUEST);
        } catch(Exception e) {
            if(isAccessDenied(e)) return protocolError(id,-32001,"Not authorized",HttpStatus.FORBIDDEN);
            log.error("MCP request failed, method={}",method,e);
            String message=props.isExposeErrorDetails()?safeMessage(e):"Tool execution failed";
            return protocolError(id,-32603,message,HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value="${mcp.server.path:/mcp}",method=RequestMethod.GET)
    public ResponseEntity<Void> get(){
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW,"POST").build();
    }

    private boolean originAllowed(HttpServletRequest req){
        String origin=req.getHeader("Origin");
        if(origin==null||origin.trim().isEmpty()) return true;
        return props.getAllowedOrigins()!=null&&props.getAllowedOrigins().contains(origin);
    }


    private Map<String, Object> discover() {
        Map<String, Object> capabilities = new LinkedHashMap<String, Object>();

        // 当前 Starter 实际支持的能力
        capabilities.put("tools", new LinkedHashMap<String, Object>());

        Map<String, Object> serverInfo = new LinkedHashMap<String, Object>();
        serverInfo.put("name", props.getName());
        serverInfo.put("version", props.getVersion());

        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("io.modelcontextprotocol/serverInfo", serverInfo);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("resultType", "complete");
        result.put("supportedVersions",
                Collections.singletonList(PROTOCOL));
        result.put("capabilities", capabilities);
        result.put("_meta", meta);

        // Discovery 缓存信息
        result.put("ttlMs", 3600000);
        result.put("cacheScope", "private");

        return result;
    }



    private Map<String, Object> toolsList() {
        List<Map<String, Object>> list =
                new ArrayList<Map<String, Object>>();

        for (McpToolDefinition t : registry.list()) {
            Map<String, Object> item =
                    new LinkedHashMap<String, Object>();

            item.put("name", t.getName());
            item.put("description", t.getDescription());
            item.put("inputSchema", t.getInputSchema());

            Map<String, Object> annotations =
                    new LinkedHashMap<String, Object>();

            annotations.put("readOnlyHint", t.isReadOnly());
            annotations.put("destructiveHint", t.isDestructive());
            annotations.put("openWorldHint", false);

            item.put("annotations", annotations);
            list.add(item);
        }

        Map<String, Object> result =
                new LinkedHashMap<String, Object>();

        result.put("resultType", "complete");
        result.put("tools", list);

        // SEP-2549: List result caching hints
        result.put("ttlMs", 0);
        result.put("cacheScope", "private");

        result.put("_meta", serverMeta());

        return result;
    }


    @SuppressWarnings("unchecked")
    private Map<String,Object> toolsCall(JsonNode p, HttpServletRequest request,HttpServletResponse response)throws Exception{
        String name=p.path("name").asText("");
        if(name.isEmpty())throw new McpValidationException("Missing tool name");
        McpToolDefinition tool=registry.get(name);
        if(tool==null)throw new McpValidationException("Unknown tool");
        Map<String,Object> args=p.has("arguments")?mapper.convertValue(p.get("arguments"),Map.class):Collections.<String,Object>emptyMap();
        long start=System.currentTimeMillis();
        String user=currentUsername();
        String status="SUCCESS";
        String errorCode=null;
        try{
            Object output=executor.execute(tool,args,request,response);
            return toolResult(mapper.writeValueAsString(output),false);
        }catch(Exception e){
            status=isAccessDenied(e)?"DENIED":"FAILED";
            errorCode=isAccessDenied(e)?"FORBIDDEN":(e instanceof McpValidationException?"VALIDATION":"EXECUTION");
            String message=isAccessDenied(e)?"Not authorized":
                    (e instanceof McpValidationException?safeMessage(e):
                    (props.isExposeErrorDetails()?safeMessage(e):"Tool execution failed"));
            return toolResult(message,true);
        }finally{
            try{audit.log(new McpAuditRecord(name,user,status,System.currentTimeMillis()-start,errorCode));}
            catch(Exception auditError){log.error("MCP audit logger failed",auditError);}
        }
    }

    private Map<String,Object> toolResult(String text,boolean isError){
        Map<String,Object> content=new LinkedHashMap<String,Object>();
        content.put("type","text");content.put("text",text);
        Map<String,Object> r=new LinkedHashMap<String,Object>();
        r.put("content",Collections.singletonList(content));r.put("isError",isError);
        r.put("resultType","complete");r.put("_meta",serverMeta());
        return r;
    }

    private Map<String,Object> serverMeta(){
        Map<String,Object> server=new LinkedHashMap<String,Object>();
        server.put("name",props.getName());server.put("version",props.getVersion());
        Map<String,Object> m=new LinkedHashMap<String,Object>();
        m.put("io.modelcontextprotocol/serverInfo",server);
        return m;
    }

    private String currentUsername(){
        try{
            org.springframework.security.core.Authentication a=
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            return a==null?"anonymous":String.valueOf(a.getName());
        }catch(NoClassDefFoundError e){return "unknown";}
    }

    private boolean isAccessDenied(Throwable t){
        while(t!=null){
            String n=t.getClass().getName();
            if(n.endsWith(".AccessDeniedException")||n.endsWith(".AuthorizationServiceException"))return true;
            t=t.getCause();
        }
        return false;
    }

    private String safeMessage(Exception e){
        String m=e.getMessage();
        if(m==null||m.trim().isEmpty())return e.getClass().getSimpleName();
        return m.length()>300?m.substring(0,300):m;
    }

    private Map<String,Object> success(JsonNode id,Object result){
        Map<String,Object> r=new LinkedHashMap<String,Object>();
        r.put("jsonrpc","2.0");r.put("id",id);r.put("result",result);return r;
    }

    private ResponseEntity<Object> protocolError(JsonNode id,int code,String message,HttpStatus status){
        Map<String,Object>d=new LinkedHashMap<String,Object>();d.put("code",code);d.put("message",message);
        Map<String,Object>r=new LinkedHashMap<String,Object>();
        r.put("jsonrpc","2.0");r.put("id",id);r.put("error",d);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(r);
    }
}
