package com.bugs.productmanager.controller;

import com.bugs.productmanager.model.AppUser;
import com.bugs.productmanager.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class UserController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String list(Model model) {
        List<AppUser> users = appUserRepository.findAll();
        model.addAttribute("users", users);
        return "admin/user-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("user", new AppUser());
        model.addAttribute("isNew", true);
        return "admin/user-form";
    }

    @PostMapping
    public String save(@ModelAttribute AppUser user,
                       @RequestParam(required = false) String rawPassword,
                       RedirectAttributes redirectAttributes) {
        // 신규 저장
        if (user.getId() == null) {
            if (appUserRepository.existsByUsername(user.getUsername())) {
                redirectAttributes.addFlashAttribute("errorMsg", "이미 존재하는 아이디입니다.");
                return "redirect:/admin/users/new";
            }
            if (rawPassword == null || rawPassword.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMsg", "비밀번호를 입력해주세요.");
                return "redirect:/admin/users/new";
            }
            user.setPassword(passwordEncoder.encode(rawPassword.trim()));
            appUserRepository.save(user);
            redirectAttributes.addFlashAttribute("successMsg", "사용자가 등록되었습니다.");
        } else {
            // 수정
            AppUser existing = appUserRepository.findById(user.getId()).orElse(null);
            if (existing == null) {
                redirectAttributes.addFlashAttribute("errorMsg", "사용자를 찾을 수 없습니다.");
                return "redirect:/admin/users";
            }
            existing.setUsername(user.getUsername());
            existing.setRole(user.getRole());
            existing.setEnabled(user.isEnabled());
            // 비밀번호: 입력했으면 변경, 비어있으면 기존 유지
            if (rawPassword != null && !rawPassword.trim().isEmpty()) {
                existing.setPassword(passwordEncoder.encode(rawPassword.trim()));
            }
            appUserRepository.save(existing);
            redirectAttributes.addFlashAttribute("successMsg", "사용자 정보가 수정되었습니다.");
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        AppUser user = appUserRepository.findById(id).orElse(null);
        if (user == null) return "redirect:/admin/users";
        model.addAttribute("user", user);
        model.addAttribute("isNew", false);
        return "admin/user-form";
    }

    @GetMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        AppUser user = appUserRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "사용자를 찾을 수 없습니다.");
            return "redirect:/admin/users";
        }
        // 본인 삭제 방지
        if (user.getUsername().equals(auth.getName())) {
            redirectAttributes.addFlashAttribute("errorMsg", "본인 계정은 삭제할 수 없습니다.");
            return "redirect:/admin/users";
        }
        appUserRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMsg", "사용자가 삭제되었습니다.");
        return "redirect:/admin/users";
    }
}
