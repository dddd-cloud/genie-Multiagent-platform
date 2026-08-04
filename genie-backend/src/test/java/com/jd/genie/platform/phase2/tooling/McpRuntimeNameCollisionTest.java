package com.jd.genie.platform.phase2.tooling;
import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
class McpRuntimeNameCollisionTest {
 @Test void collidingNamesGetStableSuffix(){var m= new McpServerService(null,null,null,null,java.time.Clock.systemUTC(),null,null);try{Method x=McpServerService.class.getDeclaredMethod("runtimeName",String.class,String.class,java.util.List.class);x.setAccessible(true);var used=new ArrayList<String>();String a=(String)x.invoke(m,"server","a-b",used);String b=(String)x.invoke(m,"server","a_b",used);assertThat(a).isNotEqualTo(b);assertThat(b).startsWith("mcp_");}catch(Exception e){throw new AssertionError(e);}}
}
