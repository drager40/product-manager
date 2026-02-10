package com.bugs.productmanager.controller;

import com.bugs.productmanager.model.AppUser;
import com.bugs.productmanager.repository.AppUserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class LoginController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginController(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/change-password")
    public String changePasswordForm() {
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        String username = auth.getName();

        AppUser appUser = appUserRepository.findByUsername(username).orElse(null);
        if (appUser == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "사용자를 찾을 수 없습니다.");
            return "redirect:/change-password";
        }

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(currentPassword, appUser.getPassword())) {
            redirectAttributes.addFlashAttribute("errorMsg", "현재 비밀번호가 일치하지 않습니다.");
            return "redirect:/change-password";
        }

        // 새 비밀번호 확인
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMsg", "새 비밀번호가 일치하지 않습니다.");
            return "redirect:/change-password";
        }

        if (newPassword.length() < 4) {
            redirectAttributes.addFlashAttribute("errorMsg", "비밀번호는 4자 이상이어야 합니다.");
            return "redirect:/change-password";
        }

        // DB에 비밀번호 업데이트
        appUser.setPassword(passwordEncoder.encode(newPassword));
        appUserRepository.save(appUser);

        // SecurityContext 갱신
        UserDetails updatedUser = new User(
                appUser.getUsername(),
                appUser.getPassword(),
                appUser.isEnabled(),
                true, true, true,
                List.of(new SimpleGrantedAuthority(appUser.getRole()))
        );
        Authentication newAuth = new UsernamePasswordAuthenticationToken(
                updatedUser, updatedUser.getPassword(), updatedUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuth);

        redirectAttributes.addFlashAttribute("successMsg", "비밀번호가 변경되었습니다.");
        return "redirect:/change-password";
    }
}
