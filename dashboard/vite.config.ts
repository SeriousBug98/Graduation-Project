// vite.config.ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react"; // 사용 플러그인 유지

export default defineConfig({
    plugins: [react()],
    server: {
        host: true, // 0.0.0.0 바인딩(외부 노출)
        port: 5173,
        strictPort: true,
        cors: true,
        allowedHosts: [
            "ec2-15-164-129-14.ap-northeast-2.compute.amazonaws.com",
            "15.164.129.14", // 퍼블릭 IP (있으면)
            // 와일드카드 허용도 가능:
            // /.*\.ap-northeast-2\.compute\.amazonaws\.com$/
        ],
        // HMR가 끊기면 아래도 추가
        // hmr: { host: 'ec2-15-164-129-14.ap-northeast-2.compute.amazonaws.com', port: 5173, clientPort: 5173 }
    },
});
