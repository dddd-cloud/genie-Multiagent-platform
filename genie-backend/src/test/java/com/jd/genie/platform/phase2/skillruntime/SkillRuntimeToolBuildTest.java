package com.jd.genie.platform.phase2.skillruntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.configuration.skill.binding.mapper.AgentSkillBindingMapper;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
import com.jd.genie.platform.phase2.skillruntime.execution.BrowserSkillExecutionCoordinator;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.*;
import com.jd.genie.platform.phase2contract.dto.*;
import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SkillRuntimeToolBuildTest {
    @TempDir Path temp;
    private final CurrentUser user=new CurrentUser("tenant","owner","owner","Owner",UserRole.USER);

    @Test void buildsStableSafePyodideAndUnavailableNativeTools() throws Exception {
        writePackage(); var service=service(); var profile=profile(List.of(
            entry("run",SkillEntrypointRuntime.pyodide),entry("native",SkillEntrypointRuntime.python)));
        List<BaseTool> first=service.buildRuntimeTools(user,profile,new AgentContext());
        List<BaseTool> second=service.buildRuntimeTools(user,profile,new AgentContext());
        assertEquals(2,first.size()); assertEquals(first.stream().map(BaseTool::getName).toList(),second.stream().map(BaseTool::getName).toList());
        assertTrue(first.stream().allMatch(t->t.getName().matches("skill_[a-f0-9]{24}")));
        Phase2ContractException e=assertThrows(Phase2ContractException.class,()->first.get(1).execute("{}"));
        assertEquals(MvpErrorCode.SKILL_ENTRYPOINT_NOT_AVAILABLE,e.errorCode());
    }

    @Test void duplicateRuntimeNameFailsClosed() throws Exception {
        writePackage(); var service=service(); var skill=runtimeSkill(List.of(entry("run",SkillEntrypointRuntime.pyodide)));
        AgentRuntimeProfile profile=new AgentRuntimeProfile("agent",1,"a","d","p","m",List.of(skill,skill),List.of());
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID,
            assertThrows(Phase2ContractException.class,()->service.buildRuntimeTools(user,profile,new AgentContext())).errorCode());
    }

    private LegacyCompatibleSkillRuntimeService service(){
        SkillPackageHasher h=new SkillPackageHasher(); SkillPackageLoader l=new SkillPackageLoader(temp.toString(),new SkillManifestParser(),new SkillPackageValidator(),h);
        @SuppressWarnings("unchecked") ObjectProvider<ToolBindingPort> p=mock(ObjectProvider.class);
        return new LegacyCompatibleSkillRuntimeService(mock(SkillDefinitionMapper.class),mock(AgentSkillBindingMapper.class),p,l,h,new BrowserSkillExecutionCoordinator(),new ObjectMapper());
    }
    private AgentRuntimeProfile profile(List<SkillEntrypointView> entries) throws Exception { return new AgentRuntimeProfile("agent",1,"a","d","p","m",List.of(runtimeSkill(entries)),List.of()); }
    private AgentRuntimeSkill runtimeSkill(List<SkillEntrypointView> entries) throws Exception {
        SkillPackageLoader l=new SkillPackageLoader(temp.toString(),new SkillManifestParser(),new SkillPackageValidator(),new SkillPackageHasher());
        String hash=l.load(user,"skill").orElseThrow().packageHash();
        return new AgentRuntimeSkill("skill",1,0,"i","o","key","FILESYSTEM","1",hash,List.of(),entries);
    }
    private SkillEntrypointView entry(String name,SkillEntrypointRuntime runtime){return new SkillEntrypointView(name,runtime,"scripts/"+name+".py",name,null,List.of());}
    private void writePackage() throws Exception {
        Path root=temp.resolve("users/tenant/owner/skill"); Files.createDirectories(root.resolve("scripts"));
        Files.writeString(root.resolve("SKILL.md"),"""
            ---
            schemaVersion: 1
            name: test
            description: test
            version: 1
            entrypoints:
              - name: run
                runtime: pyodide
                script: scripts/run.py
              - name: native
                runtime: python
                script: scripts/native.py
            ---

            instructions
            """);
        Files.writeString(root.resolve("scripts/run.py"),"pass"); Files.writeString(root.resolve("scripts/native.py"),"pass");
    }
}
