package com.homestay.security;

import java.util.regex.Pattern;

// NOTE BACKEND: Quy tắc định dạng thông tin tài khoản dùng chung cho đăng ký, sửa hồ sơ và đổi mật khẩu.
public final class AccountPolicy {
    private AccountPolicy() {}
    // NOTE BACKEND: Chấp nhận đúng 10 chữ số bắt đầu bằng 0; dữ liệu null hoặc khác định dạng trả false.
    public static boolean phone(String value) { return value!=null && value.matches("0[0-9]{9}"); }
    // NOTE BACKEND: Kiểm tra địa chỉ email tối đa 254 ký tự, phần tên/hệ thống tên miền hợp lệ và không có hai dấu chấm liên tiếp trong tên.
    public static boolean email(String value) {
        return value!=null && value.length()<=254 && value.matches("(?i)[a-z0-9](?:[a-z0-9._%+!-]*[a-z0-9])?@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
            && !value.substring(0,value.indexOf('@')).contains("..");
    }
    // NOTE BACKEND: Yêu cầu 6–128 ký tự, có chữ hoa, chữ thường, chữ số và ký tự đặc biệt; không tự cắt khoảng trắng mật khẩu.
    public static boolean password(String value) {
        return value!=null && value.length()>=6 && value.length()<=128
            && Pattern.compile("[A-Z]").matcher(value).find()
            && Pattern.compile("[a-z]").matcher(value).find()
            && Pattern.compile("[0-9]").matcher(value).find()
            && Pattern.compile("[\\p{P}\\p{S}]").matcher(value).find();
    }
}
