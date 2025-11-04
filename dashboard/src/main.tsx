import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { router } from "@/routes/Router";
import { AuthProvider } from "@/context/AuthContext";
import "./index.css";

const qc = new QueryClient();
const root = document.getElementById("root")!;

createRoot(root).render(
    <StrictMode>
        <QueryClientProvider client={qc}>
            <AuthProvider>
                <RouterProvider router={router} />
            </AuthProvider>
        </QueryClientProvider>
    </StrictMode>
);
