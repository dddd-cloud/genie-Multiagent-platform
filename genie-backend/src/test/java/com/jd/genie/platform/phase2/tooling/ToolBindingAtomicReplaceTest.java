package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ToolBindingAtomicReplaceTest {
    @Test
    void replaceOperationsUseRequiredTransactions() throws Exception {
        for (String name : new String[]{"replaceAgentBindings", "replaceSkillBindings"}) {
            Method method = ToolBindingPort.class.getMethod(name, com.jd.genie.platform.contract.CurrentUser.class, String.class, java.util.List.class);
            Transactional tx = method.getAnnotation(Transactional.class);
            assertThat(tx).isNotNull();
            assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRED);
        }
    }
}
