package com.homestay.security;

// NOTE BACKEND: Giao diện gửi email: code nghiệp vụ chỉ phụ thuộc giao diện này, còn HTTP provider và bộ giả kiểm thử có cách triển khai riêng.
public interface MailGateway {
    boolean configured();
    void send(String email,String subject,String text);
}
