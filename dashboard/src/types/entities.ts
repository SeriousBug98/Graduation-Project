export interface QueryLogResponse {
    id: string;
    userId: string;
    executedAt: string;
    status: "SUCCESS" | "FAILURE" | "DENY" | string;
    returnRows: number;
    sqlSummary?: string;
    sqlRaw?: string | null;
    sql?: string | null;
    sqlFull?: string | null;
}
