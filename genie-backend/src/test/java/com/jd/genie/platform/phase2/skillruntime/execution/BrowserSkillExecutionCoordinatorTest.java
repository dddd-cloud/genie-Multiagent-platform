package com.jd.genie.platform.phase2.skillruntime.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionResult;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;

class BrowserSkillExecutionCoordinatorTest {
    private final CurrentUser owner = user("tenant", "owner");
    private final BrowserSkillExecutionCoordinator coordinator = new BrowserSkillExecutionCoordinator();

    @Test void coordinatorOwnershipAndBundleOwnership() {
        var e = register("old");
        assertSame(e, coordinator.lookupOwned(owner, e.executionId()));
        assertCode(MvpErrorCode.RESOURCE_NOT_FOUND, () -> coordinator.lookupOwned(user("tenant", "other"), e.executionId()));
    }

    @Test void bundleImmutableSnapshotAndExecutionManifest() throws Exception {
        var e = register("old");
        byte[] zip = new BrowserSkillExecutionBundleService(new ObjectMapper()).build(e);
        Map<String,String> contents = unzip(zip);
        assertEquals("old", contents.get("scripts/run.py"));
        assertTrue(contents.get("__joyagent__/execution.json").contains(e.executionId()));
        assertFalse(contents.get("__joyagent__/execution.json").contains("tenant"));
    }

    @Test void bundleTraversalRejected() {
        var bad = new SkillPackageBytesSnapshot("hash", List.of(new SkillPackageHasher.PackageFile("../escape", new byte[0])));
        var e = coordinator.register(owner, "skill", bad, entrypoint(), "{}", 1000);
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID,
            () -> new BrowserSkillExecutionBundleService(new ObjectMapper()).build(e));
    }

    @Test void resultIdempotencyAndConflict() {
        var e=register("code"); var result=result(e.executionId(), "{\"ok\":true}");
        assertEquals(result, coordinator.complete(owner,e.executionId(),result));
        assertEquals(result, coordinator.complete(owner,e.executionId(),result));
        assertCode(MvpErrorCode.VERSION_CONFLICT,
            () -> coordinator.complete(owner,e.executionId(),result(e.executionId(),"{}")));
    }

    @Test void expiredCancelledAndLateResultsRejected() {
        var expired=register("x"); coordinator.expire(expired.executionId());
        assertCode(MvpErrorCode.RESOURCE_NOT_FOUND,
            () -> coordinator.complete(owner,expired.executionId(),result(expired.executionId(),"{}")));
        var cancelled=register("x"); coordinator.cancel(cancelled.executionId()); coordinator.release(cancelled.executionId());
        assertCode(MvpErrorCode.RESOURCE_NOT_FOUND,
            () -> coordinator.complete(owner,cancelled.executionId(),result(cancelled.executionId(),"{}")));
    }

    private BrowserSkillExecutionCoordinator.Execution register(String source) {
        var snapshot=new SkillPackageBytesSnapshot("hash", List.of(
            new SkillPackageHasher.PackageFile("SKILL.md","manifest".getBytes(StandardCharsets.UTF_8)),
            new SkillPackageHasher.PackageFile("scripts/run.py",source.getBytes(StandardCharsets.UTF_8))));
        return coordinator.register(owner,"skill",snapshot,entrypoint(),"{\"input\":1}",1000);
    }
    private SkillEntrypointView entrypoint(){ return new SkillEntrypointView("run",SkillEntrypointRuntime.pyodide,"scripts/run.py","run",null,List.of("numpy>=1.26")); }
    private BrowserSkillExecutionResult result(String id,String output){ return new BrowserSkillExecutionResult(1,id,true,output,"","",null,null); }
    private CurrentUser user(String t,String o){ return new CurrentUser(t,o,o,o,UserRole.USER); }
    private void assertCode(MvpErrorCode code, org.junit.jupiter.api.function.Executable call){ assertEquals(code,assertThrows(Phase2ContractException.class,call).errorCode()); }
    private Map<String,String> unzip(byte[] bytes) throws Exception {
        java.util.LinkedHashMap<String,String> result=new java.util.LinkedHashMap<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ java.util.zip.ZipEntry e; while((e=zip.getNextEntry())!=null) result.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8)); }
        return result;
    }
}
