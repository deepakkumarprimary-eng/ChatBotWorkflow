package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.UrlValidationResult;
import net.jqwik.api.*;

/**
 * Extended property-based tests for UrlValidator covering:
 * - Property 15: Private IP URL Rejection via Full Validation
 * - Property 16: HTTP/HTTPS Scheme-Agnostic Acceptance
 * - Property 17: Non-HTTP Scheme Rejection
 *
 * Validates: Requirements 9.1, 9.2, 9.3
 */
class UrlValidatorExtendedPropertyTest {

    private final UrlValidator urlValidator = new UrlValidator("");

    // ========================================================================
    // Property 15: Private IP URL Rejection via Full Validation
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 15: Private IP URL Rejection via Full Validation")
    void privateIpUrlsAreBlocked(@ForAll("privateIpUrls") String url) {
        // Validates: Requirements 9.1
        UrlValidationResult result = urlValidator.validate(url);

        assert !result.isAllowed() : "URL with private IP should be blocked. URL: " + url;
    }

    // ========================================================================
    // Property 16: HTTP/HTTPS Scheme-Agnostic Acceptance
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 16: HTTP/HTTPS Scheme-Agnostic Acceptance")
    void httpAndHttpsProduceSameAcceptanceResult(@ForAll("publicHosts") String host,
                                                 @ForAll("urlPaths") String path) {
        // Validates: Requirements 9.2
        String httpUrl = "http://" + host + path;
        String httpsUrl = "https://" + host + path;

        UrlValidationResult httpResult = urlValidator.validate(httpUrl);
        UrlValidationResult httpsResult = urlValidator.validate(httpsUrl);

        assert httpResult.isAllowed() == httpsResult.isAllowed() :
                "HTTP and HTTPS should produce same acceptance result. " +
                "HTTP allowed=" + httpResult.isAllowed() + ", HTTPS allowed=" + httpsResult.isAllowed() +
                ", Host=" + host;
    }

    // ========================================================================
    // Property 17: Non-HTTP Scheme Rejection
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 17: Non-HTTP Scheme Rejection")
    void nonHttpSchemesAreBlocked(@ForAll("nonHttpSchemeUrls") String url) {
        // Validates: Requirements 9.3
        UrlValidationResult result = urlValidator.validate(url);

        assert !result.isAllowed() : "URL with non-HTTP scheme should be blocked. URL: " + url;
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<String> privateIpUrls() {
        Arbitrary<String> privateHosts = Arbitraries.oneOf(
                // 10.x.x.x range
                Arbitraries.integers().between(0, 255).flatMap(b ->
                        Arbitraries.integers().between(0, 255).flatMap(c ->
                                Arbitraries.integers().between(0, 255).map(d ->
                                        "10." + b + "." + c + "." + d))),
                // 172.16-31.x.x range
                Arbitraries.integers().between(16, 31).flatMap(b ->
                        Arbitraries.integers().between(0, 255).flatMap(c ->
                                Arbitraries.integers().between(0, 255).map(d ->
                                        "172." + b + "." + c + "." + d))),
                // 192.168.x.x range
                Arbitraries.integers().between(0, 255).flatMap(c ->
                        Arbitraries.integers().between(0, 255).map(d ->
                                "192.168." + c + "." + d)),
                // 127.x.x.x range
                Arbitraries.integers().between(0, 255).flatMap(b ->
                        Arbitraries.integers().between(0, 255).flatMap(c ->
                                Arbitraries.integers().between(0, 255).map(d ->
                                        "127." + b + "." + c + "." + d)))
        );

        Arbitrary<String> schemes = Arbitraries.of("http://", "https://");
        Arbitrary<String> paths = Arbitraries.of("/", "/path", "/api/data", "/resource/123");

        return Combinators.combine(schemes, privateHosts, paths)
                .as((scheme, host, path) -> scheme + host + path);
    }

    @Provide
    Arbitrary<String> publicHosts() {
        // Use well-known public hosts that resolve to public IPs
        return Arbitraries.of(
                "google.com",
                "example.com",
                "github.com",
                "microsoft.com",
                "amazon.com",
                "cloudflare.com",
                "stackoverflow.com",
                "wikipedia.org",
                "mozilla.org",
                "apache.org"
        );
    }

    @Provide
    Arbitrary<String> urlPaths() {
        return Arbitraries.of("/", "/path", "/api/data", "/resource/123", "/index.html");
    }

    @Provide
    Arbitrary<String> nonHttpSchemeUrls() {
        Arbitrary<String> schemes = Arbitraries.of(
                "file://", "ftp://", "gopher://", "javascript:", "data:", "ldap://",
                "telnet://", "ssh://", "sftp://", "jar:", "mailto:"
        );
        Arbitrary<String> hosts = Arbitraries.of(
                "localhost/etc/passwd",
                "example.com/resource",
                "127.0.0.1/path",
                "internal.host/data",
                "void(0)"
        );

        return Combinators.combine(schemes, hosts).as((scheme, host) -> scheme + host);
    }
}
