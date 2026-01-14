package xyz.kaaniche.phoenix.iam.security;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.Priority;
import jakarta.ejb.EJBException;
import jakarta.inject.Inject;
import jakarta.security.enterprise.CallerPrincipal;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.security.Principal;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    private static final Config config = ConfigProvider.getConfig();
    private static final String REALM = config.getValue("mp.jwt.realm", String.class);
    private static final String CLAIM_ROLES = config.getValue("jwt.claim.roles", String.class);
    private static final String AUTHENTICATION_SCHEME = "Bearer";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Get the Authorization header
        String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

        // Validate the Authorization header
        if (!isTokenBasedAuthentication(authorizationHeader)) {
            abortWithUnauthorized(requestContext);
            return;
        }

        // Extract the token from the header
        String token = authorizationHeader.substring(AUTHENTICATION_SCHEME.length()).trim();
        if (token.isEmpty()) {
            abortWithUnauthorized(requestContext);
            return;
        }

        try {
            // Lookup JwtManager via JNDI
            InitialContext context = new InitialContext();
            JwtManager manager = (JwtManager) context.lookup("java:module/JwtManager");

            Optional<SignedJWT> jwt = manager.validateAccessToken(token);
            if (jwt.isPresent()) {
                JWTClaimsSet claims = jwt.get().getJWTClaimsSet();

                // ✅ Token expiration check
                if (claims.getExpirationTime() != null && claims.getExpirationTime().before(new Date())) {
                    abortWithUnauthorized(requestContext);
                    return;
                }

                // Null-safe role handling
                final String[] rolesArray = claims.getStringArrayClaim(CLAIM_ROLES);
                final Set<String> roles = rolesArray != null ? new HashSet<>(Arrays.asList(rolesArray)) : Collections.emptySet();

                final Principal userPrincipal = new CallerPrincipal(claims.getSubject());
                final boolean isSecure = requestContext.getSecurityContext().isSecure();

                // Identity utility (be aware of thread safety)
                IdentityUtility.iAm(claims.getSubject());

                // Set the SecurityContext for the request
                requestContext.setSecurityContext(new SecurityContext() {
                    @Override
                    public Principal getUserPrincipal() { return userPrincipal; }

                    @Override
                    public boolean isUserInRole(String role) { return roles.contains(role); }

                    @Override
                    public boolean isSecure() { return isSecure; }

                    @Override
                    public String getAuthenticationScheme() { return AUTHENTICATION_SCHEME; }
                });

            } else {
                abortWithUnauthorized(requestContext);
            }

        } catch (EJBException | ParseException | NamingException e) {
            abortWithUnauthorized(requestContext);
        }
    }

    private boolean isTokenBasedAuthentication(String authorizationHeader) {
        return authorizationHeader != null &&
               authorizationHeader.toLowerCase().startsWith(AUTHENTICATION_SCHEME.toLowerCase() + " ");
    }

    private void abortWithUnauthorized(ContainerRequestContext requestContext) {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE,
                            AUTHENTICATION_SCHEME + " realm=\"" + REALM + "\"")
                    .build()
        );
    }
}
