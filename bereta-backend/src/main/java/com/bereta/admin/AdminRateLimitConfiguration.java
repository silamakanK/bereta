package com.bereta.admin;

import com.bereta.auth.BeretaUserDetails;
import com.bereta.auth.ClientInfo;
import com.bereta.auth.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Limite de fréquence sur /api/admin/** (par administrateur, à défaut par adresse IP) : 429 au-delà. */
@Configuration
public class AdminRateLimitConfiguration implements WebMvcConfigurer {

    private final RateLimiterService rateLimiter;

    public AdminRateLimitConfiguration(RateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminRateLimitInterceptor(rateLimiter)).addPathPatterns("/api/admin/**");
    }

    static final class AdminRateLimitInterceptor implements HandlerInterceptor {

        private final RateLimiterService rateLimiter;

        AdminRateLimitInterceptor(RateLimiterService rateLimiter) {
            this.rateLimiter = rateLimiter;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String key = authentication != null && authentication.getPrincipal() instanceof BeretaUserDetails admin
                    ? admin.getId().toString() : ClientInfo.current().ipAddress();
            rateLimiter.check(RateLimiterService.Limit.ADMIN_PER_USER, key);
            return true;
        }
    }
}
