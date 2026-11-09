package com.bereta.imports;

import com.bereta.auth.BeretaUserDetails;
import com.bereta.imports.ImportDtos.PreviewResponse;
import com.bereta.imports.ImportDtos.RowsUpdateRequest;
import com.bereta.imports.ImportDtos.SummaryResponse;
import com.bereta.imports.ImportDtos.ValidateRequest;
import com.bereta.imports.ImportDtos.ValidationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/imports")
public class MemberImportController {

    private final MemberImportService service;

    public MemberImportController(MemberImportService service) {
        this.service = service;
    }

    /** Multipart : {@code fichiers} (1 document ou jusqu'à 5 photos) et {@code localFundId} (vide = caisse principale). */
    @PostMapping(path = "/membres/analyse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PreviewResponse analyse(@AuthenticationPrincipal BeretaUserDetails user,
                                   @RequestParam(name = "fichiers", required = false) List<MultipartFile> files,
                                   @RequestParam(name = "localFundId", required = false) UUID localFundId) {
        return service.analyse(user.getId(), localFundId, files);
    }

    @GetMapping
    public List<SummaryResponse> history(@AuthenticationPrincipal BeretaUserDetails user) {
        return service.history(user.getId());
    }

    @GetMapping("/{id}")
    public PreviewResponse get(@AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID id) {
        return service.get(user.getId(), id);
    }

    @PutMapping("/{id}/lignes")
    public PreviewResponse updateRows(@AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID id,
                                      @Valid @RequestBody RowsUpdateRequest request) {
        return service.updateRows(user.getId(), id, request);
    }

    @PostMapping("/{id}/valider")
    public ValidationResponse validate(@AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID id,
                                       @RequestBody(required = false) ValidateRequest request) {
        return service.validate(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal BeretaUserDetails user, @PathVariable UUID id) {
        service.cancel(user.getId(), id);
    }
}
