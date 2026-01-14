package xyz.kaaniche.phoenix.iam.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import xyz.kaaniche.phoenix.iam.controllers.PhoenixIAMRepository;
import xyz.kaaniche.phoenix.iam.entities.Grant;
import xyz.kaaniche.phoenix.iam.entities.Identity;
import xyz.kaaniche.phoenix.iam.entities.Tenant;
import xyz.kaaniche.phoenix.iam.security.Argon2Utility;
import xyz.kaaniche.phoenix.iam.security.AuthorizationCode;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Path("/")
@RequestScoped
public class AuthenticationEndpoint {

    public static final String CHALLENGE_RESPONSE_COOKIE_ID = "signInId";

    @Inject private Logger logger;
    @Inject PhoenixIAMRepository phoenixIAMRepository;

    @Context private HttpServletRequest request;

    // -----------------------------
    // Brute-force protection
    // -----------------------------
    private static final int MAX_FAILS_PER_USER_IP = 5;         // 5 tries
    private static final int MAX_FAILS_PER_IP = 30;             // 30 tries
    private static final long WINDOW_SECONDS = 300;             // 5 minutes
    private static final long BLOCK_SECONDS = 900;              // 15 minutes
    private static final long BASE_DELAY_MS = 150;              // small slow-down per fail (optional)

    private static final ConcurrentHashMap<String, Attempt> USER_IP_ATTEMPTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Attempt> IP_ATTEMPTS = new ConcurrentHashMap<>();

    private static final class Attempt {
        int fails;
        long windowStart;
        long blockedUntil;
    }

    private boolean isBlocked(Attempt a, long nowEpochSec) {
        return a != null && a.blockedUntil > nowEpochSec;
    }

    private Attempt getAttempt(ConcurrentHashMap<String, Attempt> map, String key, long nowEpochSec) {
        return map.compute(key, (k, existing) -> {
            if (existing == null) {
                Attempt a = new Attempt();
                a.windowStart = nowEpochSec;
                return a;
            }
            // reset window if old
            if (nowEpochSec - existing.windowStart > WINDOW_SECONDS) {
                existing.windowStart = nowEpochSec;
                existing.fails = 0;
                existing.blockedUntil = 0;
            }
            return existing;
        });
    }

    private void registerFailure(String ip, String username, long nowEpochSec) {
        String userIpKey = ip + ":" + (username == null ? "" : username);

        Attempt u = getAttempt(USER_IP_ATTEMPTS, userIpKey, nowEpochSec);
        u.fails++;
        if (u.fails >= MAX_FAILS_PER_USER_IP) {
            u.blockedUntil = nowEpochSec + BLOCK_SECONDS;
        }

        Attempt i = getAttempt(IP_ATTEMPTS, ip, nowEpochSec);
        i.fails++;
        if (i.fails >= MAX_FAILS_PER_IP) {
            i.blockedUntil = nowEpochSec + BLOCK_SECONDS;
        }

        // Optional progressive delay (slows brute-force even if not blocked yet)
        try {
            long delay = Math.min(2000, BASE_DELAY_MS * Math.max(1, u.fails));
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void clearFailures(String ip, String username) {
        String userIpKey = ip + ":" + (username == null ? "" : username);
        USER_IP_ATTEMPTS.remove(userIpKey);
        // Do NOT clear IP_ATTEMPTS globally (could help attacker reset IP penalties).
    }

    private String getClientIp() {
        // If behind proxy, you may want to trust X-Forwarded-For only from known gateways
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // -----------------------------
    // Cookie parsing (safe)
    // Format: tenant#scope$redirectUri
    // -----------------------------
    private record SignInData(String tenant, String requestedScope, String redirectUri) {}

    private Optional<SignInData> parseSignInCookie(Cookie cookie) {
        if (cookie == null || cookie.getValue() == null) return Optional.empty();
        String v = cookie.getValue();

        int hash = v.indexOf('#');
        int dollar = v.indexOf('$');

        if (hash <= 0 || dollar <= hash + 1 || dollar >= v.length() - 1) return Optional.empty();

        String tenant = v.substring(0, hash).trim();
        String scope = v.substring(hash + 1, dollar).trim();
        String redirect = v.substring(dollar + 1).trim();

        if (tenant.isEmpty() || redirect.isEmpty()) return Optional.empty();

        return Optional.of(new SignInData(tenant, scope, redirect));
    }

    // -----------------------------
    // Endpoints
    // -----------------------------
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/authorize")
    public Response authorize(@Context UriInfo uriInfo) {
        MultivaluedMap<String, String> params = uriInfo.getQueryParameters();

        // 1) tenant
        String clientId = params.getFirst("client_id");
        if (clientId == null || clientId.isEmpty()) {
            return informUserAboutError("Invalid client_id");
        }

        Tenant tenant = phoenixIAMRepository.findTenantByName(clientId);
        if (tenant == null) {
            return informUserAboutError("Invalid client_id");
        }

        // 2) grant type
        if (tenant.getSupportedGrantTypes() != null && !tenant.getSupportedGrantTypes().contains("authorization_code")) {
            return informUserAboutError("authorization_code is not allowed for this tenant");
        }

        // 3) redirectUri
        String redirectUri = params.getFirst("redirect_uri");
        if (tenant.getRedirectUri() != null && !tenant.getRedirectUri().isEmpty()) {
            if (redirectUri != null && !redirectUri.isEmpty() && !tenant.getRedirectUri().equals(redirectUri)) {
                return informUserAboutError("redirect_uri must match the pre-registered redirect_uri");
            }
            redirectUri = tenant.getRedirectUri();
        } else {
            if (redirectUri == null || redirectUri.isEmpty()) {
                return informUserAboutError("redirect_uri must be provided");
            }
        }

        // 4) response_type
        String responseType = params.getFirst("response_type");
        if (!"code".equals(responseType) && !"token".equals(responseType)) {
            return informUserAboutError("response_type must be code or token");
        }

        // 5) scope
        String requestedScope = params.getFirst("scope");
        if (requestedScope == null || requestedScope.isEmpty()) {
            requestedScope = tenant.getRequiredScopes();
        }

        // 6) PKCE method
        String codeChallengeMethod = params.getFirst("code_challenge_method");
        if (codeChallengeMethod == null || !codeChallengeMethod.equals("S256")) {
            return informUserAboutError("code_challenge_method must be 'S256'");
        }

        StreamingOutput stream = output -> {
            try (InputStream is = Objects.requireNonNull(getClass().getResource("/login.html")).openStream()) {
                output.write(is.readAllBytes());
            }
        };

        return Response.ok(stream)
                .location(uriInfo.getBaseUri().resolve("/login/authorization"))
                .cookie(new NewCookie.Builder(CHALLENGE_RESPONSE_COOKIE_ID)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite(NewCookie.SameSite.STRICT)
                        .value(tenant.getName() + "#" + requestedScope + "$" + redirectUri)
                        .build())
                .build();
    }

    @POST
    @Path("/login/authorization")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response login(@CookieParam(CHALLENGE_RESPONSE_COOKIE_ID) Cookie cookie,
                          @FormParam("username") String username,
                          @FormParam("password") String password,
                          @Context UriInfo uriInfo) throws Exception {

        String ip = getClientIp();
        long now = Instant.now().getEpochSecond();

        // Block check (per IP and per username/IP)
        Attempt ipAttempt = getAttempt(IP_ATTEMPTS, ip, now);
        Attempt userAttempt = getAttempt(USER_IP_ATTEMPTS, ip + ":" + (username == null ? "" : username), now);

        if (isBlocked(ipAttempt, now) || isBlocked(userAttempt, now)) {
            return Response.status(429).entity("Too many attempts. Try again later.").build();
        }

        Optional<SignInData> signInDataOpt = parseSignInCookie(cookie);
        if (signInDataOpt.isEmpty()) {
            // Don’t leak details; treat as invalid request
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid session state.").build();
        }
        SignInData signInData = signInDataOpt.get();

        // Re-validate tenant & redirectUri against server truth (mitigates cookie tampering)
        Tenant tenant = phoenixIAMRepository.findTenantByName(signInData.tenant());
        if (tenant == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid session state.").build();
        }
        if (tenant.getRedirectUri() != null && !tenant.getRedirectUri().isEmpty()) {
            if (!tenant.getRedirectUri().equals(signInData.redirectUri())) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Invalid session state.").build();
            }
        }

        // Prevent user enumeration: same behavior for invalid username/password
        Identity identity = (username == null) ? null : phoenixIAMRepository.findIdentityByUsername(username);
        boolean ok = identity != null && Argon2Utility.check(identity.getPassword(), password == null ? new char[0] : password.toCharArray());

        if (!ok) {
            registerFailure(ip, username, now);
            logger.info("Failure when authenticating identity:" + username);

            URI location = UriBuilder.fromUri(signInData.redirectUri())
                    .queryParam("error", "access_denied")
                    .queryParam("error_description", "Invalid credentials or request denied.")
                    .build();

            return Response.seeOther(location).build();
        }

        // success
        clearFailures(ip, username);
        logger.info("Authenticated identity:" + username);

        MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
        Optional<Grant> grant = phoenixIAMRepository.findGrant(signInData.tenant(), identity.getId());

        if (grant.isPresent()) {
            String redirectURI = buildActualRedirectURI(
                    signInData.redirectUri(),
                    params.getFirst("response_type"),
                    signInData.tenant(),
                    username,
                    checkUserScopes(grant.get().getApprovedScopes(), signInData.requestedScope()),
                    params.getFirst("code_challenge"),
                    params.getFirst("state")
            );
            return Response.seeOther(UriBuilder.fromUri(redirectURI).build()).build();
        } else {
            StreamingOutput stream = output -> {
                try (InputStream is = Objects.requireNonNull(getClass().getResource("/consent.html")).openStream()) {
                    output.write(is.readAllBytes());
                }
            };
            return Response.ok(stream).build();
        }
    }

    @PATCH
    @Path("/login/authorization")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response grantConsent(@CookieParam(CHALLENGE_RESPONSE_COOKIE_ID) Cookie cookie,
                                 @FormParam("approved_scope") String scope,
                                 @FormParam("approval_status") String approvalStatus,
                                 @FormParam("username") String username) {

        Optional<SignInData> signInDataOpt = parseSignInCookie(cookie);
        if (signInDataOpt.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid session state.").build();
        }
        SignInData signInData = signInDataOpt.get();

        if ("NO".equals(approvalStatus)) {
            URI location = UriBuilder.fromUri(signInData.redirectUri())
                    .queryParam("error", "access_denied")
                    .queryParam("error_description", "User didn't approve the request.")
                    .build();
            return Response.seeOther(location).build();
        }

        List<String> approvedScopes = (scope == null) ? List.of() : Arrays.stream(scope.split(" ")).filter(s -> !s.isBlank()).toList();
        if (approvedScopes.isEmpty()) {
            URI location = UriBuilder.fromUri(signInData.redirectUri())
                    .queryParam("error", "access_denied")
                    .queryParam("error_description", "User didn't approve the request.")
                    .build();
            return Response.seeOther(location).build();
        }

        try {
            return Response.seeOther(UriBuilder.fromUri(buildActualRedirectURI(
                    signInData.redirectUri(),
                    null,
                    signInData.tenant(),
                    username,
                    String.join(" ", approvedScopes),
                    null,
                    null
            )).build()).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildActualRedirectURI(String redirectUri,
                                         String responseType,
                                         String clientId,
                                         String userId,
                                         String approvedScopes,
                                         String codeChallenge,
                                         String state) throws Exception {
        StringBuilder sb = new StringBuilder(redirectUri);

        if ("code".equals(responseType)) {
            AuthorizationCode authorizationCode = new AuthorizationCode(
                    clientId,
                    userId,
                    approvedScopes,
                    Instant.now().plus(2, ChronoUnit.MINUTES).getEpochSecond(),
                    redirectUri
            );

            sb.append("?code=").append(URLEncoder.encode(authorizationCode.getCode(codeChallenge), StandardCharsets.UTF_8));
        } else {
            // Implicit response_type=token is not supported
            return null;
        }

        if (state != null) sb.append("&state=").append(state);
        return sb.toString();
    }

    private String checkUserScopes(String userScopes, String requestedScope) {
        Set<String> allowedScopes = new LinkedHashSet<>();
        Set<String> rScopes = new HashSet<>(Arrays.asList((requestedScope == null ? "" : requestedScope).split(" ")));
        Set<String> uScopes = new HashSet<>(Arrays.asList((userScopes == null ? "" : userScopes).split(" ")));

        for (String scope : uScopes) {
            if (rScopes.contains(scope)) allowedScopes.add(scope);
        }
        return String.join(" ", allowedScopes);
    }

    // Basic HTML escaping to prevent reflected XSS in error pages
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private Response informUserAboutError(String error) {
        String safe = escapeHtml(error);
        return Response.status(Response.Status.BAD_REQUEST).entity("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8"/>
                    <title>Error</title>
                </head>
                <body>
                <aside class="container">
                    <p>%s</p>
                </aside>
                </body>
                </html>
                """.formatted(safe)).build();
    }
}
