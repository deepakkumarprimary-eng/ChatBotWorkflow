package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.UrlValidationResult;
import net.jqwik.api.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: security-hardening, Property 2: Private IP addresses are always rejected
// Feature: security-hardening, Property 3: Non-HTTP schemes are always rejected
// Feature: security-hardening, Property 4: Allowlist enforcement blocks non-matching hosts

/**
 * Property-based tests for UrlValidator SSRF protection.
 *
 * Property 2: For any IP address within private network ranges, when a URL containing
 * that IP is validated by UrlValidator, the result SHALL be blocked.
 *
 * Property 3: For any URL with a scheme other than http or https, when validated by
 * UrlValidator, the result SHALL be blocked.
 *
 * Property 4: For any configured allowlist and for any host that does NOT match any
 * pattern in the allowlist, when a URL with that host is validated by UrlValidator,
 * the result SHALL be blocked.
 *
 * Validates: Requirements 3.2, 3.3, 3.4
 */
class UrlValidatorPropertyTest {

    // UrlValidator with no allowlist (allows any non-private host)
    private final UrlValidator validatorNoAllowlist = new UrlValidator("");

    // UrlValidator with a configured allowlist
    private final UrlValidator validatorWithAllowlist = new UrlValidator("*.example.com,api.internal");

    // --- Property 2: Private IP addresses are always rejected ---

    /**
     * Property 2: All 10.x.x.x addresses are rejected as private IPs.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 2: Private IP addresses are always rejected")
    void allClass10PrivateIpsAreRejected(
            @ForAll("class10Ips") String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(validatorNoAllowlist.isPrivateIp(address)).isTrue();
    }

    /**
     * Property 2: All 172.16-31.x.x addresses are rejected as private IPs.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 2: Private IP addresses are always rejected")
    void all172_16to31PrivateIpsAreRejected(
            @ForAll("class172Ips") String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(validatorNoAllowlist.isPrivateIp(address)).isTrue();
    }

    /**
     * Property 2: All 192.168.x.x addresses are rejected as private IPs.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 2: Private IP addresses are always rejected")
    void all192_168PrivateIpsAreRejected(
            @ForAll("class192Ips") String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(validatorNoAllowlist.isPrivateIp(address)).isTrue();
    }

    /**
     * Property 2: All 127.x.x.x loopback addresses are rejected as private IPs.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 2: Private IP addresses are always rejected")
    void allLoopbackIpsAreRejected(
            @ForAll("loopbackIps") String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(validatorNoAllowlist.isPrivateIp(address)).isTrue();
    }

    /**
     * Property 2: All 169.254.x.x link-local addresses are rejected as private IPs.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 2: Private IP addresses are always rejected")
    void allLinkLocalIpsAreRejected(
            @ForAll("linkLocalIps") String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(validatorNoAllowlist.isPrivateIp(address)).isTrue();
    }

    // --- Property 3: Non-HTTP schemes are always rejected ---

    /**
     * Property 3: All non-http/https schemes are rejected by isAllowedScheme.
     *
     * Validates: Requirements 3.3
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 3: Non-HTTP schemes are always rejected")
    void allNonHttpSchemesAreRejected(
            @ForAll("nonHttpSchemes") String scheme) {
        assertThat(validatorNoAllowlist.isAllowedScheme(scheme)).isFalse();
    }

    /**
     * Property 3: http and https schemes are always accepted.
     * (Complement property to ensure only valid schemes pass.)
     *
     * Validates: Requirements 3.3
     */
    @Property(tries = 100)
    @Tag("Feature: security-hardening, Property 3: Non-HTTP schemes are always rejected")
    void httpAndHttpsSchemesAreAccepted(
            @ForAll("httpSchemes") String scheme) {
        assertThat(validatorNoAllowlist.isAllowedScheme(scheme)).isTrue();
    }

    // --- Property 4: Allowlist enforcement blocks non-matching hosts ---

    /**
     * Property 4: Hosts that do NOT match the allowlist pattern are blocked.
     * Allowlist is "*.example.com,api.internal".
     * Generated hosts are random UUIDs under .evil.com which will never match.
     *
     * Validates: Requirements 3.4
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 4: Allowlist enforcement blocks non-matching hosts")
    void nonMatchingHostsAreBlockedByAllowlist(
            @ForAll("nonMatchingHosts") String host) {
        assertThat(validatorWithAllowlist.matchesAllowlist(host)).isFalse();
    }

    // --- Arbitraries (Generators) ---

    @Provide
    Arbitrary<String> class10Ips() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255)
        ).as((b, c, d) -> "10." + b + "." + c + "." + d);
    }

    @Provide
    Arbitrary<String> class172Ips() {
        return Combinators.combine(
                Arbitraries.integers().between(16, 31),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255)
        ).as((second, c, d) -> "172." + second + "." + c + "." + d);
    }

    @Provide
    Arbitrary<String> class192Ips() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255)
        ).as((c, d) -> "192.168." + c + "." + d);
    }

    @Provide
    Arbitrary<String> loopbackIps() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255)
        ).as((b, c, d) -> "127." + b + "." + c + "." + d);
    }

    @Provide
    Arbitrary<String> linkLocalIps() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255)
        ).as((c, d) -> "169.254." + c + "." + d);
    }

    @Provide
    Arbitrary<String> nonHttpSchemes() {
        return Arbitraries.of(
                "ftp", "file", "gopher", "javascript", "data",
                "ldap", "ssh", "telnet", "smtp", "imap",
                "pop3", "dns", "tftp", "snmp", "rtsp"
        );
    }

    @Provide
    Arbitrary<String> httpSchemes() {
        return Arbitraries.of("http", "https");
    }

    @Provide
    Arbitrary<String> nonMatchingHosts() {
        // Generate random hostnames that will never match "*.example.com" or "api.internal"
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12),
                Arbitraries.of(".evil.com", ".attacker.org", ".malicious.net",
                        ".hacker.io", ".bad-site.com", ".notexample.com")
        ).as((prefix, suffix) -> prefix.toLowerCase() + suffix);
    }
}
