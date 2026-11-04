package com.bereta.associations;

import com.bereta.auth.BeretaUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Component
public class AssociationContextInterceptor implements HandlerInterceptor {
    private final AssociationAccessService access;
    public AssociationContextInterceptor(AssociationAccessService access) { this.access = access; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("X-Association-Id");
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sélectionnez une association active.");
        }
        UUID associationId;
        try { associationId = UUID.fromString(header); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Association sélectionnée invalide.");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof BeretaUserDetails user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        access.requireAccess(user.getId(), associationId);
        request.setAttribute(AssociationRequestContext.REQUEST_ATTRIBUTE, associationId);
        return true;
    }
}
