package com.bugs.productmanager.config;

import com.bugs.productmanager.model.AppUser;
import com.bugs.productmanager.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (appUserRepository.count() == 0) {
            AppUser admin = new AppUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ROLE_ADMIN");
            admin.setEnabled(true);
            appUserRepository.save(admin);

            AppUser bugs = new AppUser();
            bugs.setUsername("bugs");
            bugs.setPassword(passwordEncoder.encode("bugs123"));
            bugs.setRole("ROLE_DEPARTMENT");
            bugs.setEnabled(true);
            bugs.setCompany("BUGS");
            bugs.setDepartment("BUGS개발실");
            bugs.setTeam(null);
            appUserRepository.save(bugs);

            System.out.println("[DataInitializer] 기본 사용자 2명 생성 완료 (admin, bugs)");
        } else {
            // 기존 유저 역할/속성만 동기화 (비밀번호는 유지)
            appUserRepository.findByUsername("bugs").ifPresent(bugs -> {
                bugs.setRole("ROLE_DEPARTMENT");
                bugs.setCompany("BUGS");
                bugs.setDepartment("BUGS개발실");
                bugs.setTeam(null);
                appUserRepository.save(bugs);
            });
            appUserRepository.findByUsername("admin").ifPresent(admin -> {
                admin.setRole("ROLE_ADMIN");
                appUserRepository.save(admin);
            });
            System.out.println("[DataInitializer] 기존 사용자 속성 동기화 완료");
        }
    }
}
