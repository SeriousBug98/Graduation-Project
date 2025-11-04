package com.example.dbids.modules.storage;

import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(20) // 다른 초기화 이후
public class SqlSummaryBackfillRunner implements CommandLineRunner {

    private final QueryLogRepository repo;

    public SqlSummaryBackfillRunner(QueryLogRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<QueryLog> all = repo.findAll(); // 데이터 많으면 페이징으로
        List<QueryLog> dirty = new ArrayList<>();
        for (QueryLog q : all) {
            if (q.getSqlSummary() == null || q.getSqlSummary().isBlank()) {
                String s = SqlSummarizer.summarize(q.getSqlRaw());
                // 엔티티에 setter가 없으면 새로운 인스턴스를 만들거나, setter 추가
                // 지금은 생성자만 있으니 setter를 하나 추가하는 걸 권장
                // 예: q.setSqlSummary(s);
                try {
                    var f = QueryLog.class.getDeclaredField("sqlSummary");
                    f.setAccessible(true);
                    f.set(q, s);
                } catch (Exception ignore) {}
                dirty.add(q);
            }
        }
        if (!dirty.isEmpty()) repo.saveAll(dirty);
    }
}
