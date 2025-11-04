// src/components/Nav.tsx
import { Link, NavLink } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";

export default function Nav() {
    const { isAuthed, logout } = useAuth();
    const item = (to: string, label: string) => (
        <NavLink to={to} style={({ isActive }) => ({ fontWeight: isActive ? 700 : 400, marginLeft: 12 })}>
            {label}
        </NavLink>
    );
    return (
        <nav style={{ display: "flex", gap: 12, padding: 12, borderBottom: "1px solid #333" }}>
            <Link to="/" style={{ marginRight: "auto", fontWeight: 800 }}>
                DBIDS Admin
            </Link>
            {isAuthed ? (
                <>
                    {/* Health 제거 */}
                    {item("/logs", "Logs")}
                    {item("/events", "Events")}
                    {item("/stats", "Stats")}
                    {item("/settings", "Settings")}
                    <button
                        onClick={logout}
                        style={{ marginLeft: 12, padding: "6px 10px", borderRadius: 8, border: "1px solid #333", background: "#111", cursor: "pointer" }}>
                        Logout
                    </button>
                </>
            ) : (
                item("/login", "Login")
            )}
        </nav>
    );
}
