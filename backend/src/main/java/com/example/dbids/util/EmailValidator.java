package com.example.dbids.util;

import java.util.regex.Pattern;

public final class EmailValidator {
    private static final Pattern P =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$");
    private EmailValidator() {}
    public static boolean isValid(String s) {
        return s != null && P.matcher(s).matches();
    }
}
