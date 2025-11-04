package com.example.dbids.modules.storage;

import com.example.dbids.dto.QueryLogDTO;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.AdminUserRepository;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock QueryLogRepository repo;
    @Mock AdminUserRepository adminRepo;
    @InjectMocks StorageService storage;

    private static final String ADMIN_ID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setup() {
        // FK 존재 스텁(대부분의 케이스에서 adminId 검증을 통과시키기 위함)
        when(adminRepo.existsById(ADMIN_ID)).thenReturn(true);
    }

    private QueryLogDTO base() {
        return new QueryLogDTO(null, "dba@example.com", ADMIN_ID,
                "SELECT 1", "ping", 1, "SUCCESS");
    }

    @Test
    @DisplayName("UT-11: 정상 insert → repo.save 1회, id 반환")
    void insertOk() {
        when(repo.save(any(QueryLog.class))).thenAnswer(inv -> inv.getArgument(0));

        String id = storage.saveQueryLog(base());
        assertNotNull(id);
        verify(repo, times(1)).save(any(QueryLog.class));
    }

    @Test
    @DisplayName("UT-11: 필수 누락/음수/빈값 → IllegalArgumentException")
    void missingOrInvalidThrows() {
        // userId 공백 → 가장 먼저 userId 검증에서 실패
        QueryLogDTO d1 = base(); d1.userId = " ";
        assertThrows(IllegalArgumentException.class, () -> storage.saveQueryLog(d1));

        // sqlRaw null
        QueryLogDTO d2 = base(); d2.sqlRaw = null;
        assertThrows(IllegalArgumentException.class, () -> storage.saveQueryLog(d2));

        // returnRows 음수
        QueryLogDTO d3 = base(); d3.returnRows = -1;
        assertThrows(IllegalArgumentException.class, () -> storage.saveQueryLog(d3));

        // status 공백
        QueryLogDTO d4 = base(); d4.status = "";
        assertThrows(IllegalArgumentException.class, () -> storage.saveQueryLog(d4));

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("UT-11: 시간 포맷 오류 → invalid executedAt")
    void invalidTimestamp() {
        QueryLogDTO d = base(); d.executedAt = "2025/10/21 12:00";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storage.saveQueryLog(d));
        assertTrue(ex.getMessage().contains("invalid executedAt"));
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("UT-11: 잘못된 status → invalid status")
    void invalidStatus() {
        QueryLogDTO d = base(); d.status = "DONE";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storage.saveQueryLog(d));
        assertTrue(ex.getMessage().contains("invalid status"));
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("UT-11: sqlRaw 8KB 초과는 절단 저장")
    void sqlRawTrimTo8KB() {
        String huge = "A".repeat(9000);
        final QueryLog[] captured = new QueryLog[1];
        when(repo.save(any(QueryLog.class))).thenAnswer(inv -> { captured[0]=inv.getArgument(0); return captured[0]; });

        QueryLogDTO d = base(); d.sqlRaw = huge;
        storage.saveQueryLog(d);

        assertNotNull(captured[0]);
        assertEquals(8192, captured[0].getSqlRaw().length());
    }

    @Test
    @DisplayName("UT-11: sqlSummary 512 초과는 절단 저장")
    void sqlSummaryTrimTo512() {
        String huge = "B".repeat(600);
        final QueryLog[] captured = new QueryLog[1];
        when(repo.save(any(QueryLog.class))).thenAnswer(inv -> { captured[0]=inv.getArgument(0); return captured[0]; });

        QueryLogDTO d = base(); d.sqlSummary = huge;
        storage.saveQueryLog(d);

        assertNotNull(captured[0]);
        assertEquals(512, captured[0].getSqlSummary().length());
    }

    // (옵션) adminId 관련 에러 케이스도 하나 추가해두면 좋음
    @Test
    @DisplayName("UT-11: adminId가 DB에 없으면 adminId not found")
    void adminIdNotFound() {
        when(adminRepo.existsById(ADMIN_ID)).thenReturn(false);
        QueryLogDTO d = base();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storage.saveQueryLog(d));
        assertTrue(ex.getMessage().contains("adminId not found"));
        verify(repo, never()).save(any());
    }
}
