package com.example.dbids.dto.admin;

import com.example.dbids.sqlite.model.AdminUser;

public class AdminDtos {

    public static class RegisterRequest {
        public String email;
        public String password;
        public String role; // "READER" / "WRITER" / "DBA"
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class ChangePasswordRequest {
        public String adminId;
        public String oldPassword;
        public String newPassword;
    }

    public static class AdminResponse {
        public String adminId;
        public String email;
        public String role;
        public String lastLogin;

        public static AdminResponse from(AdminUser u) {
            AdminResponse r = new AdminResponse();
            r.adminId = u.getId();
            r.email = u.getEmail();
            r.role = u.getRole().name();
            r.lastLogin = u.getLastLogin();
            return r;
        }
    }
}
