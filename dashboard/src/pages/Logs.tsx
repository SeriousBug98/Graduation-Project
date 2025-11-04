// src/pages/Logs.tsx
import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/services/api";
import StatusBadge from "@/components/StatusBadge";
import type { QueryLogResponse } from "@/types/entities";

type PageResp<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
type SortField = "executedAt" | "userId" | "status" | "returnRows";
type SortDir = "ASC" | "DESC";

const ALL_STATUSES = ["SUCCESS", "FAILURE"] as const;

export default function Logs() {
    // ===== 검색/필터 =====
    const [keywords, setKeywords] = useState("");
    const [user, setUser] = useState("");
    const [statuses, setStatuses] = useState<string[]>([]);
    const [from, setFrom] = useState("");
    const [to, setTo] = useState("");
    const [rowsMin, setRowsMin] = useState("");
    const [rowsMax, setRowsMax] = useState("");

    // ===== 페이지/정렬 =====
    const [rows, setRows] = useState<QueryLogResponse[]>([]);
    const [page, setPage] = useState(0); // 0-based
    const [size, setSize] = useState(20);
    const [totalPages, setTotalPages] = useState(1);
    const [sortField, setSortField] = useState<SortField>("executedAt");
    const [sortDir, setSortDir] = useState<SortDir>("DESC");
    const [loading, setLoading] = useState(false);

    // ===== 행 확장(요약/원문) & 자동새로고침 제어 =====
    const [expandedId, setExpandedId] = useState<string | null>(null);
    const expandedRef = useRef<string | null>(expandedId);
    expandedRef.current = expandedId;

    const [autoRefresh, setAutoRefresh] = useState(true); // ⬅️ 토글 가능

    const debounceId = useRef<number | null>(null);

    // 컨트롤러와 1:1 파라미터 (page는 호출 시에 주입)
    const baseParams = useMemo(() => {
        const p: Record<string, any> = { size, sort: `${sortField},${sortDir}` };
        if (keywords.trim()) p.keywords = keywords.trim();
        if (user.trim()) p.user = user.trim();
        if (statuses.length) p.status = statuses;
        if (from) p.from = from;
        if (to) p.to = to;
        if (rowsMin.trim()) p.rowsMin = Number(rowsMin);
        if (rowsMax.trim()) p.rowsMax = Number(rowsMax);
        return p;
    }, [keywords, user, statuses, from, to, rowsMin, rowsMax, size, sortField, sortDir]);

    // ✅ 특정 페이지 유지해서 로드 + expanded 행 유지
    const fetchPageAt = async (targetPage: number) => {
        setLoading(true);
        try {
            const res = await api.get("/api/logs", { params: { ...baseParams, page: targetPage } });
            const data: PageResp<QueryLogResponse> = Array.isArray(res.data)
                ? { content: res.data, page: 0, size: res.data.length, totalElements: res.data.length, totalPages: 1 }
                : res.data;

            const nextRows = data.content ?? [];
            setRows(nextRows);
            setPage(data.page ?? targetPage);
            setSize(data.size ?? size);
            setTotalPages(data.totalPages ?? 1);

            // ⬇️ 자동 새로고침 시에도, 펼쳐둔 행이 여전히 존재하면 유지
            const keep = expandedRef.current;
            if (keep && !nextRows.some((r) => r.id === keep)) {
                setExpandedId(null); // 더 이상 없으면 닫음
            }
            // 존재하면 그대로 두기(닫지 않음)
        } finally {
            setLoading(false);
        }
    };

    // 초기 1회
    useEffect(() => {
        fetchPageAt(0); /* eslint-disable-next-line */
    }, []);

    // 필터/정렬/페이지당 개수 변경 시: 1페이지부터
    useEffect(() => {
        if (debounceId.current) window.clearTimeout(debounceId.current);
        debounceId.current = window.setTimeout(() => fetchPageAt(0), 300);
        return () => {
            if (debounceId.current) window.clearTimeout(debounceId.current);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [keywords, user, statuses, from, to, rowsMin, rowsMax, sortField, sortDir, size]);

    // ⏱ 자동 새로고침: 현재 page 유지 + 토글 가능
    useEffect(() => {
        if (!autoRefresh) return;
        const id = setInterval(() => fetchPageAt(page), 5000);
        return () => clearInterval(id);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [page, baseParams, autoRefresh]);

    const goPrev = () => page > 0 && fetchPageAt(page - 1);
    const goNext = () => page + 1 < totalPages && fetchPageAt(page + 1);

    const toggleSort = (field: SortField) => {
        if (sortField === field) setSortDir((d) => (d === "ASC" ? "DESC" : "ASC"));
        else {
            setSortField(field);
            setSortDir("ASC");
        }
    };

    const onToggleStatus = (s: string) => setStatuses((arr) => (arr.includes(s) ? arr.filter((x) => x !== s) : [...arr, s]));

    const onDownload = async () => {
        try {
            const res = await api.get("/api/logs/export", { params: { ...baseParams, page }, responseType: "blob" });
            const blob = new Blob([res.data], { type: res.headers["content-type"] ?? "text/csv;charset=UTF-8" });
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            const cd = res.headers["content-disposition"] as string | undefined;
            const name = cd?.match(/filename="?([^"]+)"?/)?.[1] ?? `query_logs_${new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-")}.csv`;
            a.href = url;
            a.download = name;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(url);
        } catch {
            alert("다운로드에 실패했습니다.");
        }
    };

    const copy = async (text: string) => {
        try {
            await navigator.clipboard.writeText(text);
            alert("복사되었습니다.");
        } catch {
            alert("복사에 실패했습니다.");
        }
    };
    const downloadSql = (filename: string, sql: string) => {
        const blob = new Blob([sql], { type: "text/sql;charset=UTF-8" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    };

    const isPrevDisabled = page <= 0;
    const isNextDisabled = page + 1 >= totalPages;

    return (
        <div style={{ padding: 24 }}>
            <h1 style={{ fontSize: 32, fontWeight: 800, marginBottom: 16 }}>Query Logs</h1>

            {/* 필터 바 */}
            <div style={{ display: "grid", gap: 8, marginBottom: 12, gridTemplateColumns: "1.3fr 1fr 1fr auto", alignItems: "center" }}>
                <div style={{ display: "flex", gap: 8 }}>
                    <Input placeholder="keywords (SQL, 사용자 등)" value={keywords} onChange={setKeywords} />
                    <Input placeholder="user (또는 email)" value={user} onChange={setUser} />
                </div>
                <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                    {ALL_STATUSES.map((s) => (
                        <label key={s} style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12 }}>
                            <input type="checkbox" checked={statuses.includes(s)} onChange={() => onToggleStatus(s)} />
                            {s}
                        </label>
                    ))}
                </div>
                <div />
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                    <button onClick={() => fetchPageAt(0)} style={btn}>
                        Search
                    </button>
                    <button onClick={onDownload} style={btn}>
                        Download
                    </button>
                    <button onClick={() => setAutoRefresh((v) => !v)} style={btn}>
                        {autoRefresh ? "⏸ Auto refresh" : "▶ Auto refresh"}
                    </button>
                </div>

                <div style={{ display: "flex", gap: 8 }}>
                    <Input type="date" value={from} onChange={(v) => setFrom(v)} />
                    <Input type="date" value={to} onChange={(v) => setTo(v)} />
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                    <Input type="number" placeholder="rowsMin" value={rowsMin} onChange={setRowsMin} />
                    <Input type="number" placeholder="rowsMax" value={rowsMax} onChange={setRowsMax} />
                </div>
                <div />
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", alignItems: "center" }}>
                    <select value={size} onChange={(e) => setSize(Number(e.target.value))} style={input as any}>
                        {[20, 50, 100, 200].map((n) => (
                            <option key={n} value={n}>
                                {n}/page
                            </option>
                        ))}
                    </select>
                    <span style={{ fontSize: 12, opacity: 0.7 }}>{loading ? "불러오는 중…" : `page ${page + 1} / ${Math.max(totalPages, 1)}`}</span>
                    <button onClick={goPrev} disabled={isPrevDisabled} style={isPrevDisabled ? btnDisabled : btn}>
                        ◀ Prev
                    </button>
                    <button onClick={goNext} disabled={isNextDisabled} style={isNextDisabled ? btnDisabled : btn}>
                        Next ▶
                    </button>
                </div>
            </div>

            {/* 테이블 */}
            <div style={{ overflowX: "auto", border: "1px solid #2a2a2a", borderRadius: 12 }}>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead>
                        <tr style={{ background: "#111" }}>
                            <Th label="TIME" sortKey="executedAt" activeKey={sortField} dir={sortDir} onSort={(f) => toggleSort(f as SortField)} />
                            <Th label="USER" sortKey="userId" activeKey={sortField} dir={sortDir} onSort={(f) => toggleSort(f as SortField)} />
                            <Th label="STATUS" sortKey="status" activeKey={sortField} dir={sortDir} onSort={(f) => toggleSort(f as SortField)} />
                            <Th label="ROWS" sortKey="returnRows" activeKey={sortField} dir={sortDir} onSort={(f) => toggleSort(f as SortField)} />
                            <th style={th}>SQL (summary)</th>
                            <th style={th}>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        {rows.length === 0 && (
                            <tr>
                                <td colSpan={6} style={{ padding: 16, textAlign: "center", opacity: 0.7 }}>
                                    로그가 없습니다.
                                </td>
                            </tr>
                        )}
                        {rows.map((row) => {
                            const summary = row.sqlSummary ?? "";
                            const raw = (row as any).sqlRaw ?? (row as any).sql ?? (row as any).sqlFull ?? "";
                            const isOpen = expandedId === row.id;
                            return (
                                <>
                                    <tr key={row.id} style={{ borderTop: "1px solid #2a2a2a" }}>
                                        <td style={td}>{new Date(row.executedAt).toLocaleString()}</td>
                                        <td style={td}>{row.userId}</td>
                                        <td style={td}>
                                            <StatusBadge status={row.status} />
                                        </td>
                                        <td style={td}>{row.returnRows}</td>
                                        <td
                                            style={{
                                                ...td,
                                                fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
                                                maxWidth: 520,
                                                whiteSpace: "nowrap",
                                                textOverflow: "ellipsis",
                                                overflow: "hidden",
                                            }}>
                                            <code title={summary}>{summary}</code>
                                        </td>
                                        <td style={td}>
                                            <button onClick={() => setExpandedId(isOpen ? null : row.id)} style={btnSmall} title="요약/원문 보기">
                                                {isOpen ? "Hide" : "View"}
                                            </button>
                                        </td>
                                    </tr>

                                    {isOpen && (
                                        <tr>
                                            <td colSpan={6} style={{ padding: 0, background: "#0b0b0b" }}>
                                                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 0, borderTop: "1px solid #2a2a2a" }}>
                                                    {/* 요약 */}
                                                    <div style={{ padding: 12, borderRight: "1px solid #2a2a2a" }}>
                                                        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
                                                            <strong>SQL Summary</strong>
                                                            <div style={{ display: "flex", gap: 6 }}>
                                                                <button style={btnTiny} onClick={() => copy(summary)}>
                                                                    Copy
                                                                </button>
                                                            </div>
                                                        </div>
                                                        <pre style={pre}>
                                                            <code>{summary}</code>
                                                        </pre>
                                                    </div>
                                                    {/* 원문 */}
                                                    <div style={{ padding: 12 }}>
                                                        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
                                                            <strong>SQL Raw</strong>
                                                            <div style={{ display: "flex", gap: 6 }}>
                                                                <button style={btnTiny} onClick={() => copy(raw)}>
                                                                    Copy
                                                                </button>
                                                                <button style={btnTiny} onClick={() => downloadSql(`query_${row.id}.sql`, raw)}>
                                                                    Download .sql
                                                                </button>
                                                            </div>
                                                        </div>
                                                        <pre style={pre}>
                                                            <code>{raw}</code>
                                                        </pre>
                                                    </div>
                                                </div>
                                            </td>
                                        </tr>
                                    )}
                                </>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

function Th({ label, sortKey, activeKey, dir, onSort }: { label: string; sortKey: string; activeKey: string; dir: SortDir; onSort: (f: string) => void }) {
    const isActive = activeKey === sortKey;
    return (
        <th onClick={() => onSort(sortKey)} style={{ ...th, cursor: "pointer", userSelect: "none" }} title="정렬">
            {label} <span style={{ opacity: isActive ? 1 : 0.35 }}>{isActive ? (dir === "ASC" ? "▲" : "▼") : "↕"}</span>
        </th>
    );
}

function Input(props: Omit<React.InputHTMLAttributes<HTMLInputElement>, 'onChange'> & { onChange?: (v: string) => void }) {
    const { onChange, ...rest } = props;
    return <input {...rest} onChange={(e) => onChange?.(e.target.value)} style={input} />;
}

const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontWeight: 700, fontSize: 12, textTransform: "uppercase", letterSpacing: 0.4 };
const td: React.CSSProperties = { padding: "10px 12px", verticalAlign: "top" };
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", borderRadius: 8, border: "1px solid #333", background: "#111", color: "inherit" };
const btn: React.CSSProperties = { padding: "8px 10px", borderRadius: 8, border: "1px solid #333", background: "#111", cursor: "pointer" };
const btnDisabled: React.CSSProperties = { ...btn, opacity: 0.45, cursor: "not-allowed" };
const btnSmall: React.CSSProperties = { padding: "6px 10px", borderRadius: 8, border: "1px solid #333", background: "#111", cursor: "pointer", fontSize: 12 };
const btnTiny: React.CSSProperties = { padding: "4px 8px", borderRadius: 6, border: "1px solid #333", background: "#111", cursor: "pointer", fontSize: 11 };
const pre: React.CSSProperties = {
    margin: 0,
    padding: 12,
    background: "#0f172a",
    border: "1px solid #1f2937",
    borderRadius: 8,
    whiteSpace: "pre-wrap",
    wordBreak: "break-word",
    fontFamily: "ui-monospace,SFMono-Regular,Menlo,monospace",
    fontSize: 12,
    lineHeight: 1.4,
};
