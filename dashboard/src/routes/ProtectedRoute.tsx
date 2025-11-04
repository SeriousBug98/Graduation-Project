import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";

export default function ProtectedRoute({ children }: { children: React.ReactNode }) {
    const { isAuthed } = useAuth();
    const loc = useLocation();
    if (!isAuthed) return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname)}`} replace />;
    return <>{children}</>;
}
