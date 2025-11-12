// // src/pages/Login.tsx
// import { useMemo, useState } from 'react'
// import { z } from 'zod'
// import { login as loginApi } from '@/services/auth'
// import { useAuth } from '@/context/AuthContext'

// // 이메일 + 비밀번호 규칙
// const emailRule = z.string().email()
// const pwRule = z.string().min(8).max(64)

// export default function Login() {
//   const { login } = useAuth()
//   const [email, setEmail] = useState('')
//   const [password, setPassword] = useState('')
//   const [error, setError] = useState('')
//   const [status, setStatus] = useState<'idle' | 'loading'>('idle')

//   const justRegistered = new URLSearchParams(window.location.search).get('registered') === '1'

//   const canSubmit = useMemo(() => {
//     const okEmail = emailRule.safeParse(email).success
//     const okPw = pwRule.safeParse(password).success
//     return okEmail && okPw
//   }, [email, password])

//   const onSubmit = async (e: React.FormEvent) => {
//     e.preventDefault()
//     setError('')
//     if (!canSubmit) {
//       setError('이메일/비밀번호 형식을 확인하세요')
//       return
//     }
//     setStatus('loading')
//     try {
//       // 백엔드: 이메일 기반 로그인
//       const res = await loginApi(email, password)

//       // 토큰 보관
//       login(res.accessToken)

//       // ✅ Settings 등에서 쓸 현재 로그인 이메일 저장 (X-Admin-Email 헤더용)
//       localStorage.setItem('dbids_email', email.trim().toLowerCase())

//       // next 파라미터가 있으면 그리로, 없으면 루트
//       const params = new URLSearchParams(window.location.search)
//       window.location.href = params.get('next') || '/'
//     } catch (err: any) {
//       const code = err?.response?.data?.code
//       const attemptsLeft = err?.response?.data?.attemptsLeft
//       if (code === 'ACCOUNT_LOCKED') setError('계정이 잠겼습니다. 관리자에게 문의하세요.')
//       else if (code === 'INVALID_CREDENTIALS') setError(`로그인 실패. 남은 시도: ${attemptsLeft ?? '알 수 없음'}`)
//       else setError(err?.response?.data?.message || '로그인 중 오류가 발생했어요')
//     } finally {
//       setStatus('idle')
//     }
//   }

//   return (
//     <div style={{ padding: 24, display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
//       <form onSubmit={onSubmit} style={{ width: 360, display: 'grid', gap: 12 }}>
//         {justRegistered && (
//           <div style={{ marginBottom: 4, padding: 10, border: '1px solid #334155', borderRadius: 8, background: '#0b1220' }}>
//             회원가입이 완료되었습니다. 이메일/비밀번호로 로그인하세요.
//           </div>
//         )}
//         <h1 style={{ fontSize: 28, fontWeight: 800 }}>관리자 로그인</h1>

//         <label>
//           <div style={{ marginBottom: 4 }}>Email</div>
//           <input
//             value={email}
//             onChange={e => setEmail(e.target.value)}
//             placeholder="admin@example.com"
//             style={input}
//             autoComplete="username"
//           />
//         </label>

//         <label>
//           <div style={{ marginBottom: 4 }}>비밀번호</div>
//           <input
//             value={password}
//             onChange={e => setPassword(e.target.value)}
//             type="password"
//             placeholder="••••••••"
//             style={input}
//             autoComplete="current-password"
//           />
//         </label>

//         <button type="submit" disabled={!canSubmit || status === 'loading'} style={btn}>
//           {status === 'loading' ? '로그인 중...' : '로그인'}
//         </button>

//         {error && <div style={{ color: '#ef4444' }}>{error}</div>}
//         <div style={{ fontSize: 12, opacity: 0.7 }}>* 5회 실패 시 계정 잠금. HTTPS에서만 사용하세요.</div>
//       </form>
//     </div>
//   )
// }

// const input: React.CSSProperties = {
//   width: '100%',
//   padding: '10px 12px',
//   borderRadius: 8,
//   border: '1px solid #333',
//   background: '#111',
//   color: 'inherit',
// }
// const btn: React.CSSProperties = {
//   padding: '10px 12px',
//   borderRadius: 8,
//   border: '1px solid #333',
//   background: '#111',
//   cursor: 'pointer',
// }

// src/pages/Login.tsx
import { useMemo, useState } from "react";
import { z } from "zod";
import { login as loginApi } from "@/services/auth";
import { useAuth } from "@/context/AuthContext";
import { Link } from "react-router-dom";

const emailRule = z.string().email();
const pwRule = z.string().min(8).max(64);

export default function Login() {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [status, setStatus] = useState<"idle" | "loading">("idle");

  const justRegistered = new URLSearchParams(window.location.search).get("registered") === "1";

  const canSubmit = useMemo(() => {
    const okEmail = emailRule.safeParse(email).success;
    const okPw = pwRule.safeParse(password).success;
    return okEmail && okPw;
  }, [email, password]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!canSubmit) {
      setError("이메일/비밀번호 형식을 확인하세요");
      return;
    }
    setStatus("loading");
    try {
      // 백엔드 로그인 → AdminProfile 수신
      const profile = await loginApi(email.trim().toLowerCase(), password);
      // 컨텍스트에 프로필 저장 (인터셉터가 헤더 자동 부착)
      login(profile);

      const params = new URLSearchParams(window.location.search);
      window.location.href = params.get("next") || "/";
    } catch (err: any) {
      const code = err?.response?.data?.code;
      const attemptsLeft = err?.response?.data?.attemptsLeft;
      if (code === "ACCOUNT_LOCKED") setError("계정이 잠겼습니다. 관리자에게 문의하세요.");
      else if (code === "INVALID_CREDENTIALS") setError(`로그인 실패. 남은 시도: ${attemptsLeft ?? "알 수 없음"}`);
      else setError(err?.response?.data?.message || "로그인 중 오류가 발생했어요");
    } finally {
      setStatus("idle");
    }
  };

  return (
    <div style={{ padding: 24, display: "grid", placeItems: "center", minHeight: "60vh" }}>
      <form onSubmit={onSubmit} style={{ width: 360, display: "grid", gap: 12 }}>
        {justRegistered && (
          <div style={{ marginBottom: 4, padding: 10, border: "1px solid #334155", borderRadius: 8, background: "#0b1220" }}>
            회원가입이 완료되었습니다. 이메일/비밀번호로 로그인하세요.
          </div>
        )}
        <h1 style={{ fontSize: 28, fontWeight: 800 }}>관리자 로그인</h1>

        <label>
          <div style={{ marginBottom: 4 }}>Email</div>
          <input
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="admin@example.com"
            style={input}
            autoComplete="username"
          />
        </label>

        <label>
          <div style={{ marginBottom: 4 }}>비밀번호</div>
          <input
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            type="password"
            placeholder="••••••••"
            style={input}
            autoComplete="current-password"
          />
        </label>

        <button type="submit" disabled={!canSubmit || status === "loading"} style={btn}>
          {status === "loading" ? "로그인 중..." : "로그인"}
        </button>

        {error && <div style={{ color: "#ef4444" }}>{error}</div>}

        {/* ✅ 회원가입 링크 추가 */}
        <div style={{ fontSize: 12, opacity: 0.8, marginTop: 4 }}>
          계정이 없나요? <Link to="/register">회원가입</Link>
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
