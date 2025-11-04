import { useMemo, useState } from "react";
import { z } from "zod";
import { register, type AdminRole } from "@/services/auth";
import { Link } from "react-router-dom";

const emailRule = z.string().email();
const pwRule = z.string().min(8).max(64);
const roleRule = z.enum(["READER", "WRITER", "DBA"]);

export default function RegisterPage() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [role, setRole] = useState<AdminRole>("READER");
    const [error, setError] = useState("");
    const [status, setStatus] = useState<"idle" | "loading">("idle");

    const canSubmit = useMemo(
        () => emailRule.safeParse(email).success && pwRule.safeParse(password).success && roleRule.safeParse(role).success,
        [email, password, role]
    );

    const onSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");
        if (!canSubmit) {
            setError("입력 형식을 확인하세요");
            return;
        }
        setStatus("loading");
        try {
            await register({ email, password, role });
            window.location.href = "/login?registered=1";
        } catch (err: any) {
            setError(err?.response?.data?.message || "회원가입 중 오류가 발생했어요");
        } finally {
            setStatus("idle");
        }
    };

    return (
        <div style={{ padding: 24, display: "grid", placeItems: "center", minHeight: "60vh" }}>
            <form onSubmit={onSubmit} style={{ width: 380, display: "grid", gap: 12 }}>
                <h1 style={{ fontSize: 28, fontWeight: 800 }}>관리자 회원가입</h1>

                <label>
                    <div style={{ marginBottom: 4 }}>Email</div>
                    <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="admin@example.com" style={input} />
                </label>

                <label>
                    <div style={{ marginBottom: 4 }}>Password</div>
                    <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="••••••••" style={input} />
                </label>

                <label>
                    <div style={{ marginBottom: 4 }}>Role</div>
                    <select value={role} onChange={(e) => setRole(e.target.value as AdminRole)} style={input as any}>
                        <option value="READER">READER</option>
                        <option value="WRITER">WRITER</option>
                        <option value="DBA">DBA</option>
                    </select>
                </label>

                <button type="submit" disabled={!canSubmit || status === "loading"} style={btn}>
                    {status === "loading" ? "가입 중..." : "회원가입"}
                </button>

                {error && <div style={{ color: "#ef4444" }}>{error}</div>}
                <div style={{ fontSize: 12, opacity: 0.7 }}>
                    이미 계정이 있나요? <Link to="/login">로그인</Link>
                </div>
            </form>
        </div>
    );
}

const input: React.CSSProperties = { width: "100%", padding: "10px 12px", borderRadius: 8, border: "1px solid #333", background: "#111", color: "inherit" };
const btn: React.CSSProperties = { padding: "10px 12px", borderRadius: 8, border: "1px solid #333", background: "#111", cursor: "pointer" };
