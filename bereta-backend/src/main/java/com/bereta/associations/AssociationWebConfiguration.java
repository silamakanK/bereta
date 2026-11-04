package com.bereta.associations;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AssociationWebConfiguration implements WebMvcConfigurer {
    private final AssociationContextInterceptor interceptor;
    public AssociationWebConfiguration(AssociationContextInterceptor interceptor) { this.interceptor = interceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/admin/**", "/api/associations", "/api/bonjour");
    }
}
