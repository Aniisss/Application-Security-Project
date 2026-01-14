package xyz.kaaniche.phoenix.iam.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJBException;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Startup
@Singleton
@LocalBean
public class JwtManager {

    // --- Standardized claim names ---
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_CLIENT_ID = "client_id";
    public static final String CLAIM_SCOPE = "scope";

    // Small clock-skew tolerance (seconds)
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final Config config = ConfigProvider.getConfig();

    private final Map<String, Long> keyPairExpirationTimes = new HashMap<>();
    private final Set<OctetKeyPair> cachedKeyPairs = new HashSet<>();

    private final Long keyPairLifetimeDuration = config.getValue("key.pair.lifetime.duration", Long.class);
    private final Short keyPairCacheSize = config.getValue("key.pair.cache.size", Short.class);

    private final Integer jwtLifetimeDuration = config.getValue("jwt.lifetime.duration", Integer.class);
    private final String issuer = config.getValue("jwt.issuer", String.class);
    private final List<String> audiences = config.getValues("jwt.audiences", String.class);

    private final String claimRoles = config.getValue("jwt.claim.roles", String.class);

    private final OctetKeyPairGenerator keyPairGenerator = new OctetKeyPairGenerator(Curve.Ed25519);

    @PostConstruct
    public void start() {
        while (cachedKeyPairs.size() < keyPairCacheSize) {
            cachedKeyPairs.add(generateKeyPair());
        }
    }

    public String generateAccessToken(String tenantId, String subject, String approvedScopes, String[] roles) {
        try {
            OctetKeyPair keyPair = getKeyPair()
                    .orElseThrow(() -> new EJBException("Unable to retrieve a valid Ed25519 KeyPair"));

            JWSSigner signer = new Ed25519Signer(keyPair);

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                    .keyID(keyPair.getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build();

            Instant now = Instant.now();

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audiences)
                    .subject(subject)
                    .claim("upn", subject)
                    .claim(CLAIM_TENANT_ID, tenantId)
                    .claim(CLAIM_SCOPE, normalizeScopes(approvedScopes))
                    .claim(claimRoles, roles)
                    .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(jwtLifetimeDuration, ChronoUnit.SECONDS)))
                    .build();

            SignedJWT jwt = new SignedJWT(header, claimsSet);
            jwt.sign(signer);
            return jwt.serialize();

        } catch (JOSEException e) {
            throw new EJBException(e);
        }
    }

    /**
     * Refresh token should be bound to the CLIENT (OAuth client_id) and optionally tenant_id.
     * Here we store BOTH to avoid confusion:
     * - client_id: the OAuth client (tenant/application)
     * - tenant_id: optional same value, keep if your system expects it
     */
    public String generateRefreshToken(String clientId, String subject, String approvedScope) {
        try {
            OctetKeyPair keyPair = getKeyPair()
                    .orElseThrow(() -> new EJBException("Unable to retrieve a valid Ed25519 KeyPair"));

            JWSSigner signer = new Ed25519Signer(keyPair);

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                    .keyID(keyPair.getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build();

            Instant now = Instant.now();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audiences)
                    .subject(subject)
                    // keep compatibility: some code expects tenant_id
                    .claim(CLAIM_TENANT_ID, clientId)
                    // correct semantic name:
                    .claim(CLAIM_CLIENT_ID, clientId)
                    .claim(CLAIM_SCOPE, normalizeScopes(approvedScope))
                    .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(3, ChronoUnit.HOURS)))
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();

        } catch (JOSEException e) {
            throw new EJBException(e);
        }
    }

    public Optional<SignedJWT> validateAccessToken(String token) {
        return validateJWT(token, TOKEN_TYPE_ACCESS);
    }

    public Optional<SignedJWT> validateRefreshToken(String token) {
        return validateJWT(token, TOKEN_TYPE_REFRESH);
    }

    /**
     * Validates:
     * - signature
     * - exp (required), nbf (if present), iat (sanity)
     * - iss and aud
     * - token_type (required)
     */
    private Optional<SignedJWT> validateJWT(String token, String expectedType) {
        try {
            SignedJWT parsed = SignedJWT.parse(token);

            OctetKeyPair publicKey = cachedKeyPairs.stream()
                    .filter(kp -> kp.getKeyID().equals(parsed.getHeader().getKeyID()))
                    .findFirst()
                    .orElseThrow(() -> new EJBException("Unable to retrieve the key pair associated with the kid"))
                    .toPublicJWK();

            JWSVerifier verifier = new Ed25519Verifier(publicKey);
            if (!parsed.verify(verifier)) return Optional.empty();

            JWTClaimsSet claims = parsed.getJWTClaimsSet();
            Instant now = Instant.now();

            // exp REQUIRED
            Date exp = claims.getExpirationTime();
            if (exp == null) return Optional.empty();
            if (exp.toInstant().isBefore(now.minusSeconds(CLOCK_SKEW_SECONDS))) return Optional.empty();

            // nbf
            Date nbf = claims.getNotBeforeTime();
            if (nbf != null && nbf.toInstant().isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))) return Optional.empty();

            // iat sanity
            Date iat = claims.getIssueTime();
            if (iat != null && iat.toInstant().isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))) return Optional.empty();

            // iss
            String tokenIss = claims.getIssuer();
            if (tokenIss == null || !issuer.equals(tokenIss)) return Optional.empty();

            // aud
            List<String> tokenAud = claims.getAudience();
            if (tokenAud == null || tokenAud.isEmpty()) return Optional.empty();
            boolean audOk = tokenAud.stream().anyMatch(audiences::contains);
            if (!audOk) return Optional.empty();

            // token_type
            String tokenType = claims.getStringClaim(CLAIM_TOKEN_TYPE);
            if (tokenType == null || !tokenType.equals(expectedType)) return Optional.empty();

            return Optional.of(parsed);

        } catch (ParseException | JOSEException e) {
            throw new EJBException(e);
        }
    }

    public OctetKeyPair getPublicValidationKey(String kid) {
        return cachedKeyPairs.stream()
                .filter(kp -> kp.getKeyID().equals(kid))
                .findFirst()
                .orElseThrow(() -> new EJBException("Unable to retrieve the key pair associated with the kid"))
                .toPublicJWK();
    }

    private OctetKeyPair generateKeyPair() {
        try {
            long currentUTCSeconds = LocalDateTime.now(ZoneId.of("UTC")).toEpochSecond(ZoneOffset.UTC);
            String kid = UUID.randomUUID().toString();

            keyPairExpirationTimes.put(kid, currentUTCSeconds + keyPairLifetimeDuration);

            return keyPairGenerator
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(kid)
                    .generate();

        } catch (JOSEException e) {
            throw new EJBException(e);
        }
    }

    private boolean hasNotExpired(OctetKeyPair keyPair) {
        long now = LocalDateTime.now(ZoneId.of("UTC")).toEpochSecond(ZoneOffset.UTC);
        Long exp = keyPairExpirationTimes.get(keyPair.getKeyID());
        return exp != null && now <= exp;
    }

    private boolean isPublicKeyExpired(OctetKeyPair keyPair) {
        long now = LocalDateTime.now(ZoneId.of("UTC")).toEpochSecond(ZoneOffset.UTC);
        Long exp = keyPairExpirationTimes.get(keyPair.getKeyID());
        if (exp == null) return true;
        return now > (exp + jwtLifetimeDuration);
    }

    private Optional<OctetKeyPair> getKeyPair() {
        // prune expired public keys + prune metadata (prevents memory leak)
        cachedKeyPairs.removeIf(kp -> {
            boolean expired = isPublicKeyExpired(kp);
            if (expired) keyPairExpirationTimes.remove(kp.getKeyID());
            return expired;
        });

        while (cachedKeyPairs.stream().filter(this::hasNotExpired).count() < keyPairCacheSize) {
            cachedKeyPairs.add(generateKeyPair());
        }

        return cachedKeyPairs.stream().filter(this::hasNotExpired).findAny();
    }

    private static String normalizeScopes(String scopes) {
        if (scopes == null) return "";
        // normalize whitespace to single spaces
        return String.join(" ", Arrays.stream(scopes.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .toList());
    }

    public String getClaimRoles() {
        return claimRoles;
    }

    public String getIssuer() {
        return issuer;
    }

    public List<String> getAudiences() {
        return audiences;
    }
}
