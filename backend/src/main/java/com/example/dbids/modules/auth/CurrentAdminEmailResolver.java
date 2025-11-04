package com.example.dbids.modules.auth;

import com.example.dbids.sqlite.model.AdminUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;

@Component
public class CurrentAdminEmailResolver {

    // 과거 AdminAuthController에 있던 세션 키를 여기서 독립적으로 정의
    private static final String SESSION_ADMIN_ID     = "ADMIN_ID";
    private static final String SESSION_ADMIN_EMAIL  = "ADMIN_EMAIL";
    private static final String SESSION_NOTIFY_EMAIL = "ADMIN_NOTIFY_EMAIL";

    // Stateless 모드 지원용 헤더 키
    private static final String HDR_NOTIFY_EMAIL = "X-Notify-Email";
    private static final String HDR_ADMIN_ID     = "X-Admin-Id";

    private final AdminUserService adminUserService;

    public CurrentAdminEmailResolver(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** 알림 수신 이메일을 결정한다.
     * 우선순위: (1) 요청 헤더 → (2) 세션 notify → (3) 세션 adminId로 DB → (4) 세션 loginEmail → (5) null
     */
    public String resolveNotifyEmailOrNull() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;

        HttpServletRequest req = attrs.getRequest();

        // 1) Stateless 헤더 우선
        String hdrNotify = trimOrNull(req.getHeader(HDR_NOTIFY_EMAIL));
        if (isEmail(hdrNotify)) return hdrNotify;

        // 2) 세션 경로 (세션이 있을 때만)
        HttpSession s = req.getSession(false);
        if (s != null) {
            Object notify = s.getAttribute(SESSION_NOTIFY_EMAIL);
            if (notify instanceof String ne && isEmail(ne)) return ne;

            String adminId = asString(s.getAttribute(SESSION_ADMIN_ID));
            if (adminId == null) {
                // 헤더로 adminId가 온 경우(세션 없는 stateless + 일부 정보 제공)도 고려
                adminId = trimOrNull(req.getHeader(HDR_ADMIN_ID));
            }
            if (adminId != null) {
                Optional<AdminUser> u = adminUserService.findById(adminId);
                if (u.isPresent() && isEmail(u.get().getEmail())) return u.get().getEmail();
            }

            Object loginEmail = s.getAttribute(SESSION_ADMIN_EMAIL);
            if (loginEmail instanceof String le && isEmail(le)) return le;
        } else {
            // 세션이 없다면 stateless 헤더의 adminId로 DB 조회 시도
            String adminId = trimOrNull(req.getHeader(HDR_ADMIN_ID));
            if (adminId != null) {
                Optional<AdminUser> u = adminUserService.findById(adminId);
                if (u.isPresent() && isEmail(u.get().getEmail())) return u.get().getEmail();
            }
        }

        return null;
    }

    private static String asString(Object o) {
        return (o instanceof String s) ? s : null;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isEmail(String s) {
        return s != null && !s.isBlank() && s.contains("@");
    }
}
