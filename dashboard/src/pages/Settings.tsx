// src/pages/Settings.tsx
import { useEffect, useState } from "react";
import { z } from "zod";
import { api } from "@/services/api";

// ----- helpers -----
const toUndef = (v: unknown) => (typeof v === "string" && v.trim().length === 0 ? undefined : v);

// 개별 필드 스키마
const emailSchema = z.preprocess(toUndef, z.string().email({ message: "유효한 이메일을 입력해 주세요" }).optional());

const slackSchema = z.preprocess(
    toUndef,
    z
        .string()
        .url({ message: "URL 형식이어야 합니다." })
        .startsWith("https://hooks.slack.com/", {
            message: "Slack Webhook URL 형식이어야 합니다.",
        })
        .optional()
);

// 둘 중 하나만 있어도 통과
const schema = z
    .object({
        email: emailSchema,
        slackWebhook: slackSchema,
    })
    .refine((d) => !!d.email || !!d.slackWebhook, {
        message: "이메일이나 Slack Webhook 중 하나는 입력하세요.",
        path: ["_root"],
    });

// 로그인 이메일 헤더 추출
function resolveAdminEmailHeader(fallbackFormEmail?: string) {
    try {
        const raw = localStorage.getItem("dbids_profile");
        if (raw) {
            const p = JSON.parse(raw);
            if (p?.email) return String(p.email).trim().toLowerCase();
        }
    } catch {}
    const le = (localStorage.getItem("dbids_email") || "").trim().toLowerCase();
    if (le) return le;
    return (fallbackFormEmail || "").trim().toLowerCase();
}

export default function Settings() {
    const [email, setEmail] = useState("");
    const [slack, setSlack] = useState("");
    const [status, setStatus] = useState<"idle" | "loading" | "saving" | "ok" | "fail">("idle");
    const [error, setError] = useState("");
    const [fieldErr, setFieldErr] = useState<{ email?: string; slack?: string }>({});

    // 초기 값 로드 (프리필)
    useEffect(() => {
        const load = async () => {
            setStatus("loading");
            setError("");
            try {
                const hdrEmail = resolveAdminEmailHeader();
                const { data } = await api.get<{ email?: string; slackWebhook?: string }>(
                    "/api/settings/alerts",
                    hdrEmail ? { headers: { "X-Admin-Email": hdrEmail } } : undefined
                );
                if (data?.email) setEmail(data.email);
                // slack은 서버 저장소가 없으므로 항상 null/undefined → 프리필 생략
                setStatus("idle");
            } catch (e: any) {
                setStatus("idle");
                // 프리필 실패는 치명적이지 않으니 화면은 계속 사용 가능하게
            }
        };
        load();
    }, []);

    const onSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");
        setFieldErr({});

        const parsed = schema.safeParse({ email, slackWebhook: slack });
        if (!parsed.success) {
            const fe: { email?: string; slack?: string } = {};
            for (const issue of parsed.error.issues) {
                if (issue.path[0] === "email") fe.email = issue.message;
                else if (issue.path[0] === "slackWebhook") fe.slack = issue.message;
                else setError(issue.message); // _root
            }
            setFieldErr(fe);
            return;
        }

        const data = parsed.data;

        // 전송 페이로드: 채워진 것만 포함 (빈문자는 toUndef로 제거됨)
        const payload: Record<string, string> = {};
        if (data.email) payload.email = data.email;
        if (data.slackWebhook) payload.slackWebhook = data.slackWebhook;

        // X-Admin-Email 헤더
        const headerEmail = resolveAdminEmailHeader(data.email);
        setStatus("saving");
        try {
            await api.patch("/api/settings/alerts", payload, {
                headers: headerEmail ? { "X-Admin-Email": headerEmail } : undefined,
            });
            setStatus("ok");
        } catch (err: any) {
            setStatus("fail");
            setError(err?.response?.data?.error || err?.response?.data?.message || "저장 중 오류가 발생했어요");
        }
    };

    return (
        <div style={{ padding: 24, maxWidth: 560 }}>
            <h1 style={{ fontSize: 32, fontWeight: 800, marginBottom: 8 }}>Alert Channels</h1>
            <p style={{ opacity: 0.7, marginBottom: 16 }}>
                이메일 또는 Slack Webhook 중 <b>하나만</b> 입력해도 저장됩니다. (둘 다 입력도 가능)
            </p>

            <form onSubmit={onSave} style={{ display: "grid", gap: 12 }}>
                <label>
                    <div style={{ marginBottom: 4 }}>Email (선택)</div>
                    <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="admin@example.com" style={input} />
                    {fieldErr.email && <div style={errTxt}>{fieldErr.email}</div>}
                </label>

                <label>
                    <div style={{ marginBottom: 4 }}>Slack Webhook URL (선택)</div>
                    <input value={slack} onChange={(e) => setSlack(e.target.value)} placeholder="https://hooks.slack.com/services/..." style={input} />
                    {fieldErr.slack && <div style={errTxt}>{fieldErr.slack}</div>}
                </label>

                <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    <button type="submit" disabled={status === "saving"} style={btn}>
                        {status === "saving" ? "저장 중..." : "저장하기"}
                    </button>
                    {status === "ok" && <span style={{ color: "#22c55e" }}>저장됨</span>}
                    {status === "fail" && <span style={{ color: "#ef4444" }}>실패</span>}
                    {error && <span style={{ marginLeft: 8, color: "#ef4444" }}>{error}</span>}
                </div>
            </form>
        </div>
    );
}

const input: React.CSSProperties = {
    width: "100%",
    padding: "10px 12px",
    borderRadius: 8,
    border: "1px solid #333",
    background: "#111",
    color: "inherit",
};
const btn: React.CSSProperties = {
    padding: "10px 12px",
    borderRadius: 8,
    border: "1px solid #333",
    background: "#111",
    cursor: "pointer",
};
const errTxt: React.CSSProperties = { color: "#ef4444", marginTop: 6, fontSize: 12 };
