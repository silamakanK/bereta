package com.bereta.members;

import com.bereta.auth.BeretaUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/membres")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberResponse> findAll(@AuthenticationPrincipal BeretaUserDetails user) {
        return memberService.findAll(user.getId());
    }

    @GetMapping("/{id}")
    public MemberResponse findById(@AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID id) {
        return memberService.findById(user.getId(), id);
    }

    @PostMapping
    public ResponseEntity<MemberResponse> create(@AuthenticationPrincipal BeretaUserDetails user,
                                                 @Valid @RequestBody MemberRequest request) {
        MemberResponse created = memberService.create(user.getId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public MemberResponse update(@AuthenticationPrincipal BeretaUserDetails user,
                                 @PathVariable UUID id, @Valid @RequestBody MemberRequest request) {
        return memberService.update(user.getId(), id, request);
    }

    /** Archiver un membre : il reste consultable mais n'est plus inscrit aux nouvelles campagnes. */
    @PostMapping("/{id}/archiver")
    public MemberResponse archive(@AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID id) {
        return memberService.setActive(user.getId(), id, false);
    }

    @PostMapping("/{id}/reactiver")
    public MemberResponse reactivate(@AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID id) {
        return memberService.setActive(user.getId(), id, true);
    }
}
