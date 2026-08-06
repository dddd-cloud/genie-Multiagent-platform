package com.jd.genie.platform.phase2.tooling;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class GenieClientRedactionTest { @Test void clientSourcesDoNotLogRawRequestSecrets(){try{String s=java.nio.file.Files.readString(java.nio.file.Path.of("../genie-client/app/header.py"));assertThat(s).doesNotContain("cookies={self.cookies}");}catch(Exception e){throw new AssertionError(e);}} }
