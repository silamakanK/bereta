package com.bereta.funds;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PrimaryFundService {

    private final PrimaryFundRepository primaryFundRepository;
    private final LocalFundRepository localFundRepository;

    public PrimaryFundService(PrimaryFundRepository primaryFundRepository, LocalFundRepository localFundRepository) {
        this.primaryFundRepository = primaryFundRepository;
        this.localFundRepository = localFundRepository;
    }

    public List<PrimaryFundResponse> findAll() {
        return primaryFundRepository.findAllByOrderByNameAsc().stream()
                .map(PrimaryFundResponse::from).toList();
    }

    public PrimaryFundResponse findById(UUID id) {
        return PrimaryFundResponse.from(requirePrimaryFund(id));
    }

    @Transactional
    public PrimaryFundResponse create(PrimaryFundRequest request) {
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        PrimaryFundEntity fund = new PrimaryFundEntity(
                request.name().trim(), request.objective().trim(), description);
        return PrimaryFundResponse.from(primaryFundRepository.save(fund));
    }

    public List<LocalFundResponse> findLocalFunds(UUID primaryFundId) {
        requirePrimaryFund(primaryFundId);
        return localFundRepository.findAllByPrimaryFundIdOrderByNameAsc(primaryFundId).stream()
                .map(LocalFundResponse::from).toList();
    }

    @Transactional
    public LocalFundResponse findLocalFund(UUID primaryFundId, UUID localFundId) {
        requirePrimaryFund(primaryFundId);
        return localFundRepository.findById(localFundId)
                .filter(localFund -> localFund.getPrimaryFund().getId().equals(primaryFundId))
                .map(LocalFundResponse::from)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Caisse locale introuvable"));
    }

    @Transactional
    public LocalFundResponse createLocalFund(UUID primaryFundId, LocalFundRequest request) {
        PrimaryFundEntity primaryFund = requirePrimaryFund(primaryFundId);
        LocalFundEntity localFund = new LocalFundEntity(primaryFund, request.name().trim());
        return LocalFundResponse.from(localFundRepository.save(localFund));
    }

    private PrimaryFundEntity requirePrimaryFund(java.util.UUID id) {
        return primaryFundRepository.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Caisse principale introuvable"));
    }
}
