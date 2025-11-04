import type { Severity } from "@/types/events";

export default function SeverityBadge({ severity }: { severity: Severity | string }) {
    const map: Record<string, string> = {
        LOW: "#22c55e",
        MEDIUM: "#f59e0b",
        HIGH: "#ef4444",
    };
    const color = map[severity] || "#64748b";
    return (
        <span
            style={{
                padding: "2px 8px",
                borderRadius: 8,
                color,
                background: "rgba(255,255,255,0.06)",
                border: `1px solid ${color}55`,
                fontSize: 12,
                fontWeight: 700,
            }}>
            {severity}
        </span>
    );
}
