package com.bereta.members;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<MemberResponse> findAll() {
        return memberRepository.findAllByOrderByLastNameAscFirstNameAsc()
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    public MemberResponse findById(UUID id) {
        return MemberResponse.from(getMember(id));
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        MemberEntity member = new MemberEntity(
                request.firstName().trim(),
                request.lastName().trim(),
                cleanPhone(request.phone())
        );
        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public MemberResponse update(UUID id, MemberRequest request) {
        MemberEntity member = getMember(id);
        member.updateDetails(
                request.firstName().trim(),
                request.lastName().trim(),
                cleanPhone(request.phone())
        );
        return MemberResponse.from(memberRepository.save(member));
    }

    private MemberEntity getMember(UUID id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
    }

    private String cleanPhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
