package com.chatvsdoc.api.service.auth;

/** Chính sách mật khẩu (T04 mục 3.5): ≥ 10 ký tự, có ít nhất một chữ cái và một chữ số. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    private PasswordPolicy() {}

    public static boolean isAcceptable(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        return letter && digit;
    }
}
