// src/pages/Events.tsx
import { useEffect, useState } from "react";
import { api } from "@/services/api";
import SeverityBadge from "@/components/SeverityBadge";

type BackendEvent = {
    id: string;
    logId: string;
    eventType: string;
    severity: "LOW" | "MEDIUM" | "HIGH" | string;
    occurredAt: string;
    userId?: string | null;
    adminId?: string | null;
    sqlPreview?: string | null;
};

type PageResp<T> = {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
};

export default function Events() {
    const [rows, setRows] = useState<BackendEvent[]>([]);
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(50);
    const [totalPages, setTotalPages] = useState(0);
    const [status, setStatus] = useState<"idle" | "loading" | "error">("idle");

    const fetchPage = async (p = page, s = size) => {
        setStatus("loading");
        try {
            const res = await api.get("/api/events", { params: { page: p, size: s } });
            const data: PageResp<BackendEvent> = Array.isArray(res.data)
                ? { content: res.data, page: 0, size: res.data.length, totalElements: res.data.length, totalPages: 1 }
                : res.data;

            setRows(data.content || []);
            setPage(data.page ?? p);
            setSize(data.size ?? s);
            setTotalPages(data.totalPages ?? 1);
            setStatus("idle");
        } catch {
            setRows([]);
            setStatus("error");
        }
    };

    useEffect(() => {
        fetchPage(0, size);
    }, []); // 첫 로드
    useEffect(() => {
        const id = setInterval(() => fetchPage(page, size), 5000); // 5초 폴링 (원하면 제거 가능)
        return () => clearInterval(id);
    }, [page, size]);

    const prev = () => {
        if (page > 0) fetchPage(page - 1, size);
    };
    const next = () => {
        if (page + 1 < totalPages) fetchPage(page + 1, size);
    };

    return (
        <div style={{ padding: 24 }}>
            <h1 style={{ fontSize: 32, fontWeight: 800, marginBottom: 8 }}>Detection Events — All</h1>

            <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
                <button onClick={() => fetchPage(page, size)} style={btn}>
                    Refresh
                </button>
                <span style={{ fontSize: 12, opacity: 0.7 }}>
                    {status === "loading" ? "갱신 중…" : status === "error" ? "불러오기 실패" : `page ${page + 1} / ${Math.max(totalPages, 1)}`}
                </span>
                <div style={{ marginLeft: "auto", display: "flex", gap: 6 }}>
                    <button onClick={prev} disabled={page <= 0} style={btn}>
                        ◀ Prev
                    </button>
                    <button onClick={next} disabled={page + 1 >= totalPages} style={btn}>
                        Next ▶
                    </button>
                </div>
            </div>

            <div style={{ border: "1px solid #2a2a2a", borderRadius: 12, overflow: "hidden" }}>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead>
                        <tr style={{ background: "#111" }}>
                            <th style={th}>time</th>
                            <th style={th}>user</th>
                            <th style={th}>type</th>
                            <th style={th}>severity</th>
                            <th style={th}>sql</th>
                        </tr>
                    </thead>
                    <tbody>
                        {rows.length === 0 && (
                            <tr>
                                <td colSpan={5} style={{ padding: 16, textAlign: "center", opacity: 0.7 }}>
                                    이벤트가 없습니다.
                                </td>
                            </tr>
                        )}
                        {rows.map((ev) => (
                            <tr key={ev.id} style={{ borderTop: "1px solid #2a2a2a" }}>
                                <td style={td}>{new Date(ev.occurredAt).toLocaleString()}</td>
                                <td style={td}>{ev.userId ?? "-"}</td>
                                <td style={td}>{ev.eventType}</td>
                                <td style={td}>
                                    <SeverityBadge severity={ev.severity} />
                                </td>
                                <td style={{ ...td, fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace" }}>
                                    <code>{ev.sqlPreview ?? ""}</code>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontWeight: 700, fontSize: 12, textTransform: "uppercase", letterSpacing: 0.4 };
const td: React.CSSProperties = { padding: "10px 12px", verticalAlign: "top" };
const btn: React.CSSProperties = { padding: "6px 10px", borderRadius: 8, border: "1px solid #333", background: "#111", cursor: "pointer" };
