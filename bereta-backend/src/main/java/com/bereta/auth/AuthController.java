package com.bereta.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken, HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
        csrfToken.getToken();
        return csrfToken;
    }

    @GetMapping("/me")
    public AuthUserResponse currentUser(@AuthenticationPrincipal BeretaUserDetails user) {
        return AuthUserResponse.from(user);
    }
}
