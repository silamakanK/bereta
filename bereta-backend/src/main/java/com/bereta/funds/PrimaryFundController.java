package com.bereta.funds;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/caisses-principales")
@CrossOrigin(origins = "http://localhost:4200")
public class PrimaryFundController {

    private final PrimaryFundService primaryFundService;

    public PrimaryFundController(PrimaryFundService primaryFundService) {
        this.primaryFundService = primaryFundService;
    }

    @GetMapping
    public List<PrimaryFundResponse> findAll() {
        return primaryFundService.findAll();
    }

    @GetMapping("/{primaryFundId}")
    public PrimaryFundResponse findById(@PathVariable UUID primaryFundId) {
        return primaryFundService.findById(primaryFundId);
    }

    @PostMapping
    public ResponseEntity<PrimaryFundResponse> create(@Valid @RequestBody PrimaryFundRequest request) {
        PrimaryFundResponse created = primaryFundService.create(request);
        URI location = URI.create("/api/caisses-principales/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{primaryFundId}/caisses-locales")
    public List<LocalFundResponse> findLocalFunds(@PathVariable UUID primaryFundId) {
        return primaryFundService.findLocalFunds(primaryFundId);
    }

    @GetMapping("/{primaryFundId}/caisses-locales/{localFundId}")
    public LocalFundResponse findLocalFund(@PathVariable UUID primaryFundId, @PathVariable UUID localFundId) {
        return primaryFundService.findLocalFund(primaryFundId, localFundId);
    }

    @PostMapping("/{primaryFundId}/caisses-locales")
    public ResponseEntity<LocalFundResponse> createLocalFund(
            @PathVariable UUID primaryFundId,
            @Valid @RequestBody LocalFundRequest request
    ) {
        LocalFundResponse created = primaryFundService.createLocalFund(primaryFundId, request);
        URI location = URI.create("/api/caisses-principales/" + primaryFundId + "/caisses-locales/" + created.id());
        return ResponseEntity.created(location).body(created);
    }
}
