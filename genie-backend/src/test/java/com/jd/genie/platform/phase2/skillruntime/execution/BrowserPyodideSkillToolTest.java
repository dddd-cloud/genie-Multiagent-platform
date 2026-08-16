package com.jd.genie.platform.phase2.skillruntime.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2contract.BrowserSkillExecutionContract;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionResult;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionSignal;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BrowserPyodideSkillToolTest {
    @Test void signalContainsFrozenSafeFieldsAndFutureReturnsOutput() {
        BrowserSkillExecutionCoordinator c=new BrowserSkillExecutionCoordinator(); Printer printer=mock(Printer.class);
        AtomicReference<BrowserSkillExecutionSignal> signal=new AtomicReference<>();
        doAnswer(i->{ var s=(BrowserSkillExecutionSignal)i.getArgument(2); signal.set(s);
            c.complete(user(),s.executionId(),new BrowserSkillExecutionResult(1,s.executionId(),true,"{\"ok\":true}","","",null,null)); return null;
        }).when(printer).send(isNull(),eq(BrowserSkillExecutionContract.PRINTER_MESSAGE_TYPE),any(),isNull(),eq(false));
        Object out=tool(c,printer,1000).execute("{\"secret\":\"not-in-signal\"}");
        assertEquals("{\"ok\":true}",out); assertEquals("skill",signal.get().skillId()); assertEquals("hash",signal.get().packageHash());
        assertFalse(signal.get().toString().contains("secret")); assertEquals(0,c.pendingCount());
    }
    @Test void generatedHtmlIsUploadedAndOmittedFromAgentVisibleJson() {
        BrowserSkillExecutionCoordinator c=new BrowserSkillExecutionCoordinator(); Printer printer=mock(Printer.class);
        BaseTool fileTool=mock(BaseTool.class);
        when(fileTool.getName()).thenReturn("file_tool");
        when(fileTool.execute(any())).thenReturn("algorithmic-art.html uploaded");
        ToolCollection tools=new ToolCollection();
        tools.addTool(fileTool);
        doAnswer(i->{ var s=(BrowserSkillExecutionSignal)i.getArgument(2);
            c.complete(user(),s.executionId(),new BrowserSkillExecutionResult(1,s.executionId(),true,
                "{\"ok\":true,\"token\":\"JOY-SKILL-RAN-1\",\"filename\":\"algorithmic-art.html\",\"html\":\"<html>big</html>\"}","","",null,null)); return null;
        }).when(printer).send(isNull(),eq(BrowserSkillExecutionContract.PRINTER_MESSAGE_TYPE),any(),isNull(),eq(false));
        Object out=tool(c,printer,1000,tools).execute("{}");
        assertTrue(String.valueOf(out).contains("\"token\":\"JOY-SKILL-RAN-1\""));
        assertTrue(String.valueOf(out).contains("\"previewFile\":\"algorithmic-art.html\""));
        assertTrue(String.valueOf(out).contains("\"uploaded\":true"));
        assertFalse(String.valueOf(out).contains("<html>"));
        verify(fileTool).execute(argThat(input -> input instanceof Map<?,?> map
            && "upload".equals(map.get("command"))
            && "algorithmic-art.html".equals(map.get("filename"))
            && "<html>big</html>".equals(map.get("content"))));
    }
    @Test void noCompatiblePrinterFailsFast() {
        BrowserSkillExecutionCoordinator c=new BrowserSkillExecutionCoordinator();
        Phase2ContractException e=assertThrows(Phase2ContractException.class,()->tool(c,null,1000).execute("{}"));
        assertEquals(MvpErrorCode.TOOL_NOT_BOUND,e.errorCode()); assertEquals(0,c.pendingCount());
    }
    @Test void timeoutCleansAndLateResultRejected() {
        BrowserSkillExecutionCoordinator c=new BrowserSkillExecutionCoordinator(); Printer p=mock(Printer.class);
        AtomicReference<String> id=new AtomicReference<>(); doAnswer(i->{id.set(((BrowserSkillExecutionSignal)i.getArgument(2)).executionId());return null;})
            .when(p).send(isNull(),anyString(),any(),isNull(),eq(false));
        Phase2ContractException e=assertThrows(Phase2ContractException.class,()->tool(c,p,10).execute("{}"));
        assertEquals(MvpErrorCode.TOOL_TIMEOUT,e.errorCode()); assertEquals(0,c.pendingCount());
        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND,assertThrows(Phase2ContractException.class,()->c.complete(user(),id.get(),new BrowserSkillExecutionResult(1,id.get(),true,"{}","","",null,null))).errorCode());
    }
    @Test void interruptedWaitCancelsAndCleans() throws Exception {
        BrowserSkillExecutionCoordinator c=new BrowserSkillExecutionCoordinator(); Printer p=mock(Printer.class);
        Thread worker=new Thread(()->assertThrows(Phase2ContractException.class,()->tool(c,p,10_000).execute("{}")));
        worker.start(); for(int i=0;i<100 && c.pendingCount()==0;i++) Thread.sleep(5); worker.interrupt(); worker.join(2000);
        assertFalse(worker.isAlive()); assertEquals(0,c.pendingCount());
    }
    private BrowserPyodideSkillTool tool(BrowserSkillExecutionCoordinator c,Printer p,long timeout){
        return tool(c,p,timeout,null);
    }
    private BrowserPyodideSkillTool tool(BrowserSkillExecutionCoordinator c,Printer p,long timeout,ToolCollection tools){
        AgentContext context=AgentContext.builder().printer(p).toolCollection(tools).build();
        return new BrowserPyodideSkillTool("skill_test","skill",user(),context,new SkillPackageBytesSnapshot("hash",List.of()),
            new SkillEntrypointView("run",SkillEntrypointRuntime.pyodide,"scripts/run.py","run",null,List.of()),c,new ObjectMapper(),timeout);
    }
    private CurrentUser user(){return new CurrentUser("tenant","owner","owner","Owner",UserRole.USER);}
}
