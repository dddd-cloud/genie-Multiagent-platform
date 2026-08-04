package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class McpServerService {
    private final JdbcTemplate jdbc; private final CurrentUserProvider currentUser; private final CredentialEnvelopeService credentials;
    private final ObjectMapper objectMapper; private final Clock clock;
    public McpServerService(JdbcTemplate jdbc, CurrentUserProvider currentUser, CredentialEnvelopeService credentials, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc; this.currentUser = currentUser; this.credentials = credentials; this.objectMapper = objectMapper; this.clock = clock;
    }
    public List<McpServerResponse> list() { var u=currentUser.requireCurrentUser(); return jdbc.query("SELECT * FROM mcp_server WHERE tenant_id=? AND owner_id=? AND deleted_at IS NULL ORDER BY created_at DESC", this::server, u.tenantId(), u.userId()); }
    public McpServerResponse get(String id) { var u=currentUser.requireCurrentUser(); return one(u.tenantId(),u.userId(),id); }
    @Transactional public McpServerResponse create(CreateMcpServerRequest req) {
        validate(req); var u=currentUser.requireCurrentUser(); String id=UUID.randomUUID().toString(); LocalDateTime now=LocalDateTime.now(clock);
        String envelope=credentials.encrypt(req.credential(),u.tenantId(),u.userId(),id,req.authType());
        try { jdbc.update("INSERT INTO mcp_server(id,tenant_id,owner_id,name,server_url,auth_type,auth_name,credential_envelope,status,version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,'DRAFT',0,?,?)", id,u.tenantId(),u.userId(),req.name().trim(),req.serverUrl().trim(),req.authType().name(),blankToNull(req.authName()),envelope,now,now); }
        catch (DataAccessException ex) { throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR,"MCP server cannot be saved",ex); }
        return one(u.tenantId(),u.userId(),id);
    }
    @Transactional public McpServerResponse update(String id, UpdateMcpServerRequest req) {
        if(req==null || req.version()<0) throw validation(); validate(req.name(),req.serverUrl(),req.authType(),req.authName()); var u=currentUser.requireCurrentUser(); var existing=raw(u.tenantId(),u.userId(),id); LocalDateTime now=LocalDateTime.now(clock);
        String envelope=existing.credentialEnvelope; if(Boolean.TRUE.equals(req.clearCredential())) envelope=null; else if(req.credential()!=null && !req.credential().isBlank()) envelope=credentials.encrypt(req.credential(),u.tenantId(),u.userId(),id,req.authType());
        int changed=jdbc.update("UPDATE mcp_server SET name=?,server_url=?,auth_type=?,auth_name=?,credential_envelope=?,updated_at=?,version=version+1 WHERE id=? AND tenant_id=? AND owner_id=? AND deleted_at IS NULL AND version=?",req.name().trim(),req.serverUrl().trim(),req.authType().name(),blankToNull(req.authName()),envelope,now,id,u.tenantId(),u.userId(),req.version());
        if(changed!=1) throw new Phase2ContractException(MvpErrorCode.VERSION_CONFLICT,"MCP server version conflict"); return one(u.tenantId(),u.userId(),id);
    }
    @Transactional public void delete(String id,long version) { var u=currentUser.requireCurrentUser(); raw(u.tenantId(),u.userId(),id); LocalDateTime now=LocalDateTime.now(clock); int changed=jdbc.update("UPDATE mcp_server SET status='DISABLED',deleted_at=?,updated_at=?,version=version+1 WHERE id=? AND tenant_id=? AND owner_id=? AND deleted_at IS NULL AND version=?",now,now,id,u.tenantId(),u.userId(),version); if(changed!=1) throw new Phase2ContractException(MvpErrorCode.VERSION_CONFLICT,"MCP server version conflict"); jdbc.update("UPDATE mcp_tool SET available=FALSE,updated_at=?,version=version+1 WHERE mcp_server_id=? AND tenant_id=? AND owner_id=?",now,id,u.tenantId(),u.userId()); }
    @Transactional public McpServerResponse setStatus(String id,long version,McpServerStatus status) { var u=currentUser.requireCurrentUser(); var s=raw(u.tenantId(),u.userId(),id); if(status==McpServerStatus.ENABLED){long available=jdbc.queryForObject("SELECT COUNT(*) FROM mcp_tool WHERE mcp_server_id=? AND tenant_id=? AND owner_id=? AND available=TRUE",Long.class,id,u.tenantId(),u.userId()); if(!"SUCCESS".equals(s.lastCheckStatus) || available<1) throw new Phase2ContractException(MvpErrorCode.MCP_UNAVAILABLE,"MCP server has not passed a tool check"); } LocalDateTime now=LocalDateTime.now(clock); int changed=jdbc.update("UPDATE mcp_server SET status=?,updated_at=?,version=version+1 WHERE id=? AND tenant_id=? AND owner_id=? AND deleted_at IS NULL AND version=?",status.name(),now,id,u.tenantId(),u.userId(),version); if(changed!=1) throw new Phase2ContractException(MvpErrorCode.VERSION_CONFLICT,"MCP server version conflict"); return one(u.tenantId(),u.userId(),id); }
    @Transactional public McpServerResponse test(String id) { var u=currentUser.requireCurrentUser(); one(u.tenantId(),u.userId(),id); LocalDateTime now=LocalDateTime.now(clock); jdbc.update("UPDATE mcp_server SET last_check_status='FAILED',last_check_code='MCP_UNAVAILABLE',last_checked_at=?,updated_at=? WHERE id=? AND tenant_id=? AND owner_id=? AND deleted_at IS NULL",now,now,id,u.tenantId(),u.userId()); throw new Phase2ContractException(MvpErrorCode.MCP_UNAVAILABLE,"MCP server is unavailable"); }
    public List<McpToolResponse> tools(String id) { var u=currentUser.requireCurrentUser(); one(u.tenantId(),u.userId(),id); return jdbc.query("SELECT id,tool_name,runtime_name,description,input_schema,enabled,available,version FROM mcp_tool WHERE mcp_server_id=? AND tenant_id=? AND owner_id=? ORDER BY tool_name",(rs,n)->tool(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),parse(rs.getString(5)),rs.getBoolean(6),rs.getBoolean(7),rs.getLong(8)),id,u.tenantId(),u.userId()); }
    public List<McpToolResponse> capabilities() { var u=currentUser.requireCurrentUser(); return jdbc.query("SELECT id,tool_name,runtime_name,description,input_schema,enabled,available,version FROM mcp_tool WHERE tenant_id=? AND owner_id=? AND enabled=TRUE AND available=TRUE ORDER BY runtime_name",(rs,n)->tool(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),parse(rs.getString(5)),rs.getBoolean(6),rs.getBoolean(7),rs.getLong(8)),u.tenantId(),u.userId()); }
    @Transactional public McpToolResponse setToolEnabled(String serverId,String toolId,UpdateToolEnabledRequest req){if(req==null||req.enabled()==null)throw validation();var u=currentUser.requireCurrentUser();one(u.tenantId(),u.userId(),serverId);int c=jdbc.update("UPDATE mcp_tool SET enabled=?,updated_at=?,version=version+1 WHERE id=? AND mcp_server_id=? AND tenant_id=? AND owner_id=? AND version=?",req.enabled(),LocalDateTime.now(clock),toolId,serverId,u.tenantId(),u.userId(),req.version());if(c!=1)throw new Phase2ContractException(MvpErrorCode.VERSION_CONFLICT,"Tool version conflict");return tools(serverId).stream().filter(t->t.id().equals(toolId)).findFirst().orElseThrow(()->notFound());}
    private McpServerResponse one(String tenant,String owner,String id){try{return jdbc.queryForObject("SELECT * FROM mcp_server WHERE id=? AND tenant_id=? AND owner_id=? AND deleted_at IS NULL",this::server,id,tenant,owner);}catch(Exception e){throw notFound();}}
    private Raw raw(String tenant,String owner,String id){try{return jdbc.queryForObject("SELECT credential_envelope,last_check_status FROM mcp_server WHERE id=? AND tenant_id=? AND owner_id=? AND deleted_at IS NULL",(rs,n)->new Raw(rs.getString(1),rs.getString(2)),id,tenant,owner);}catch(Exception e){throw notFound();}}
    private McpServerResponse server(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new McpServerResponse(rs.getString("id"),rs.getString("name"),rs.getString("server_url"),AuthType.valueOf(rs.getString("auth_type")),rs.getString("auth_name"),McpServerStatus.valueOf(rs.getString("status")),rs.getString("credential_envelope")!=null,rs.getString("last_check_status"),rs.getString("last_check_code"),rs.getTimestamp("last_checked_at")==null?null:rs.getTimestamp("last_checked_at").toLocalDateTime(),rs.getLong("version"),rs.getTimestamp("created_at").toLocalDateTime(),rs.getTimestamp("updated_at").toLocalDateTime());}
    private McpToolResponse tool(String id,String name,String runtime,String desc,JsonNode schema,boolean enabled,boolean available,long version){return new McpToolResponse(id,name,runtime,desc,schema,enabled,available,version);}
    private JsonNode parse(String s){try{return objectMapper.readTree(s);}catch(Exception e){return objectMapper.createObjectNode();}}
    private void validate(CreateMcpServerRequest r){if(r==null)throw validation();validate(r.name(),r.serverUrl(),r.authType(),r.authName());}
    private void validate(String name,String url,AuthType type,String authName){if(name==null||name.isBlank()||name.length()>128||url==null||url.isBlank()||url.length()>2048||type==null)throw validation();if(type==AuthType.QUERY_PARAM&&(authName==null||authName.isBlank()))throw validation();if(type==AuthType.NONE&&authName!=null&&!authName.isBlank())throw validation();}
    private String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();} private Phase2ContractException validation(){return new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR,"Invalid MCP server request");} private Phase2ContractException notFound(){return new Phase2ContractException(MvpErrorCode.RESOURCE_NOT_FOUND,"Resource not found");}
    private record Raw(String credentialEnvelope,String lastCheckStatus){}
}
