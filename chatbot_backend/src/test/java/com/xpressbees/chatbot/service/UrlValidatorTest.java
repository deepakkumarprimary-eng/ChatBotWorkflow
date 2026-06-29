package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.UrlValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UrlValidator SSRF protection.
 * Tests specific examples, edge cases, and error handling.
 *
 * Validates: Requirements 3.2, 3.4, 3.6
 */
class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator("");

    // --- Test 1: Specific private IPs blocked ---

    @Nested
    @DisplayName("Private IP blocking")
    class PrivateIpBlocking {

        @Test
        @DisplayName("10.0.0.1 is blocked as private IP")
        void blocks10_0_0_1() throws UnknownHostException {
            InetAddress address = InetAddress.getByName("10.0.0.1");
            assertThat(validator.isPrivateIp(address)).isTrue();
        }

        @Test
        @DisplayName("172.16.0.1 is blocked as private IP")
        void blocks172_16_0_1() throws UnknownHostException {
            InetAddress address = InetAddress.getByName("172.16.0.1");
            assertThat(validator.isPrivateIp(address)).isTrue();
        }

        @Test
        @DisplayName("192.168.1.1 is blocked as private IP")
        void blocks192_168_1_1() throws UnknownHostException {
            InetAddress address = InetAddress.getByName("192.168.1.1");
            assertThat(validator.isPrivateIp(address)).isTrue();
        }

        @Test
        @DisplayName("127.0.0.1 is blocked as loopback")
        void blocks127_0_0_1() throws UnknownHostException {
            InetAddress address = InetAddress.getByName("127.0.0.1");
            assertThat(validator.isPrivateIp(address)).isTrue();
        }

        @Test
        @DisplayName("169.254.1.1 is blocked as link-local")
        void blocks169_254_1_1() throws UnknownHostException {
            InetAddress address = InetAddress.getByName("169.254.1.1");
            assertThat(validator.isPrivateIp(address)).isTrue();
        }

        @Test
        @DisplayName("Full validate() blocks URL with private IP 10.0.0.1")
        void validateBlocksPrivateIpUrl() {
            UrlValidationResult result = validator.validate("http://10.0.0.1/path");
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.reason()).contains("private network");
        }

        @Test
        @DisplayName("Full validate() blocks URL with loopback 127.0.0.1")
        void validateBlocksLoopbackUrl() {
            UrlValidationResult result = validator.validate("http://127.0.0.1/api");
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.reason()).contains("private network");
        }
    }

    // --- Test 2: Valid public URLs allowed ---

    @Nested
    @DisplayName("Public URLs allowed")
    class PublicUrlsAllowed {

        @Test
        @DisplayName("Public IP 8.8.8.8 is allowed")
        void allowsPublicIp() {
            UrlValidationResult result = validator.validate("http://8.8.8.8/path");
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("Public IP 1.1.1.1 is allowed")
        void allowsCloudflareIp() {
            UrlValidationResult result = validator.validate("https://1.1.1.1/dns-query");
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("isPrivateIp returns false for public IP 8.8.8.8")
        void publicIpNotPrivate() throws UnknownHostException {
            InetAddress address = InetAddress.getByName("8.8.8.8");
            assertThat(validator.isPrivateIp(address)).isFalse();
        }
    }

    // --- Test 3: Non-http schemes blocked ---

    @Nested
    @DisplayName("Non-HTTP schemes blocked")
    class NonHttpSchemesBlocked {

        @Test
        @DisplayName("ftp scheme is blocked")
        void blocksFtpScheme() {
            assertThat(validator.isAllowedScheme("ftp")).isFalse();
        }

        @Test
        @DisplayName("file scheme is blocked")
        void blocksFileScheme() {
            assertThat(validator.isAllowedScheme("file")).isFalse();
        }

        @Test
        @DisplayName("Full validate() blocks ftp://example.com")
        void validateBlocksFtpUrl() {
            UrlValidationResult result = validator.validate("ftp://example.com/file.txt");
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.reason()).contains("Only http and https schemes are allowed");
        }

        @Test
        @DisplayName("Full validate() blocks file:///etc/passwd")
        void validateBlocksFileUrl() {
            UrlValidationResult result = validator.validate("file:///etc/passwd");
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.reason()).contains("Only http and https schemes are allowed");
        }

        @Test
        @DisplayName("http scheme is allowed")
        void allowsHttp() {
            assertThat(validator.isAllowedScheme("http")).isTrue();
        }

        @Test
        @DisplayName("https scheme is allowed")
        void allowsHttps() {
            assertThat(validator.isAllowedScheme("https")).isTrue();
        }
    }

    // --- Test 4: Allowlist matching ---

    @Nested
    @DisplayName("Allowlist matching")
    class AllowlistMatching {

        private final UrlValidator allowlistValidator = new UrlValidator("*.example.com,api.internal");

        @Test
        @DisplayName("sub.example.com matches wildcard *.example.com")
        void wildcardMatchesSubdomain() {
            assertThat(allowlistValidator.matchesAllowlist("sub.example.com")).isTrue();
        }

        @Test
        @DisplayName("deep.sub.example.com matches wildcard *.example.com")
        void wildcardMatchesDeepSubdomain() {
            assertThat(allowlistValidator.matchesAllowlist("deep.sub.example.com")).isTrue();
        }

        @Test
        @DisplayName("api.internal matches exact pattern")
        void exactMatchWorks() {
            assertThat(allowlistValidator.matchesAllowlist("api.internal")).isTrue();
        }

        @Test
        @DisplayName("evil.com does NOT match the allowlist")
        void evilComNotAllowed() {
            assertThat(allowlistValidator.matchesAllowlist("evil.com")).isFalse();
        }

        @Test
        @DisplayName("notexample.com does NOT match *.example.com")
        void notexampleComNotAllowed() {
            assertThat(allowlistValidator.matchesAllowlist("notexample.com")).isFalse();
        }

        @Test
        @DisplayName("example.com matches wildcard *.example.com (bare domain)")
        void bareDomainMatchesWildcard() {
            assertThat(allowlistValidator.matchesAllowlist("example.com")).isTrue();
        }

        @Test
        @DisplayName("Allowlist matching is case-insensitive")
        void caseInsensitiveMatching() {
            assertThat(allowlistValidator.matchesAllowlist("SUB.EXAMPLE.COM")).isTrue();
            assertThat(allowlistValidator.matchesAllowlist("API.INTERNAL")).isTrue();
        }

        @Test
        @DisplayName("Empty host does not match")
        void emptyHostDoesNotMatch() {
            assertThat(allowlistValidator.matchesAllowlist("")).isFalse();
        }

        @Test
        @DisplayName("Null host does not match")
        void nullHostDoesNotMatch() {
            assertThat(allowlistValidator.matchesAllowlist(null)).isFalse();
        }
    }

    // --- Test 5: DNS resolution failure ---

    @Nested
    @DisplayName("DNS resolution failure")
    class DnsResolutionFailure {

        @Test
        @DisplayName("Non-existent host results in blocked with DNS failure reason")
        void nonExistentHostIsBlocked() {
            UrlValidationResult result = validator.validate("http://this-host-definitely-does-not-exist-xyz123.invalid/path");
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.reason()).contains("DNS resolution failed");
        }
    }

    // --- Test 6: Malformed URL ---

    @Nested
    @DisplayName("Malformed URL handling")
    class MalformedUrlHandling {

        @Test
        @DisplayName("Completely invalid URL is blocked")
        void malformedUrlBlocked() {
            UrlValidationResult result = validator.validate("not a url");
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.reason()).contains("Malformed URL");
        }

        @Test
        @DisplayName("Empty string is blocked as malformed")
        void emptyStringBlocked() {
            UrlValidationResult result = validator.validate("");
            assertThat(result.isAllowed()).isFalse();
        }
    }

    // --- Test 7: IPv6 loopback ---

    @Nested
    @DisplayName("IPv6 loopback blocking")
    class Ipv6LoopbackBlocking {

        @Test
        @DisplayName("IPv6 loopback ::1 is detected as private IP")
        void ipv6LoopbackIsPrivate() throws UnknownHostException {
            InetAddress address = InetAddress.getByName("::1");
            assertThat(validator.isPrivateIp(address)).isTrue();
        }

        @Test
        @DisplayName("Full validate() blocks URL with IPv6 loopback [::1]")
        void validateBlocksIpv6LoopbackUrl() {
            UrlValidationResult result = validator.validate("http://[::1]/path");
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.reason()).contains("private network");
        }
    }
}
