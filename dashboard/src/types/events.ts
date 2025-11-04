export type Severity = "LOW" | "MEDIUM" | "HIGH";
export type EventType = "PATTERN" | "BEHAVIOR" | "AUTHZ" | string;

export interface DetectionEvent {
    eventId: string;
    logId: string;
    type: EventType;
    severity: Severity;
    occurredAt: string;
    sqlRaw: string;

    // 서버가 내려주면 사용자의 것만 필터링 가능
    userEmail?: string;
    userId?: string;
}
