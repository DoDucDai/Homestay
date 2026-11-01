package com.homestay.security;

public class AccountPolicyChecks {
    public static void main(String[] args) {
        for(String value:new String[]{"Abc@12","  Abc@12  ","Password1!"})
            if(!AccountPolicy.password(value)) throw new AssertionError("Valid password rejected");
        for(String value:new String[]{"Ab@12","abc@12","ABC@12","Abcd@!","Abcd12","Abc 12","123456"})
            if(AccountPolicy.password(value)) throw new AssertionError("Weak password accepted");
        if(!AccountPolicy.email("User.Name@GMAIL.COM"))throw new AssertionError("Valid Gmail rejected");
        for(String value:new String[]{"user@example.com","user@gmail.com.evil","user@@gmail.com",".user@gmail.com","user..name@gmail.com","user name@gmail.com"})
            if(AccountPolicy.email(value))throw new AssertionError("Invalid Gmail accepted");
        if(!AccountPolicy.phone("0901234567") || AccountPolicy.phone("09012345678") || AccountPolicy.phone("901234567")) throw new AssertionError("Phone validation mismatch");
        System.out.println("PASS backend Gmail, phone and password policy boundaries");
    }
}
