// vite.config.ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
    plugins: [react()],
    resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
    server: {
        host: true,
        port: 5173,
        strictPort: true,
        allowedHosts: ["ec2-52-78-120-100.ap-northeast-2.compute.amazonaws.com", "52.78.120.100"],
        proxy: {
            "/api": {
                target: "http://127.0.0.1:8080",
                changeOrigin: true,
                secure: false,
                configure: (proxy) => {
                    // ← 여기!
                    proxy.on("proxyReq", (proxyReq) => {
                        proxyReq.removeHeader("origin"); // Origin 헤더 제거
                    });
                },
            },
        },
    },
});
