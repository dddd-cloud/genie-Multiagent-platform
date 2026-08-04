package com.jd.genie.platform.phase2.tooling;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class GenieClientInternalAuthTest { @Test void internalTransportComponentsArePresent(){assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("../genie-client/app/security.py"))).isTrue();} }
