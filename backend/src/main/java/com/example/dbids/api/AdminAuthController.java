// src/main/java/com/example/dbids/api/AdminAuthController.java
package com.example.dbids.api;

import com.example.dbids.dto.admin.AdminDtos.AdminResponse;
import com.example.dbids.dto.admin.AdminDtos.ChangePasswordRequest;
import com.example.dbids.dto.admin.AdminDtos.RegisterRequest;
import com.example.dbids.modules.auth.AdminUserService;
import com.example.dbids.sqlite.model.AdminUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AdminAuthController {

    private final AdminUserService service;

    public AdminAuthController(AdminUserService service) {
        this.service = service;
    }

    /** 초기 관리자 생성 (운영 환경에서는 보호 필요) */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            if (req == null || req.email == null || req.password == null || req.role == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REQUEST"));
            }
            AdminUser.Role role = AdminUser.Role.valueOf(req.role.toUpperCase());
            String id = service.register(req.email.trim().toLowerCase(), req.password, role);
            return ResponseEntity.ok(Map.of("adminId", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 로그인: 세션/쿠키 없이 성공 시 프로필만 반환 (프론트가 로컬 스토리지에 보관) */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        if (body == null) return unauthorized();
        Object e = body.get("email");
        Object p = body.get("password");
        String email = (e == null) ? null : String.valueOf(e).trim().toLowerCase();
        String password = (p == null) ? null : String.valueOf(p);

        if (email == null || email.isEmpty() || password == null) return unauthorized();

        return service.login(email, password)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(AdminResponse.from(u)))
                .orElseGet(this::unauthorized);
    }

    /** 로그아웃: 서버 상태 없음(프론트만 로컬 스토리지 지움) */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("status", "LOGGED_OUT"));
    }

    /** 비밀번호 변경: adminId 기준 */
    @PatchMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req) {
        if (req == null || req.adminId == null || req.oldPassword == null || req.newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REQUEST"));
        }
        boolean ok = service.changePassword(req.adminId, req.oldPassword, req.newPassword);
        return ok
                ? ResponseEntity.ok(Map.of("status", "OK"))
                : ResponseEntity.badRequest().body(Map.of("error", "INVALID_OLD_PASSWORD"));
    }

    /** 내 정보 조회: adminId로 조회 */
    @GetMapping("/me/{adminId}")
    public ResponseEntity<?> me(@PathVariable String adminId) {
        return service.findById(adminId)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(AdminResponse.from(u)))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND")));
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "INVALID_CREDENTIALS"));
    }
}
