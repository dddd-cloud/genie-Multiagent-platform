package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class McpUrlPolicy {
    private static final int MAX_LENGTH = 2048;
    private final Environment environment;
    public McpUrlPolicy(Environment environment) { this.environment = environment; }
    public URI validate(String value) { return validate(value, environment.matchesProfiles("test")); }
    public URI validate(String value, boolean testProfile) {
        try {
            if (value == null || value.length() > MAX_LENGTH) throw rejected();
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("https") || (testProfile && scheme.equals("http")))) throw rejected();
            if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getHost() == null || uri.getQuery() != null && uri.getQuery().contains("=")) {
                if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getHost() == null) throw rejected();
            }
            int port = uri.getPort(); if (port != -1 && (port < 1 || port > 65535)) throw rejected();
            String host = uri.getHost();
            if (host.equalsIgnoreCase("localhost") || isNumericAddress(host)) throw rejected();
            for (InetAddress address : InetAddress.getAllByName(host)) if (DnsAddressPolicy.isForbidden(address)) throw rejected();
            return uri;
        } catch (Phase2ContractException ex) { throw ex; }
        catch (Exception ex) { throw rejected(); }
    }
    private boolean isNumericAddress(String host) { return host.matches("[0-9a-fA-FxX:.]+") || host.matches("[0-9]+") || host.matches("0x[0-9a-fA-F]+"); }
    private Phase2ContractException rejected() { return new Phase2ContractException(MvpErrorCode.MCP_URL_REJECTED, "MCP URL rejected"); }
}
