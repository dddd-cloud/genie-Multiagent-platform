package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class HttpMcpClientAdapter implements McpClientAdapter {
    private final ObjectMapper mapper; private final McpUrlPolicy urlPolicy;
    public HttpMcpClientAdapter(ObjectMapper mapper, McpUrlPolicy urlPolicy) { this.mapper=mapper; this.urlPolicy=urlPolicy; }
    @Override public List<RemoteTool> listTools(String url, AuthType type, String authName, String credential) {
        JsonNode tools=post(url,type,authName,credential,"tools/list",Map.of()).path("result").path("tools");
        if(!tools.isArray()||tools.size()>200) throw discovery(); List<RemoteTool> out=new ArrayList<>();
        for(JsonNode t:tools){String n=t.path("name").asText(null);JsonNode schema=t.path("inputSchema");if(n==null||n.isBlank()||!schema.isObject()||schema.toString().length()>256*1024)throw discovery();out.add(new RemoteTool(n,t.path("description").asText(""),schema));} return out;
    }
    @Override public JsonNode callTool(String url, AuthType type, String authName, String credential, String name, Map<String,Object> arguments){return post(url,type,authName,credential,"tools/call",Map.of("name",name,"arguments",arguments));}
    private JsonNode post(String raw,AuthType type,String authName,String credential,String method,Map<String,Object> params){
        try { URI uri=urlPolicy.validate(raw); if(type==AuthType.QUERY_PARAM){if(authName==null||authName.isBlank()||credential==null)throw auth();String sep=uri.getQuery()==null?"?":"&";uri=URI.create(uri+sep+URLEncoder.encode(authName,StandardCharsets.UTF_8)+"="+URLEncoder.encode(credential,StandardCharsets.UTF_8));}
            String body=mapper.writeValueAsString(Map.of("jsonrpc","2.0","id",1,"method",method,"params",params)); HttpRequest.Builder b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json"); if(type==AuthType.BEARER_TOKEN)b.header("Authorization","Bearer "+credential);
            var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build().send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==401||response.statusCode()==403)throw auth(); if(response.statusCode()/100!=2||response.body().length()>2*1024*1024)throw unavailable(); return mapper.readTree(response.body());
        } catch(Phase2ContractException e){throw e;} catch(Exception e){throw unavailable();}
    }
    private Phase2ContractException auth(){return new Phase2ContractException(MvpErrorCode.MCP_AUTH_INVALID,"MCP authentication is invalid");}
    private Phase2ContractException unavailable(){return new Phase2ContractException(MvpErrorCode.MCP_UNAVAILABLE,"MCP server unavailable");}
    private Phase2ContractException discovery(){return new Phase2ContractException(MvpErrorCode.MCP_DISCOVERY_INVALID,"MCP discovery response invalid");}
}
