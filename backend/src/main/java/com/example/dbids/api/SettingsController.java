package com.example.dbids.api;

import com.example.dbids.dto.AlertSettingDTO;
import com.example.dbids.modules.auth.AdminUserService;
import com.example.dbids.sqlite.model.AdminUser;
import com.example.dbids.sqlite.repository.AdminUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final AdminUserService adminService;
    private final AdminUserRepository adminRepo;

    public SettingsController(AdminUserService adminService,
            AdminUserRepository adminRepo) {
        this.adminService = adminService;
        this.adminRepo = adminRepo;
    }

    /** 조회: 현재 로그인 이메일 기준으로만 반환 (Slack은 저장소가 없으므로 항상 null) */
    @GetMapping("/alerts")
    public ResponseEntity<?> getAlerts(HttpServletRequest request) {
        String currentEmail = headerLower(request, "X-Admin-Email");
        if (currentEmail == null || currentEmail.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "NOT_LOGGED_IN"));
        }
        AdminUser u = adminRepo.findByEmail(currentEmail.trim().toLowerCase())
                .orElse(null);
        if (u == null) {
            return ResponseEntity.status(404).body(Map.of("error", "ADMIN_NOT_FOUND_BY_EMAIL"));
        }
        // Slack 저장소가 없으므로 null 고정
        return ResponseEntity.ok(new AlertSettingDTO(u.getEmail(), null));
    }

    /**
     * 변경: email만 실제로 갱신 (admin_user.email)
     *      slackWebhook은 저장소가 없으므로 키가 와도 무시(유지)
     */
    @PatchMapping("/alerts")
    public ResponseEntity<?> patchAlerts(@RequestBody AlertSettingDTO dto,
            HttpServletRequest request) {
        String currentEmail = headerLower(request, "X-Admin-Email");
        if (currentEmail == null || currentEmail.isBlank())
            return ResponseEntity.status(401).body(Map.of("error", "NOT_LOGGED_IN"));

        String newEmail = null;
        String slack = null;
        try {
            newEmail = dto.normalizedEmailOrNull();   // 빈문자 → null → 미변경
            slack    = dto.normalizedSlackOrNull();   // 빈문자 → null → 미변경
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        try {
            adminService.updateContacts(currentEmail, newEmail, slack);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            int status = switch (msg) {
                case "NOT_LOGGED_IN" -> 401;
                case "ADMIN_NOT_FOUND_BY_EMAIL" -> 404;
                case "EMAIL_ALREADY_EXISTS" -> 400;
                default -> 400;
            };
            return ResponseEntity.status(status).body(Map.of("error", msg));
        }

        // 변경 후 최신 레코드 조회 (이메일이 바뀌었으면 그걸로)
        String finalEmail = (newEmail != null) ? newEmail : currentEmail;
        return adminService.findByEmail(finalEmail)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(
                        Map.of("email", u.getEmail(), "slackWebhook", u.getSlackWebhook())))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "ADMIN_NOT_FOUND_BY_EMAIL")));
    }

    private static String headerLower(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return v == null ? null : v.trim().toLowerCase();
    }
}
