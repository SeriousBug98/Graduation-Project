// vite.config.ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
    plugins: [react()],
    resolve: {
        // "@/..." 별칭이 리눅스에서도 안정적으로 동작하도록
        alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
    },
    server: {
        host: true, // 0.0.0.0 바인딩(외부 공개)
        port: 5173,
        strictPort: true,
        allowedHosts: [
            "ec2-15-164-129-14.ap-northeast-2.compute.amazonaws.com", // ← 본인 퍼블릭 DNS
            "15.164.129.14", // ← 퍼블릭 IP(쓰면)
        ],
        // HMR가 끊기면 주석 해제
        // hmr: { host: "ec2-15-164-129-14.ap-northeast-2.compute.amazonaws.com", port: 5173 },

        // 백엔드가 같은 EC2에서 :8080이면 이대로 OK
        proxy: {
            "/api": {
                target: "http://127.0.0.1:8080", // EC2 내부에서 프록시하므로 localhost/127.0.0.1 둘 다 무방
                changeOrigin: true,
                secure: false,
            },
        },
    },
});
