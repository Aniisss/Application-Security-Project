package xyz.kaaniche.phoenix.iam.boundaries;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import xyz.kaaniche.phoenix.iam.controllers.PhoenixIAMRepository;
import xyz.kaaniche.phoenix.iam.security.AuthorizationCode;
import xyz.kaaniche.phoenix.iam.security.JwtManager;

import java.security.GeneralSecurityException;
import java.util.Set;

@Path("/oauth/token")
public class TokenEndpoint {

    private final Set<String> supportedGrantTypes = Set.of("authorization_code", "refresh_token");

    @Inject
    private PhoenixIAMRepository phoenixIAMRepository;

    @EJB
    private JwtManager jwtManager;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response token(@FormParam("grant_type") String grantType,
                          // Authorization Code flow params
                          @FormParam("code") String code,
                          @FormParam("code_verifier") String codeVerifier,
                          // Refresh token flow param (correct one)
                          @FormParam("refresh_token") String refreshToken) {

        if (grantType == null || grantType.isBlank()) {
            return responseError("invalid_request", "grant_type is required", Response.Status.BAD_REQUEST);
        }

        if (!supportedGrantTypes.contains(grantType)) {
            return responseError("unsupported_grant_type",
                    "grant_type should be one of: " + supportedGrantTypes,
                    Response.Status.BAD_REQUEST);
        }

        // -------------------------------
        // 1) Refresh Token Grant
        // -------------------------------
        if ("refresh_token".equals(grantType)) {

            if (refreshToken == null || refreshToken.isBlank()) {
                return responseError("invalid_request", "refresh_token is required", Response.Status.BAD_REQUEST);
            }

            // IMPORTANT: validate as REFRESH token (prevents access/refresh confusion)
            var refreshJwtOpt = jwtManager.validateRefreshToken(refreshToken);
            if (refreshJwtOpt.isEmpty()) {
                return responseError("invalid_grant", "Invalid refresh token", Response.Status.UNAUTHORIZED);
            }

            try {
                var refreshClaims = refreshJwtOpt.get().getJWTClaimsSet();

                String tenantId = refreshClaims.getStringClaim("tenant_id");
                String subject = refreshClaims.getSubject();
                String scopes = refreshClaims.getStringClaim("scope");

                if (tenantId == null || tenantId.isBlank() ||
                    subject == null || subject.isBlank() ||
                    scopes == null) {
                    return responseError("invalid_grant", "Malformed refresh token", Response.Status.UNAUTHORIZED);
                }

                // TODO (best practice): implement refresh token rotation + revocation store by jti
                // Example:
                // String jti = refreshClaims.getJWTID();
                // if (isRevokedOrAlreadyUsed(jti)) return responseError(...)

                // Roles should ideally come from server-side source (DB), not from refresh token
                String[] roles = phoenixIAMRepository.getRoles(subject);

                String newAccessToken = jwtManager.generateAccessToken(tenantId, subject, scopes, roles);
                String newRefreshToken = jwtManager.generateRefreshToken(tenantId, subject, scopes);

                return Response.ok(Json.createObjectBuilder()
                                .add("token_type", "Bearer")
                                .add("access_token", newAccessToken)
                                .add("expires_in", ConfigProvider.getConfig().getValue("jwt.lifetime.duration", Integer.class))
                                .add("scope", scopes)
                                .add("refresh_token", newRefreshToken)
                                .build())
                        .header("Cache-Control", "no-store")
                        .header("Pragma", "no-cache")
                        .build();

            } catch (Exception e) {
                return responseError("server_error", "Can't refresh token", Response.Status.INTERNAL_SERVER_ERROR);
            }
        }

        // -------------------------------
        // 2) Authorization Code Grant (PKCE)
        // -------------------------------
        if ("authorization_code".equals(grantType)) {

            if (code == null || code.isBlank()) {
                return responseError("invalid_request", "code is required", Response.Status.BAD_REQUEST);
            }
            if (codeVerifier == null || codeVerifier.isBlank()) {
                return responseError("invalid_request", "code_verifier is required", Response.Status.BAD_REQUEST);
            }

            try {
                AuthorizationCode decoded = AuthorizationCode.decode(code, codeVerifier);
                if (decoded == null) {
                    return responseError("invalid_grant", "Invalid authorization code", Response.Status.UNAUTHORIZED);
                }

                String tenantName = decoded.tenantName();
                String username = decoded.identityUsername();
                String approvedScopes = decoded.approvedScopes();

                if (tenantName == null || tenantName.isBlank() ||
                    username == null || username.isBlank() ||
                    approvedScopes == null) {
                    return responseError("invalid_grant", "Malformed authorization code", Response.Status.UNAUTHORIZED);
                }

                String[] roles = phoenixIAMRepository.getRoles(username);

                String accessToken = jwtManager.generateAccessToken(tenantName, username, approvedScopes, roles);
                String refreshTok = jwtManager.generateRefreshToken(tenantName, username, approvedScopes);

                return Response.ok(Json.createObjectBuilder()
                                .add("token_type", "Bearer")
                                .add("access_token", accessToken)
                                .add("expires_in", ConfigProvider.getConfig().getValue("jwt.lifetime.duration", Integer.class))
                                .add("scope", approvedScopes)
                                .add("refresh_token", refreshTok)
                                .build())
                        .header("Cache-Control", "no-store")
                        .header("Pragma", "no-cache")
                        .build();

            } catch (GeneralSecurityException e) {
                return responseError("invalid_grant", "Invalid PKCE verification", Response.Status.UNAUTHORIZED);
            } catch (Exception e) {
                return responseError("server_error", "Can't get token", Response.Status.INTERNAL_SERVER_ERROR);
            }
        }

        // Should never happen because we validated supportedGrantTypes
        return responseError("unsupported_grant_type", "Unsupported grant_type", Response.Status.BAD_REQUEST);
    }

    private Response responseError(String error, String errorDescription, Response.Status status) {
        JsonObject errorResponse = Json.createObjectBuilder()
                .add("error", error)
                .add("error_description", errorDescription)
                .build();

        return Response.status(status)
                .entity(errorResponse)
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .build();
    }
}
