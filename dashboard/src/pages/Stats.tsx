// src/pages/Stats.tsx
import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/services/api";
import { BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, LineChart, Line, Legend } from "recharts";
import type { QueryLogResponse } from "@/types/entities";

type PageResp<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export default function Stats() {
    const [byUser, setByUser] = useState<Array<{ userId: string; count: number }> | null>(null);
    const [byHour, setByHour] = useState<Array<{ hourLabel: string; count: number }> | null>(null);
    const [from, setFrom] = useState<string>(defaultFrom()); // 기본 7일
    const [to, setTo] = useState<string>(today());
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string>("");

    const debRef = useRef<number | null>(null);

    const fetchSummary = async (f: string, t: string) => {
        setLoading(true);
        setError("");
        try {
            // 1) 서버 요약 우선 (래핑/키이름 다양한 경우 모두 수용)
            let users: Array<{ userId: string; count: number }> = [];
            let hours: Array<{ hourLabel: string; count: number }> = [];

            try {
                const u = await api.get("/api/logs/summary", { params: { by: "user", from: f || undefined, to: t || undefined } });
                const arr = pickArray(u.data, ["users", "user", "data", "content"]);
                users = normalizeUserBuckets(arr);
            } catch {}

            try {
                const h = await api.get("/api/logs/summary", { params: { by: "hour", from: f || undefined, to: t || undefined } });
                const arr = pickArray(h.data, ["hours", "hour", "data", "content"]);
                hours = normalizeHourBuckets(arr);
            } catch {}

            // 2) 폴백: 최근 1000건을 가져와 클라이언트 집계
            if (users.length === 0 || hours.length === 0) {
                const res = await api.get("/api/logs", {
                    params: { page: 0, size: 1000, from: f || undefined, to: t || undefined, sort: "executedAt,DESC" },
                });
                const rows: QueryLogResponse[] = Array.isArray(res.data) ? res.data : (res.data as PageResp<QueryLogResponse>).content ?? [];
                if (users.length === 0) users = aggregateByUser(rows);
                if (hours.length === 0) hours = aggregateByHourSeries(rows, f, t);
            }

            setByUser([...users].sort((a, b) => b.count - a.count).slice(0, 10)); // 상위 10
            setByHour(hours);
        } catch (e: any) {
            setError(e?.response?.data?.message || "통계를 불러오지 못했습니다.");
            setByUser([]);
            setByHour([]);
        } finally {
            setLoading(false);
        }
    };

    // 최초/기간 변경 시 (디바운스 250ms)
    useEffect(() => {
        if (debRef.current) window.clearTimeout(debRef.current);
        debRef.current = window.setTimeout(() => fetchSummary(from, to), 250);
        return () => {
            if (debRef.current) window.clearTimeout(debRef.current);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [from, to]);

    // KPI
    const totalCount = useMemo(() => (byUser ?? []).reduce((s, v) => s + (v.count || 0), 0), [byUser]);
    const topUser = useMemo(() => (byUser && byUser[0] ? byUser[0].userId : "-"), [byUser]);
    const peakHour = useMemo(() => {
        if (!byHour || byHour.length === 0) return "-";
        const max = byHour.reduce((a, b) => (b.count > a.count ? b : a));
        return max.hourLabel;
    }, [byHour]);

    return (
        <div style={{ padding: 24, display: "grid", gap: 24 }}>
            {/* 헤더 + 기간선택 */}
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <h1 style={{ fontSize: 28, fontWeight: 800, marginRight: "auto" }}>Usage Analytics</h1>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} style={input} />
                    <span>—</span>
                    <input type="date" value={to} onChange={(e) => setTo(e.target.value)} style={input} />
                    <button
                        style={btn}
                        onClick={() => {
                            setFrom(defaultFrom());
                            setTo(today());
                        }}>
                        Last 7 days
                    </button>
                    <button
                        style={btn}
                        onClick={() => {
                            const d = today();
                            setFrom(d);
                            setTo(d);
                        }}>
                        Today
                    </button>
                </div>
            </div>

            {/* KPI 카드 */}
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12 }}>
                <KpiCard title="Total Queries" value={fmt(totalCount)} hint={`${from || "—"} ~ ${to || "—"}`} />
                <KpiCard title="Top User" value={topUser} hint="가장 많은 쿼리" />
                <KpiCard title="Peak Hour" value={peakHour} hint="가장 많은 시간대" />
            </div>

            {/* 사용자별 상위 10 */}
            <section>
                <h2 style={{ fontSize: 20, fontWeight: 800, marginBottom: 8 }}>사용자별 쿼리 상위 10</h2>
                <div style={{ height: 360, border: "1px solid #2a2a2a", borderRadius: 12, padding: 12 }}>
                    <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={byUser ?? []} margin={{ top: 10, right: 10, left: 0, bottom: 0 }} barCategoryGap={18}>
                            <CartesianGrid strokeDasharray="3 3" />
                            {/* X축 레이블 숨김 */}
                            <XAxis dataKey="userId" hide />
                            <YAxis />
                            {/* 툴팁에 userId + queries 표기 */}
                            <Tooltip content={<CustomBarTooltip />} />
                            <Bar dataKey="count" name="queries" />
                        </BarChart>
                    </ResponsiveContainer>
                </div>
            </section>

            {/* 시간대별 */}
            <section>
                <h2 style={{ fontSize: 20, fontWeight: 800, marginBottom: 8 }}>시간대별 쿼리 수</h2>
                <div style={{ height: 360, border: "1px solid #2a2a2a", borderRadius: 12, padding: 12 }}>
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={byHour ?? []} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="hourLabel" tick={{ fontSize: 12 }} />
                            <YAxis />
                            <Tooltip formatter={(v: any) => [fmt(v), "queries"]} />
                            <Legend />
                            <Line type="monotone" dataKey="count" name="queries" />
                        </LineChart>
                    </ResponsiveContainer>
                </div>
            </section>

            <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                {loading && <span style={{ fontSize: 12, opacity: 0.7 }}>불러오는 중…</span>}
                {error && <span style={{ fontSize: 12, color: "#ef4444" }}>{error}</span>}
                <span style={{ marginLeft: "auto", opacity: 0.6, fontSize: 12 }}>서버 요약이 없으면 최근 1000건으로 클라이언트 집계합니다.</span>
            </div>
        </div>
    );
}

/* ====================== 유틸/보조 ====================== */

function KpiCard({ title, value, hint }: { title: string; value: string; hint?: string }) {
    return (
        <div style={{ border: "1px solid #2a2a2a", borderRadius: 16, padding: 16 }}>
            <div style={{ fontSize: 12, opacity: 0.7 }}>{title}</div>
            <div style={{ fontSize: 28, fontWeight: 800, marginTop: 4 }}>{value}</div>
            {hint && <div style={{ fontSize: 12, opacity: 0.6, marginTop: 6 }}>{hint}</div>}
        </div>
    );
}

function defaultFrom() {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 10);
}
function today() {
    const d = new Date();
    return d.toISOString().slice(0, 10);
}
function fmt(n: number) {
    return new Intl.NumberFormat().format(n || 0);
}

/** 응답이 배열이 아닌 { users: [...] } / { hours: [...] } 형태여도 배열을 뽑아내기 */
function pickArray(data: any, candidates: string[]) {
    if (Array.isArray(data)) return data;
    if (data && typeof data === "object") {
        for (const k of candidates) {
            if (Array.isArray((data as any)[k])) return (data as any)[k];
        }
    }
    return [];
}

function coalesceUser(v: any) {
    const s = String(v ?? "").trim();
    return s.length ? s : "(unknown)";
}

/** 서버 요약 정규화: by=user → { userId, count } */
function normalizeUserBuckets(list: any[]) {
    return (list || []).map((it) => {
        const userId = coalesceUser(it.userId ?? it.user ?? it.email);
        const count =
            Number(it.count !== undefined ? it.count : it.total !== undefined ? it.total : (Number(it.success) || 0) + (Number(it.failure) || 0)) || 0;
        return { userId, count };
    });
}

/** 서버 요약 정규화: by=hour → { hourLabel, count } */
function normalizeHourBuckets(list: any[]) {
    return (list || [])
        .map((it) => {
            const raw = it.hour ?? it.hourLabel ?? it.time ?? it.bucket;
            const count = Number(it.count ?? it.total) || 0;
            let hourLabel = "";
            if (typeof raw === "string" && /^\d{2}:\d{2}/.test(raw)) {
                hourLabel = raw.slice(0, 5);
            } else if (typeof raw === "string" && /^\d{4}-\d{2}-\d{2}T/.test(raw)) {
                hourLabel = `${String(new Date(raw).getHours()).padStart(2, "0")}:00`;
            } else {
                const h = Number(raw);
                hourLabel = Number.isFinite(h) ? `${String(h).padStart(2, "0")}:00` : String(raw ?? "");
            }
            return { hourLabel, count };
        })
        .sort((a, b) => a.hourLabel.localeCompare(b.hourLabel));
}

/** 클라이언트 폴백: 사용자별 */
function aggregateByUser(rows: QueryLogResponse[]) {
    const map = new Map<string, number>();
    rows.forEach((r) => {
        const label = coalesceUser((r as any).userId ?? (r as any).user ?? (r as any).email);
        map.set(label, (map.get(label) || 0) + 1);
    });
    return Array.from(map, ([userId, count]) => ({ userId, count }));
}

/** 클라이언트 폴백: 시간대(선택한 날짜 범위만) */
function aggregateByHourSeries(rows: QueryLogResponse[], from: string, to: string) {
    const fromTs = from ? Date.parse(from) : NaN;
    const toTs = to ? Date.parse(to) + 24 * 3600 * 1000 - 1 : NaN;
    const map = new Map<string, number>();

    rows.forEach((r) => {
        const ts = Date.parse(r.executedAt);
        if (!isNaN(fromTs) && ts < fromTs) return;
        if (!isNaN(toTs) && ts > toTs) return;
        const d = new Date(ts);
        const key = `${String(d.getHours()).padStart(2, "0")}:00`;
        map.set(key, (map.get(key) || 0) + 1);
    });

    const full: { hourLabel: string; count: number }[] = [];
    for (let h = 0; h < 24; h++) {
        const label = `${String(h).padStart(2, "0")}:00`;
        full.push({ hourLabel: label, count: map.get(label) || 0 });
    }
    return full;
}

function CustomBarTooltip({ active, payload }: any) {
    if (!active || !payload || payload.length === 0) return null;
    const p = payload[0];
    const userId = p?.payload?.userId ?? "(unknown)";
    const count = Number(p?.value) || 0;
    return (
        <div
            style={{
                background: "#0b0b0b",
                border: "1px solid #2a2a2a",
                borderRadius: 8,
                padding: "8px 10px",
                fontSize: 12,
            }}>
            <div style={{ opacity: 0.7, marginBottom: 4 }}>user</div>
            <div style={{ fontWeight: 800, marginBottom: 6 }}>{userId}</div>
            <div>
                <span style={{ opacity: 0.7, marginRight: 6 }}>queries</span>
                <b>{new Intl.NumberFormat().format(count)}</b>
            </div>
        </div>
    );
}

const input: React.CSSProperties = {
    width: 150,
    padding: "8px 10px",
    borderRadius: 8,
    border: "1px solid #333",
    background: "#111",
    color: "inherit",
};
const btn: React.CSSProperties = {
    padding: "8px 10px",
    borderRadius: 8,
    border: "1px solid #333",
    background: "#111",
    cursor: "pointer",
};
