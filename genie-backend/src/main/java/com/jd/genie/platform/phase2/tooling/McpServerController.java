package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.platform.contract.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2")
public class McpServerController {
    private final McpServerService service;
    public McpServerController(McpServerService service) { this.service = service; }
    @GetMapping("/tool-capabilities") public ApiResponse<List<McpToolResponse>> capabilities() { return ok(service.capabilities()); }
    @GetMapping("/mcp-servers") public ApiResponse<List<McpServerResponse>> list() { return ok(service.list()); }
    @PostMapping("/mcp-servers") public ApiResponse<McpServerResponse> create(@RequestBody CreateMcpServerRequest request) { return ok(service.create(request)); }
    @GetMapping("/mcp-servers/{id}") public ApiResponse<McpServerResponse> get(@PathVariable String id) { return ok(service.get(id)); }
    @PutMapping("/mcp-servers/{id}") public ApiResponse<McpServerResponse> update(@PathVariable String id,@RequestBody UpdateMcpServerRequest request) { return ok(service.update(id,request)); }
    @DeleteMapping("/mcp-servers/{id}") public ApiResponse<Void> delete(@PathVariable String id,@RequestParam long version) { service.delete(id,version); return new ApiResponse<>("OK","success",null); }
    @PostMapping("/mcp-servers/{id}/test") public ApiResponse<McpServerResponse> test(@PathVariable String id) { return ok(service.test(id)); }
    @PostMapping("/mcp-servers/{id}/refresh-tools") public ApiResponse<List<McpToolResponse>> refresh(@PathVariable String id) { return ok(service.refreshTools(id)); }
    @PostMapping("/mcp-servers/{id}/enable") public ApiResponse<McpServerResponse> enable(@PathVariable String id,@RequestParam long version) { return ok(service.setStatus(id,version,McpServerStatus.ENABLED)); }
    @PostMapping("/mcp-servers/{id}/disable") public ApiResponse<McpServerResponse> disable(@PathVariable String id,@RequestParam long version) { return ok(service.setStatus(id,version,McpServerStatus.DISABLED)); }
    @GetMapping("/mcp-servers/{id}/tools") public ApiResponse<List<McpToolResponse>> tools(@PathVariable String id) { return ok(service.tools(id)); }
    @PutMapping("/mcp-servers/{id}/tools/{toolId}/enabled") public ApiResponse<McpToolResponse> toolEnabled(@PathVariable String id,@PathVariable String toolId,@RequestBody UpdateToolEnabledRequest request) { return ok(service.setToolEnabled(id,toolId,request)); }
    private <T> ApiResponse<T> ok(T value) { return new ApiResponse<>("OK","success",value); }
}
