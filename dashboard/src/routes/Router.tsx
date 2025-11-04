// src/routes/Router.tsx
import { createBrowserRouter } from "react-router-dom";
import App from "@/App";
import Logs from "@/pages/Logs";
import Events from "@/pages/Events";
import Stats from "@/pages/Stats";
import Settings from "@/pages/Settings";
import Login from "@/pages/Login";
import ProtectedRoute from "@/routes/ProtectedRoute";
import RegisterPage from "@/pages/Register"; // ← 추가

export const router = createBrowserRouter([
    { path: "/login", element: <Login /> },
    { path: "/register", element: <RegisterPage /> }, // ← 이 줄 추가
    {
        path: "/",
        element: (
            <ProtectedRoute>
                <App />
            </ProtectedRoute>
        ),
        children: [
            { index: true, element: <Logs /> },
            { path: "logs", element: <Logs /> },
            { path: "events", element: <Events /> },
            { path: "stats", element: <Stats /> },
            { path: "settings", element: <Settings /> },
        ],
    },
]);
