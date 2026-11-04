package com.bereta.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AccountStatusFilter extends OncePerRequestFilter {
    private final UserAccountRepository accounts;
    public AccountStatusFilter(UserAccountRepository accounts) { this.accounts = accounts; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof BeretaUserDetails principal) {
            UserAccountEntity current = accounts.findById(principal.getId()).orElse(null);
            if (current == null || !current.isEnabled() || !current.getRole().equals(principal.getRole())) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "La session du compte n’est plus valide.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
