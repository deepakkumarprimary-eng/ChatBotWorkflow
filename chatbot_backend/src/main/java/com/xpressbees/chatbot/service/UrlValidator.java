package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.UrlValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Validates outbound URLs against private IP ranges and an optional host allowlist
 * to prevent Server-Side Request Forgery (SSRF) attacks.
 */
@Component
public class UrlValidator {

    private final List<String> allowedHostPatterns;

    public UrlValidator(@Value("${ssrf.allowed-hosts:}") String allowedHostsConfig) {
        if (allowedHostsConfig == null || allowedHostsConfig.isBlank()) {
            this.allowedHostPatterns = Collections.emptyList();
        } else {
            this.allowedHostPatterns = Arrays.stream(allowedHostsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    /**
     * Validates a URL for SSRF protection.
     *
     * @param url the resolved URL string to validate
     * @return UrlValidationResult indicating whether the URL is allowed or blocked
     */
    public UrlValidationResult validate(String url) {
        // 1. Parse URL, reject if malformed
        URL parsedUrl;
        try {
            parsedUrl = new URL(url);
        } catch (MalformedURLException e) {
            return UrlValidationResult.blocked("Malformed URL: " + e.getMessage());
        }

        // 2. Check scheme is http or https
        String scheme = parsedUrl.getProtocol();
        if (!isAllowedScheme(scheme)) {
            return UrlValidationResult.blocked("Only http and https schemes are allowed");
        }

        // 3. If allowed hosts configured, check host matches allowlist
        String host = parsedUrl.getHost();
        if (!allowedHostPatterns.isEmpty() && !matchesAllowlist(host)) {
            return UrlValidationResult.blocked("Host not in allowed hosts list");
        }

        // 4. Resolve hostname via DNS
        InetAddress resolvedAddress;
        try {
            resolvedAddress = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return UrlValidationResult.blocked("DNS resolution failed for host: " + host);
        }

        // 5. Check resolved IP is not private (DNS rebinding protection)
        if (isPrivateIp(resolvedAddress)) {
            return UrlValidationResult.blocked("URL resolves to private network address");
        }

        // 6. URL passed all checks
        return UrlValidationResult.allowed();
    }

    /**
     * Checks if the given address is in a private/reserved IP range.
     * Covers: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8,
     * 169.254.0.0/16, and IPv6 loopback (::1).
     */
    boolean isPrivateIp(InetAddress address) {
        byte[] bytes = address.getAddress();

        // IPv6 loopback (::1)
        if (bytes.length == 16) {
            return isIpv6Loopback(bytes);
        }

        // IPv4 checks
        if (bytes.length == 4) {
            int firstOctet = bytes[0] & 0xFF;
            int secondOctet = bytes[1] & 0xFF;

            // 127.0.0.0/8 (loopback)
            if (firstOctet == 127) {
                return true;
            }

            // 10.0.0.0/8 (private)
            if (firstOctet == 10) {
                return true;
            }

            // 172.16.0.0/12 (private) - 172.16.x.x to 172.31.x.x
            if (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31) {
                return true;
            }

            // 192.168.0.0/16 (private)
            if (firstOctet == 192 && secondOctet == 168) {
                return true;
            }

            // 169.254.0.0/16 (link-local)
            if (firstOctet == 169 && secondOctet == 254) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if only http and https schemes are permitted.
     */
    boolean isAllowedScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    /**
     * Checks if the given host matches any pattern in the allowlist.
     * Supports wildcard patterns (e.g., *.example.com matches sub.example.com).
     */
    boolean matchesAllowlist(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }

        String lowerHost = host.toLowerCase();

        for (String pattern : allowedHostPatterns) {
            String lowerPattern = pattern.toLowerCase();

            if (lowerPattern.startsWith("*.")) {
                // Wildcard pattern: *.example.com matches sub.example.com and deep.sub.example.com
                String suffix = lowerPattern.substring(1); // ".example.com"
                if (lowerHost.endsWith(suffix) || lowerHost.equals(lowerPattern.substring(2))) {
                    return true;
                }
            } else {
                // Exact match
                if (lowerHost.equals(lowerPattern)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isIpv6Loopback(byte[] bytes) {
        // ::1 is all zeros except the last byte which is 1
        for (int i = 0; i < 15; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[15] == 1;
    }
}
