package com.bereta.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class BeretaUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public BeretaUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userAccountRepository.findByEmailIgnoreCase(email.trim())
                .map(BeretaUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants invalides"));
    }
}
