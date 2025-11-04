export default function StatusBadge({ status }: { status: string }) {
    const ok = status === "SUCCESS";
    const color = ok ? "#16a34a" : "#dc2626";
    const bg = ok ? "rgba(22,163,74,0.12)" : "rgba(220,38,38,0.12)";
    return (
        <span
            style={{
                padding: "2px 8px",
                borderRadius: 8,
                color,
                background: bg,
                fontSize: 12,
                fontWeight: 600,
            }}>
            {status}
        </span>
    );
}
