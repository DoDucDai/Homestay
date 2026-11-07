package com.homestay.security;

// NOTE KIỂM THỬ: Kiểm tra các biên hợp lệ/không hợp lệ của điện thoại, email và mật khẩu tài khoản mà không cần mở website.
// File này thuộc src/test, không phải endpoint và không nằm trong mã nghiệp vụ chạy phục vụ người dùng.
public class AccountPolicyChecks {
    // NOTE KIỂM THỬ: Điểm chạy nhóm kiểm tra; dùng AssertionError/kết thúc khác 0 để báo điều kiện không đạt.
    public static void main(String[] args) {
        for(String value:new String[]{"Abc@12","  Abc@12  ","Password1!"})
            if(!AccountPolicy.password(value)) throw new AssertionError("Valid password rejected");
        for(String value:new String[]{"Ab@12","abc@12","ABC@12","Abcd@!","Abcd12","Abc 12","123456"})
            if(AccountPolicy.password(value)) throw new AssertionError("Weak password accepted");
        for(String value:new String[]{"User.Name@GMAIL.COM","user+tag@example.org","a@sub.example.com"})
            if(!AccountPolicy.email(value))throw new AssertionError("Valid email rejected");
        for(String value:new String[]{"user@example","user@-example.com","user@@gmail.com",".user@gmail.com","user..name@gmail.com","user name@gmail.com"})
            if(AccountPolicy.email(value))throw new AssertionError("Invalid email accepted");
        if(!AccountPolicy.phone("0901234567") || AccountPolicy.phone("09012345678") || AccountPolicy.phone("901234567")) throw new AssertionError("Phone validation mismatch");
        System.out.println("PASS backend email, phone and password policy boundaries");
    }
}
