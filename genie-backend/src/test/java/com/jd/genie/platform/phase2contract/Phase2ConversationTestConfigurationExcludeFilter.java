package com.jd.genie.platform.phase2contract;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.IOException;
import java.util.Set;

final class Phase2ConversationTestConfigurationExcludeFilter extends TypeExcludeFilter {
    private static final Set<String> EXCLUDED_CLASS_NAMES = Set.of(
        "com.jd.genie.platform.phase2contract.Phase2ContractIntegrationTestApplication",
        "com.jd.genie.platform.security.Phase3IntegrationTestApplication",
        "com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport$TestApplication",
        "com.jd.genie.platform.conversation.ConversationCrudApiTest$TestConfig",
        "com.jd.genie.platform.conversation.ConversationExecutionServiceTest$TestConfig",
        "com.jd.genie.platform.conversation.ConversationHistoryServiceTest$TestConfig",
        "com.jd.genie.platform.conversation.ConversationMessageStateMachineTest$TestConfig",
        "com.jd.genie.platform.conversation.ConversationPersistenceFoundationTest$TestConfig",
        "com.jd.genie.platform.conversation.ConversationRecoveryServiceTest$TestConfig",
        "com.jd.genie.platform.conversation.ConversationTitleServiceTest$TestConfig"
    );

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
        String className = metadataReader.getClassMetadata().getClassName();
        return EXCLUDED_CLASS_NAMES.contains(className);
    }

    @Override
    public boolean equals(Object other) {
        return other != null && getClass() == other.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
