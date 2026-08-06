package com.jd.genie.platform.phase2.tooling;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

public final class DnsAddressPolicy {
    private DnsAddressPolicy() { }
    public static boolean isForbidden(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) return true;
        if (address instanceof Inet4Address) {
            byte[] b = address.getAddress(); int a=b[0]&255, c=b[1]&255;
            return a == 10 || a == 127 || (a == 172 && c >= 16 && c <= 31) || (a == 192 && c == 168) || (a == 169 && c == 254) || (a == 100 && c >= 64 && c <= 127) || (a == 198 && (c == 18 || c == 19 || c == 51)) || (a == 203 && c == 0) || (a == 192 && (c == 0 || c == 2));
        }
        if (address instanceof Inet6Address) {
            byte[] b=address.getAddress(); int first=(b[0]&255), second=(b[1]&255);
            return (first & 0xfe) == 0xfc || (first == 0xfe && (second & 0xc0) == 0x80) || address.isSiteLocalAddress();
        }
        return address.isSiteLocalAddress();
    }
}
