// import { createContext, useContext, useMemo, useState } from "react";
// import type { AdminProfile } from "@/services/auth";
// import { getProfile, saveProfile, clearProfile } from "@/services/auth";

// interface AuthCtx {
//     isAuthed: boolean;
//     profile?: AdminProfile;
//     login: (profile: AdminProfile) => void; // ← 프로필을 그대로 넣는다
//     logout: () => void;
// }

// const Ctx = createContext<AuthCtx | null>(null);

// export function AuthProvider({ children }: { children: React.ReactNode }) {
//     const [profile, setProfile] = useState<AdminProfile | undefined>(getProfile());

//     const value = useMemo<AuthCtx>(
//         () => ({
//             isAuthed: Boolean(profile?.adminId),
//             profile,
//             login: (p: AdminProfile) => {
//                 saveProfile(p);
//                 setProfile(p);
//             },
//             logout: () => {
//                 clearProfile();
//                 setProfile(undefined);
//                 window.location.href = "/login";
//             },
//         }),
//         [profile]
//     );

//     return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
// }

// export function useAuth() {
//     const v = useContext(Ctx);
//     if (!v) throw new Error("AuthProvider 안에서 사용해야 합니다");
//     return v;
// }

// src/context/AuthContext.tsx
import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { AdminProfile } from "@/services/auth";
import { getProfile, saveProfile, clearProfile } from "@/services/auth";
import { api } from "@/services/api";

interface AuthCtx {
  isAuthed: boolean;
  profile?: AdminProfile;
  login: (profile: AdminProfile) => void; // 프로필 그대로 저장
  logout: () => void;
}

const Ctx = createContext<AuthCtx | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [profile, setProfile] = useState<AdminProfile | undefined>(getProfile());

  // axios 인터셉터: 토큰/이메일 자동 부착 + 401 처리
  useEffect(() => {
    const reqId = api.interceptors.request.use((config) => {
      const p = getProfile(); // 최신값 참조
      if (p?.accessToken) {
        config.headers.Authorization = `Bearer ${p.accessToken}`;
      }
      if (p?.email) {
        config.headers["X-Admin-Email"] = p.email;
      }
      return config;
    });

    const resId = api.interceptors.response.use(
      (res) => res,
      (err) => {
        if (err?.response?.status === 401) {
          clearProfile();
          setProfile(undefined);
          window.location.href = "/login?reason=unauthorized";
        }
        return Promise.reject(err);
      }
    );

    return () => {
      api.interceptors.request.eject(reqId);
      api.interceptors.response.eject(resId);
    };
  }, []);

  const value = useMemo<AuthCtx>(
    () => ({
      isAuthed: Boolean(profile?.adminId),
      profile,
      login: (p: AdminProfile) => {
        saveProfile(p);
        setProfile(p);
      },
      logout: () => {
        clearProfile();
        setProfile(undefined);
        window.location.href = "/login";
      },
    }),
    [profile]
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const v = useContext(Ctx);
  if (!v) throw new Error("AuthProvider 안에서 사용해야 합니다");
  return v;
}
