package com.example.dbids.modules.events;

import com.example.dbids.dto.EventDtos;
import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.QueryLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class EventQueryService {

    @PersistenceContext(unitName = "sqliteUnit")
    private EntityManager em;

    public static class SearchCond {
        public String type;      // PATTERN|BEHAVIOR|AUTHZ (opt)
        public String severity;  // LOW|MEDIUM|HIGH (opt)
        public String from;      // ISO-8601 >= (opt)
        public String to;        // ISO-8601 <  (opt)
        public String user;      // contains (opt)
        public String adminId;   // exact (opt)
        public String q;         // SQL like keyword (opt)
        public int page = 0;
        public int size = 20;
    }

    @Transactional(readOnly = true, transactionManager = "sqliteTx")
    public EventDtos.Page<EventDtos.EventSummary> search(SearchCond c) {
        StringBuilder sb = new StringBuilder();
        StringBuilder cnt = new StringBuilder();
        Map<String,Object> p = new HashMap<>();

        // 조인: event e LEFT JOIN query_log q (userId, adminId 조건/출력용)
        sb.append("""
            SELECT e, q
            FROM DetectionEvent e
            LEFT JOIN QueryLog q ON q.id = e.logId
            WHERE 1=1
        """);
        cnt.append("""
            SELECT COUNT(e)
            FROM DetectionEvent e
            LEFT JOIN QueryLog q ON q.id = e.logId
            WHERE 1=1
        """);

        if (notBlank(c.type))     { sb.append(" AND e.eventType = :type");     cnt.append(" AND e.eventType = :type");     p.put("type", DetectionEvent.Type.valueOf(c.type)); }
        if (notBlank(c.severity)) { sb.append(" AND e.severity = :severity");  cnt.append(" AND e.severity = :severity");  p.put("severity", DetectionEvent.Severity.valueOf(c.severity)); }
        if (notBlank(c.from))     { sb.append(" AND e.occurredAt >= :from");   cnt.append(" AND e.occurredAt >= :from");   p.put("from", c.from); }
        if (notBlank(c.to))       { sb.append(" AND e.occurredAt < :to");      cnt.append(" AND e.occurredAt < :to");      p.put("to", c.to); }
        if (notBlank(c.user))     { sb.append(" AND q.userId LIKE :user");     cnt.append(" AND q.userId LIKE :user");     p.put("user", "%" + c.user + "%"); }
        if (notBlank(c.adminId))  { sb.append(" AND q.adminId = :adminId");    cnt.append(" AND q.adminId = :adminId");    p.put("adminId", c.adminId); }
        if (notBlank(c.q))        { sb.append(" AND e.sqlRaw LIKE :q");        cnt.append(" AND e.sqlRaw LIKE :q");        p.put("q", "%" + c.q + "%"); }

        sb.append(" ORDER BY e.occurredAt DESC");

        TypedQuery<Object[]> query = em.createQuery(sb.toString(), Object[].class);
        TypedQuery<Long> countQ = em.createQuery(cnt.toString(), Long.class);

        p.forEach((k,v) -> { query.setParameter(k,v); countQ.setParameter(k,v); });

        int page = Math.max(c.page, 0);
        int size = Math.min(Math.max(c.size, 1), 200);
        query.setFirstResult(page * size);
        query.setMaxResults(size);

        List<Object[]> rows = query.getResultList();
        List<EventDtos.EventSummary> list = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            DetectionEvent e = (DetectionEvent) r[0];
            QueryLog q = (QueryLog) r[1];
            list.add(new EventDtos.EventSummary(e, q));
        }

        long total = countQ.getSingleResult();
        return new EventDtos.Page<>(list, page, size, total);
    }

    @Transactional(readOnly = true, transactionManager = "sqliteTx")
    public Optional<EventDtos.EventDetail> findDetail(String id) {
        DetectionEvent e = em.find(DetectionEvent.class, id);
        if (e == null) return Optional.empty();
        QueryLog q = (e.getLogId() == null ? null : em.find(QueryLog.class, e.getLogId()));
        return Optional.of(new EventDtos.EventDetail(e, q));
    }

    private static boolean notBlank(String s){ return s != null && !s.trim().isEmpty(); }
}
