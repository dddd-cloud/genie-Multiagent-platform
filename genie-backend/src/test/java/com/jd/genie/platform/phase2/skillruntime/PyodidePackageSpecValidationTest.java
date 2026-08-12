package com.jd.genie.platform.phase2.skillruntime;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillManifestParser;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PyodidePackageSpecValidationTest {
    @ParameterizedTest
    @ValueSource(strings={"https://evil/x.whl","file:///tmp/x.whl","git+https://evil/repo","user:pass@host/pkg","../x.whl","C:\\x.whl","local.whl"})
    void unsafePackageSpecsFailClosed(String spec) {
        Phase2ContractException e=assertThrows(Phase2ContractException.class,()->new SkillManifestParser().parse(manifest(spec)));
        assertEquals(MvpErrorCode.SKILL_PACKAGE_INVALID,e.errorCode());
    }
    private byte[] manifest(String spec){return ("""
        ---
        schemaVersion: 1
        name: test
        description: test
        version: 1
        entrypoints:
          - name: run
            runtime: pyodide
            script: scripts/run.py
            packages:
              - %s
        ---

        instructions
        """.formatted(spec)).getBytes(StandardCharsets.UTF_8);}
}
