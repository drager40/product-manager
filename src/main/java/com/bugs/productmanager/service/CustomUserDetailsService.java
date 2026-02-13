package com.bugs.productmanager.service;

import com.bugs.productmanager.config.CustomUserPrincipal;
import com.bugs.productmanager.model.AppUser;
import com.bugs.productmanager.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return new CustomUserPrincipal(
                appUser.getUsername(),
                appUser.getPassword(),
                appUser.isEnabled(),
                List.of(new SimpleGrantedAuthority(appUser.getRole())),
                appUser.getCompany(),
                appUser.getDepartment(),
                appUser.getTeam()
        );
    }
}
