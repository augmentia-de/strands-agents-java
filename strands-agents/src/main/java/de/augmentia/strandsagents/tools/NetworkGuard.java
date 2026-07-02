package de.augmentia.strandsagents.tools;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkGuard {

    private static final Logger log = LoggerFactory.getLogger(NetworkGuard.class);

    private static final List<String> BLOCKED_HOSTS = Arrays.asList(
        "localhost", "127.0.0.1", "0.0.0.0", "[::1]", "::1",
        "169.254.169.254",
        "metadata.google.internal",
        "100.100.100.200"
    );

    private NetworkGuard() {}

    public static void validateUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new SecurityException("URL is required");
        }

        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            throw new SecurityException("Invalid URL: " + e.getMessage());
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new SecurityException("Only http/https URLs are allowed");
        }

        var host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("URL has no host");
        }

        host = host.toLowerCase();

        if (BLOCKED_HOSTS.contains(host)) {
            throw new SecurityException("Access to " + host + " is restricted");
        }

        try {
            var inet = InetAddress.getByName(host);
            if (inet.isLoopbackAddress()) {
                throw new SecurityException("Access to loopback address is restricted: " + host);
            }
            if (inet.isLinkLocalAddress()) {
                throw new SecurityException("Access to link-local address is restricted: " + host);
            }
            if (inet.isSiteLocalAddress()) {
                throw new SecurityException("Access to private network address is restricted: " + host);
            }
        } catch (UnknownHostException e) {
            throw new SecurityException("Cannot resolve host: " + host);
        }
    }
}
