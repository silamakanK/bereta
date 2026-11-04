package com.bereta.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Adresse IP et navigateur de la requête en cours. Derrière un proxy, configurer
 * server.forward-headers-strategy pour que getRemoteAddr() renvoie l'IP réelle.
 */
public record ClientInfo(String ipAddress, String userAgent) {

    public static ClientInfo current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return new ClientInfo(null, null);
        }
        HttpServletRequest request = attributes.getRequest();
        String agent = request.getHeader("User-Agent");
        return new ClientInfo(request.getRemoteAddr(),
                agent == null ? null : agent.substring(0, Math.min(agent.length(), 255)));
    }
}
