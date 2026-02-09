package com.bugs.productmanager.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LoginController {

    private final InMemoryUserDetailsManager userDetailsManager;
    private final PasswordEncoder passwordEncoder;

    public LoginController(InMemoryUserDetailsManager userDetailsManager, PasswordEncoder passwordEncoder) {
        this.userDetailsManager = userDetailsManager;
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

        // 현재 비밀번호 확인
        UserDetails user = userDetailsManager.loadUserByUsername(username);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
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

        // 비밀번호 변경
        userDetailsManager.changePassword(
                currentPassword,
                passwordEncoder.encode(newPassword)
        );

        redirectAttributes.addFlashAttribute("successMsg", "비밀번호가 변경되었습니다.");
        return "redirect:/change-password";
    }
}
