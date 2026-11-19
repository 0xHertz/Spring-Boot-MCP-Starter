# Spring Boot MCP Starter 2.0.0 — JDK 8

JDK 8 / Spring Boot 2.7.x starter. It exposes annotated Spring MVC controller methods as MCP tools.

## Regist
```
Spring Boot 启动
      |
      v
获取 Spring 容器中的 Controller Bean
      |
      v
扫描 Controller 中的所有方法
      |
      v
判断方法是否存在 @McpTool
      |
      +---- 否 ---> 忽略
      |
      v
读取 @McpTool 信息
      |
      v
读取所有 @McpParam
      |
      v
生成参数 JSON Schema
      |
      v
生成 McpToolDefinition
      |
      v
注册到 McpToolRegistry
```
## Call
```
Agent
  |
  | POST /mcp
  v
McpEndpoint
  |
  | 解析 JSON-RPC 请求
  v
判断 MCP 方法是否为 tools/call
  |
  v
根据工具名称查询 McpToolRegistry
  |
  v
McpToolExecutor
  |
  | 读取参数定义
  | 校验必填参数
  | 拒绝未知参数
  | Jackson 类型转换
  | Bean Validation 校验
  v
调用 Spring Bean Proxy
  |
  v
Controller.query()
  |
  v
DeviceService.query()
  |
  v
数据库
  |
  v
返回结果
  |
  v
转换为 MCP Tool Result
  |
  v
Agent
```
## Fealture

| MCP methods | usage | 2.0 |
| -- | -- | -- |
| server/discover | 发现服务能力 | 支持 |
| tools/list | 获取可用工具列表 | 支持 |
| tools/call | 执行指定工具 | 支持 |

## Build and install

```bash
mvn clean package # 生成 JAR	本地测试、手动分发
mvn clean install # 生成 JAR 并安装到本机 Maven 仓库	本机其他项目引用
mvn deploy        # 发布到 Maven 私服	多个项目、团队共同使用
```

## Add dependency

```xml
<dependency>
  <groupId>com.xatlj</groupId>
  <artifactId>spring-boot-mcp-starter</artifactId>
  <version>2.0.0</version>
</dependency>
```

## Configuration

```yaml
mcp:
  server:
    enabled: true
    path: /mcp
    name: railway-system
    version: 2.0.0
    scan-base-packages:
      - com.xxx.device.controller
      - com.xxx.alarm.controller
      - com.xxx.video.controller
    allowed-tool-names: # 工具白名单，allowed-tool-names: []为空表示不启用工具名称白名单
      - query_device
      - query_alarm
    allowed-origins: # origin白名单,allowed-origins: []为空表示不启用白名单限制
      - https://agent.example.com
    max-request-bytes: 1048576
    sse-enabled: true
    expose-error-details: false # 是否向 Agent 暴露异常详情
```

## Example

```java
@RestController
@RequestMapping("/device")
public class DeviceController {
    @PreAuthorize("hasAuthority('DEVICE_QUERY')")
    @McpTool(name="query_device", description="按设备编号查询设备")
    @GetMapping("/query")
    public Result query(
        @McpParam(name="deviceId", description="设备编号", required=true)
        @NotBlank String deviceId,
        @McpParam(name="filter", description="过滤条件", required=false)
        DeviceFilter filter) {
        return deviceService.query(deviceId, filter);
    }
}
```

All method parameters require `@McpParam`. Use explicit parameter names. Servlet request/response/session arguments are rejected. DTO and generic collection parameter types are converted through Jackson `JavaType`.

## Test

### 基础功能
[ ] Starter 自动配置加载成功
[ ] 多包扫描成功
[ ] 工具名称白名单生效
[ ] 工具描述和参数 Schema 正确生成
[ ] 工具调用成功

### 参数与异常
[ ] 必填参数校验
[ ] 参数类型转换
[ ] 未知参数拒绝
[ ] 工具不存在处理
[ ] 业务异常脱敏

### 安全与兼容
[ ] JWT 认证有效
[ ] Spring AOP 正常执行
[ ] 方法级权限正常执行
[ ] 部门数据权限正常执行
[ ] Origin 白名单生效
[ ] 关闭 MCP 后接口不可用

## Security: integrate with existing Spring Security/JWT

The MCP endpoint is in the same application and request pipeline:
- Ensure your JWT authentication filter runs for `/mcp`, reads the `Authorization: Bearer ...` header, and populates `SecurityContextHolder`.
- Require authentication for `/mcp`; do not expose it with `permitAll`.
- For Boot 2.7 / Spring Security 5, enable method security with `@EnableGlobalMethodSecurity(prePostEnabled = true)` if not already enabled.
- Put `@PreAuthorize` on each exposed controller method or, preferably, on the service method that enforces business permissions.
- The scanner calls the Spring proxy. It fails startup if the annotated method cannot be invoked through that proxy.
- Existing URL authorization for `/device/query` does NOT automatically apply to a Java method invoked from `/mcp`. Method-level authorization is required.
- Department scope must be derived from the authenticated principal or checked in service/mapper logic. Never trust a model-supplied deptCode without validating it.
- Avoid self-invocation (`this.securedMethod()`) for authorization-sensitive logic.
- Configure CSRF policy narrowly for stateless bearer-token APIs; do not disable CSRF globally without review.

Example matcher fragment (adapt to your security configuration):
```java
http.authorizeRequests()
    .antMatchers("/mcp").authenticated()
    .anyRequest().authenticated();
```

## Bean Validation

Add the host dependency:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```
When a `javax.validation.Validator` bean exists, executable parameter constraints and nested DTO constraints are evaluated. Without a provider, validation is skipped.

## Audit

Default audit logger: `MCP_AUDIT`. It records tool, authenticated username, status, duration and error category. It deliberately does not log raw arguments or tool output. Supply your own `McpAuditLogger` bean to persist audit events to Oracle.

## Swagger/OpenAPI

Without a compile-time Swagger dependency, the starter inspects annotations by simple name:
- Swagger 2: `@ApiOperation(value/notes)`, `@ApiParam(value)`
- OpenAPI 3: `@Operation(summary/description)`, `@Parameter(description)`
Explicit `@McpTool.description` and `@McpParam.description` are fallbacks.

## MCP transport

Implements the stateless Streamable HTTP request path for protocol `2026-07-28`:
- POST-only MCP endpoint.
- Required `MCP-Protocol-Version`, `Mcp-Method`, and `params._meta.io.modelcontextprotocol/protocolVersion`.
- `Mcp-Name` checked for `tools/call`.
- `server/discover`, `tools/list`, `tools/call`.
- JSON response by default; optional request-scoped single-message SSE response when Accept includes `text/event-stream`.
- GET returns 405.
- Tool call errors are returned as MCP tool results with `isError=true`; invalid protocol/header requests return HTTP/JSON-RPC errors.

This is not a full implementation of every MCP capability. It does not implement Resources, Prompts, pagination cursors, subscriptions/listen, resumable streams, MRTR, Tasks, or extension negotiation. Test against the exact Agent/client and protocol release used in deployment.


## Protocol smoke test (2026-07-28)

The latest stateless revision does not use `initialize` / `notifications/initialized`.
Each request carries protocol metadata and routing headers.

### Discover server
```bash
curl -i -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Authorization: Bearer YOUR_JWT' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: server/discover' \
  -d '{"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"smoke-test","version":"1.0"},"io.modelcontextprotocol/clientCapabilities":{}}}}'
```

### List tools
```bash
curl -i -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Authorization: Bearer YOUR_JWT' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/list' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"smoke-test","version":"1.0"},"io.modelcontextprotocol/clientCapabilities":{}}}}'
```

### Call a tool
```bash
curl -i -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Authorization: Bearer YOUR_JWT' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/call' \
  -H 'Mcp-Name: query_device' \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"query_device","arguments":{"deviceId":"D001"},"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"smoke-test","version":"1.0"},"io.modelcontextprotocol/clientCapabilities":{}}}}'
```

## Important deployment hardening

- Deploy behind TLS.
- Require authentication and method-level authorization.
- Restrict `scan-base-packages` and `allowed-tool-names`.
- Configure reverse-proxy/servlet request-body size limits. `max-request-bytes` is reserved for a future servlet filter and is not currently enforced by this version.
- Add rate limiting, execution timeouts, concurrency limits, audit retention and alerting.
- The endpoint rejects an invalid supplied Origin. Requests without Origin are allowed because non-browser MCP clients commonly omit it; authentication remains mandatory.
- The starter does not log tool arguments or results by default to reduce sensitive-data exposure.
