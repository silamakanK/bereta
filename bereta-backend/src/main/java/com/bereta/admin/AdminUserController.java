package com.bereta.admin;

import com.bereta.admin.AdminDtos.ActionRequest;
import com.bereta.admin.AdminDtos.PasswordResetResult;
import com.bereta.admin.AdminDtos.UserActionResult;
import com.bereta.admin.AdminDtos.UserDetail;
import com.bereta.admin.AdminDtos.UserSummary;
import com.bereta.auth.BeretaUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Comptes de toute la plateforme (rôle ADMIN exigé par SecurityConfiguration sur /api/admin/**). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public AdminPageResponse<UserSummary> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) String role,
                                               @RequestParam(required = false) Boolean enabled,
                                               @RequestParam(defaultValue = "createdAt") String sort,
                                               @RequestParam(defaultValue = "desc") String direction,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.list(new AdminUserQuery.Filter(q, role, enabled, sort, "asc".equalsIgnoreCase(direction)),
                page, size);
    }

    @GetMapping("/{id}")
    public UserDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/disable")
    public UserActionResult disable(@AuthenticationPrincipal BeretaUserDetails admin, @PathVariable UUID id,
                                    @Valid @RequestBody ActionRequest request) {
        return service.disable(admin.getId(), id, request.reason().trim());
    }

    @PostMapping("/{id}/enable")
    public UserActionResult enable(@AuthenticationPrincipal BeretaUserDetails admin, @PathVariable UUID id,
                                   @Valid @RequestBody ActionRequest request) {
        return service.enable(admin.getId(), id, request.reason().trim());
    }

    /** Le mot de passe provisoire n'est renvoyé qu'ici, une seule fois, et jamais mis en cache. */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<PasswordResetResult> resetPassword(@AuthenticationPrincipal BeretaUserDetails admin,
                                                             @PathVariable UUID id,
                                                             @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.resetPassword(admin.getId(), id, request.reason().trim()));
    }
}
