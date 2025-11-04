// import { api } from "@/services/api";

// export type AdminRole = "READER" | "WRITER" | "DBA";

// export interface AdminProfile {
//     adminId: string;
//     email: string;
//     role: AdminRole;
//     lastlogin?: string; // 백엔드가 lastLogin이든 lastlogin이든 옵셔널로 받자
// }

// export async function login(email: string, password: string): Promise<AdminProfile> {
//     // 백엔드: POST /api/auth/login { email, password } → 200 { adminId, email, role, lastlogin }
//     const { data } = await api.post<AdminProfile>("/api/auth/login", { email, password });
//     return data;
// }

// /** 로컬 저장소 (프로필 기반 세션) */
// const PROFILE_KEY = "dbids_profile";

// export function saveProfile(p: AdminProfile) {
//     localStorage.setItem(PROFILE_KEY, JSON.stringify(p));
// }
// export function getProfile(): AdminProfile | undefined {
//     try {
//         const raw = localStorage.getItem(PROFILE_KEY);
//         return raw ? (JSON.parse(raw) as AdminProfile) : undefined;
//     } catch {
//         return undefined;
//     }
// }
// export function clearProfile() {
//     localStorage.removeItem(PROFILE_KEY);
// }

// export interface RegisterPayload {
//     email: string;
//     password: string;
//     role: AdminRole;
// }

// /** POST /api/auth/register */
// export async function register(payload: RegisterPayload) {
//     await api.post("/api/auth/register", payload);
// }

// src/services/auth.ts
import { api } from "@/services/api";

/** 관리자 권한 (BE enum과 동일하게 맞추세요) */
export type AdminRole = "READER" | "WRITER" | "DBA";

/** 로그인/세션에 쓰는 관리자 프로필 */
export interface AdminProfile {
  adminId: string;        // 내부 PK (예: UUID)
  email: string;          // 로그인 이메일
  name?: string;
  role?: AdminRole;
  accessToken?: string;   // 선택: BE가 내려주면 자동으로 Authorization 헤더 부착됨
  expiresAt?: string;     // 선택: ISO8601
}

/** 회원가입 입력 */
export interface RegisterInput {
  email: string;
  password: string;
  role: AdminRole;
}

/** 로그인: 이메일/비밀번호 → AdminProfile */
export async function login(email: string, password: string): Promise<AdminProfile> {
  const { data } = await api.post<AdminProfile>("/api/auth/login", {
    email,
    password,
  });
  return { ...data, email: (data.email || email).trim().toLowerCase() };
}

/** 회원가입: 이메일/비밀번호/권한 → 성공시 201/200 */
export async function register(input: RegisterInput): Promise<void> {
  await api.post("/api/auth/register", {
    email: input.email,
    password: input.password,
    role: input.role,
  });
}

/* ===== 로컬스토리지에 프로필 저장/로드 ===== */
const KEY = "dbids_profile";

export function saveProfile(p: AdminProfile) {
  localStorage.setItem(KEY, JSON.stringify(p));
}
export function getProfile(): AdminProfile | undefined {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as AdminProfile) : undefined;
  } catch {
    return undefined;
  }
}
export function clearProfile() {
  localStorage.removeItem(KEY);
}
