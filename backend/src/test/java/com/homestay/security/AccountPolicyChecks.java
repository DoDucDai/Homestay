package com.homestay.security;

public class AccountPolicyChecks {
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
