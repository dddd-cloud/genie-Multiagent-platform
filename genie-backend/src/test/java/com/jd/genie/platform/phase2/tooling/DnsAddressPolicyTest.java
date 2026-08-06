package com.jd.genie.platform.phase2.tooling;
import static org.assertj.core.api.Assertions.assertThat;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
class DnsAddressPolicyTest {
 @Test void blocksPrivateAndLoopback() throws Exception {assertThat(DnsAddressPolicy.isForbidden(InetAddress.getLoopbackAddress())).isTrue();assertThat(DnsAddressPolicy.isForbidden(InetAddress.getByName("192.168.1.1"))).isTrue();}
 @Test void allowsDocumentationPublicAddress() throws Exception {assertThat(DnsAddressPolicy.isForbidden(InetAddress.getByName("203.0.113.1"))).isTrue();}
}
