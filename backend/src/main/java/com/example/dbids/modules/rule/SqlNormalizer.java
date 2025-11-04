package com.example.dbids.modules.rule;

public class SqlNormalizer {

    public String normalize(String sql) {
        if (sql == null) return "";

        String s = sql;

        // 블록/라인 주석 제거
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("(?m)--.*?$", " ");

        // 문자열 리터럴 마스킹
        // 1) 싱글쿼트: SQL 이스케이프('' -> ')를 고려
        s = s.replaceAll("'([^']|'')*'", "'?'");

        // 2) 더블쿼트: 백슬래시 이스케이프까지 처리 (\" 등)
        //    "[^"\\]*  : 쌍따옴표/백슬래시가 아닌 문자들
        //    \\.       : 백슬래시로 이스케이프된 임의의 한 글자
        //    전체를 반복해서 닫는 " 를 만날 때까지
        s = s.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"?\"");

        // 숫자 상수 마스킹 (OR 1=1 → OR 0=0)
        s = s.replaceAll("\\b\\d+\\b", "0");

        // 공백 압축 + 트림
        s = s.replaceAll("\\s+", " ").trim();

        // 대문자화
        return s.toUpperCase();
    }
}
