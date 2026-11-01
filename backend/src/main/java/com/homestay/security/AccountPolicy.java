package com.homestay.security;

import java.util.regex.Pattern;

public final class AccountPolicy {
    private AccountPolicy() {}
    public static boolean phone(String value) { return value!=null && value.matches("0[0-9]{9}"); }
    public static boolean email(String value) {
        return value!=null && value.length()<=254 && value.matches("(?i)[a-z0-9]+(?:\\.[a-z0-9]+)*@gmail\\.com");
    }
    public static boolean password(String value) {
        return value!=null && value.length()>=6 && value.length()<=128
            && Pattern.compile("[A-Z]").matcher(value).find()
            && Pattern.compile("[a-z]").matcher(value).find()
            && Pattern.compile("[0-9]").matcher(value).find()
            && Pattern.compile("[\\p{P}\\p{S}]").matcher(value).find();
    }
}
