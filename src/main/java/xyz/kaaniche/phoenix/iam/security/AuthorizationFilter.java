package xyz.kaaniche.phoenix.iam.security;

import jakarta.annotation.Priority;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import java.lang.reflect.Method;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class AuthorizationFilter implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Method method = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();

        SecurityContext sc = requestContext.getSecurityContext();

        // DenyAll on method
        if (method.isAnnotationPresent(DenyAll.class)) {
            refuseRequest(sc);
        }

        // RolesAllowed on method
        RolesAllowed rolesAllowed = method.getAnnotation(RolesAllowed.class);
        if (rolesAllowed != null) {
            performAuthorization(rolesAllowed.value(), sc);
            return;
        }

        // PermitAll on method
        if (method.isAnnotationPresent(PermitAll.class)) {
            return;
        }

        // RolesAllowed on class
        rolesAllowed = resourceClass.getAnnotation(RolesAllowed.class);
        if (rolesAllowed != null) {
            performAuthorization(rolesAllowed.value(), sc);
            return;
        }

        // DenyAll on class
        if (resourceClass.isAnnotationPresent(DenyAll.class)) {
            refuseRequest(sc);
        }

        // PermitAll on class
        if (resourceClass.isAnnotationPresent(PermitAll.class)) {
            return;
        }

        // No annotation → allow by default
    }

    private void performAuthorization(String[] rolesAllowed, SecurityContext sc) {
        if (sc == null || sc.getUserPrincipal() == null) {
            refuseRequest(sc);
        }

        for (String role : rolesAllowed) {
            if (sc.isUserInRole(role.trim())) {
                return;
            }
        }

        refuseRequest(sc);
    }

    private void refuseRequest(SecurityContext sc) {
        // Optional logging
        System.out.println("Authorization failed for user: " +
                (sc != null && sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : "anonymous"));

        // Return 403 Forbidden instead of 401
        throw new WebApplicationException("Access denied", Response.Status.FORBIDDEN);
    }
}
