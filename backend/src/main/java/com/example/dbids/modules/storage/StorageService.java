package com.example.dbids.modules.storage;

import com.example.dbids.dto.QueryLogDTO;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.AdminUserRepository;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class StorageService {
    private static final int SQL_RAW_MAX = 8192;
    private static final int SQL_SUMMARY_MAX = 512;
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$");

    private final QueryLogRepository repo;
    private final AdminUserRepository adminRepo;

    public StorageService(QueryLogRepository repo, AdminUserRepository adminRepo) {
        this.repo = repo;
        this.adminRepo = adminRepo;
    }

    @Transactional
    public QueryLog saveLog(String id, String executedAt, String userId,
            String adminId, String sqlRaw, String sqlSummary,
            int returnRows, QueryLog.Status status) {

        String summary = (sqlSummary != null && !sqlSummary.isBlank())
                ? sqlSummary
                : SqlSummarizer.summarize(sqlRaw);

        QueryLog q = new QueryLog(id, executedAt, userId, adminId, sqlRaw, summary, returnRows, status);
        return repo.save(q);
    }

    @Transactional("sqliteTx")
    public String saveQueryLog(QueryLogDTO d) {
        if (d == null) throw new IllegalArgumentException("null dto");

        // --- 필수값 검증: userId(이메일), adminId(UUID+존재), sqlRaw, returnRows, status ---
        if (blank(d.userId)) throw new IllegalArgumentException("userId is required");
        if (!EMAIL.matcher(d.userId).matches()) throw new IllegalArgumentException("userId must be email");
//
//        if (blank(d.adminId)) throw new IllegalArgumentException("adminId is required");
//        try { UUID.fromString(d.adminId); } catch (Exception e) {
//            throw new IllegalArgumentException("adminId must be UUID");
//        }
//        if (!adminRepo.existsById(d.adminId)) throw new IllegalArgumentException("adminId not found");

        if (blank(d.sqlRaw)) throw new IllegalArgumentException("sqlRaw is required");
        if (d.returnRows == null || d.returnRows < 0) throw new IllegalArgumentException("returnRows must be >= 0");
        if (blank(d.status)) throw new IllegalArgumentException("status is required");

        // --- 길이 제한 & 시간 보정 ---
        String raw = d.sqlRaw.length() > SQL_RAW_MAX ? d.sqlRaw.substring(0, SQL_RAW_MAX) : d.sqlRaw;
        String sum = d.sqlSummary == null ? null :
                (d.sqlSummary.length() > SQL_SUMMARY_MAX ? d.sqlSummary.substring(0, SQL_SUMMARY_MAX) : d.sqlSummary);

        String executedIso;
        if (blank(d.executedAt)) {
            executedIso = Instant.now().toString();
        } else {
            try { Instant.parse(d.executedAt); executedIso = d.executedAt; }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("invalid executedAt"); }
        }

        QueryLog.Status st;
        try { st = QueryLog.Status.valueOf(d.status.toUpperCase()); }
        catch (Exception e) { throw new IllegalArgumentException("invalid status"); }

        // --- 저장: user_id=이메일 문자열, admin_id=UUID(FK) ---
        String logId = UUID.randomUUID().toString();
        QueryLog entity = new QueryLog(
                logId,
                executedIso,
                d.userId,      // 이메일
                d.adminId,     // FK(UUID)
                raw,
                sum,
                d.returnRows,
                st
        );
        repo.save(entity);
        return logId;
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
