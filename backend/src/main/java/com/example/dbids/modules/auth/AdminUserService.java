package com.example.dbids.modules.auth;

import com.example.dbids.sqlite.model.AdminUser;
import com.example.dbids.sqlite.repository.AdminUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminUserService {

    private final AdminUserRepository repo;

    public AdminUserService(AdminUserRepository repo) {
        this.repo = repo;
    }

    /* -------- 기존 회원가입/로그인/비번변경 그대로 -------- */

    @Transactional("sqliteTx")
    public String register(String email, String rawPassword, AdminUser.Role role) {
        if (repo.existsByEmail(email)) {
            throw new IllegalArgumentException("EMAIL_ALREADY_EXISTS");
        }
        String id = UUID.randomUUID().toString();
        String hash = PasswordHasher.sha256Hex(rawPassword);
        AdminUser u = new AdminUser(id, email, hash, role, null);
        repo.save(u);
        return id;
    }

    @Transactional("sqliteTx")
    public Optional<AdminUser> login(String email, String rawPassword) {
        return repo.findByEmail(email).filter(u -> {
            String hash = PasswordHasher.sha256Hex(rawPassword);
            boolean ok = hash.equalsIgnoreCase(u.getPasswordHash());
            if (ok) {
                u.setLastLogin(Instant.now().toString());
                repo.save(u);
            }
            return ok;
        });
    }

    @Transactional("sqliteTx")
    public boolean changePassword(String adminId, String oldRaw, String newRaw) {
        return repo.findById(adminId).map(u -> {
            String oldHash = PasswordHasher.sha256Hex(oldRaw);
            if (!oldHash.equalsIgnoreCase(u.getPasswordHash())) return false;
            u.setPasswordHash(PasswordHasher.sha256Hex(newRaw));
            repo.save(u);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true, transactionManager = "sqliteTx")
    public Optional<AdminUser> findById(String adminId) {
        return repo.findById(adminId);
    }

    @Transactional(readOnly = true, transactionManager = "sqliteTx")
    public Optional<AdminUser> findByEmail(String email) {
        return repo.findByEmail(email.toLowerCase());
    }

    @Transactional("sqliteTx")
    public void updateContacts(String currentEmail,
            String newEmailOrNull,
            String slackWebhookOrNull) {
        if (currentEmail == null || currentEmail.isBlank())
            throw new IllegalArgumentException("NOT_LOGGED_IN");

        AdminUser u = repo.findByEmail(currentEmail.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("ADMIN_NOT_FOUND_BY_EMAIL"));

        boolean changed = false;

        // 이메일 변경
        if (newEmailOrNull != null && !newEmailOrNull.equalsIgnoreCase(u.getEmail())) {
            if (repo.existsByEmail(newEmailOrNull))
                throw new IllegalArgumentException("EMAIL_ALREADY_EXISTS");
            u.setEmail(newEmailOrNull);
            changed = true;
        }

        // 슬랙 웹훅 변경 (빈문자는 컨트롤러/DTO에서 null로 걸러짐 → 여기선 미변경)
        if (slackWebhookOrNull != null &&
                (u.getSlackWebhook() == null || !slackWebhookOrNull.equals(u.getSlackWebhook()))) {
            u.setSlackWebhook(slackWebhookOrNull);
            changed = true;
        }

        if (changed) repo.save(u);
    }

    /* -------- 신규: 현재 이메일 기준으로 admin_user.email 갱신 (stateless) -------- */

    /**
     * @param currentEmail 현재 로그인한 관리자 이메일 (프런트 헤더로 전달)
     * @param newEmail     갱신할 이메일 (검증 완료·빈문자 불가)
     */
    @Transactional("sqliteTx")
    public void updateEmailByCurrentEmail(String currentEmail, String newEmail) {
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new IllegalArgumentException("NOT_LOGGED_IN");
        }
        String cur = currentEmail.trim().toLowerCase();
        String next = newEmail.trim().toLowerCase();

        AdminUser admin = repo.findByEmail(cur)
                .orElseThrow(() -> new IllegalArgumentException("ADMIN_NOT_FOUND_BY_EMAIL"));

        // 유니크 충돌 검사
        repo.findByEmail(next).ifPresent(conflict -> {
            if (!conflict.getId().equals(admin.getId())) {
                throw new IllegalArgumentException("EMAIL_ALREADY_EXISTS");
            }
        });

        admin.setEmail(next);
        repo.save(admin);
    }
}
