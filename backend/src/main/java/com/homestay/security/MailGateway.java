package com.homestay.security;

public interface MailGateway {
    boolean configured();
    void send(String email,String subject,String text);
}
