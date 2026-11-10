package com.homestay.security;

import com.homestay.db.DBConnection;
import java.sql.*;
import java.util.*;
import org.springframework.core.env.*;

/** Account bootstrap checks on a disposable database; never use the live application database. */
public final class DemoAccountsSqlServerIT {
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        String database="HomestayDemoAccountsTest_"+UUID.randomUUID().toString().replace("-","");
        String previousEnabled=System.getProperty("DEMO_ACCOUNTS_ENABLED"),previousPassword=System.getProperty("DEMO_ACCOUNT_PASSWORD");
        try(var connection=DBConnection.getConnection();var sql=connection.createStatement()) {
            sql.execute("CREATE DATABASE ["+database+"]");
            try {
                sql.execute("USE ["+database+"]");
                sql.execute("CREATE TABLE Users(user_id INT IDENTITY PRIMARY KEY,username VARCHAR(50) NOT NULL UNIQUE,password VARCHAR(256) NOT NULL,password_scheme VARCHAR(30) NOT NULL,full_name NVARCHAR(100) NOT NULL,role VARCHAR(20) NOT NULL,auth_version INT NOT NULL DEFAULT 0)");
                String existingHash=PasswordHashing.encoder().encode("Existing@123");
                try(var insert=connection.prepareStatement("INSERT Users(username,password,password_scheme,full_name,role) VALUES('existingadmin',?,?,'Existing admin','admin')")) {
                    insert.setString(1,existingHash);insert.setString(2,PasswordHashing.SCHEME);insert.executeUpdate();
                }
                var dbEnvironment=new StandardEnvironment();
                dbEnvironment.getPropertySources().addFirst(new MapPropertySource("demo-test-db",Map.of("DB_NAME",database)));
                DBConnection.configure(dbEnvironment);
                var prod=new StandardEnvironment();prod.setActiveProfiles("prod");
                System.setProperty("DEMO_ACCOUNTS_ENABLED","false");
                new SecurityConfig().checkAccountMigration(prod).run(null);
                require(count(sql)==1,"Disabled bootstrap created accounts");
                System.setProperty("DEMO_ACCOUNTS_ENABLED","true");System.setProperty("DEMO_ACCOUNT_PASSWORD","123456");
                new SecurityConfig().checkAccountMigration(prod).run(null);
                require(count(sql)==3,"Did not create exactly two demo accounts");
                Map<String,String> hashes=new HashMap<>();
                try(var rows=sql.executeQuery("SELECT username,role,password,password_scheme,auth_version FROM Users")) {
                    while(rows.next()) {
                        String name=rows.getString(1);hashes.put(name,rows.getString(3));
                        require(PasswordHashing.SCHEME.equals(rows.getString(4)),"Wrong password scheme");
                        if(name.equals("existingadmin"))require(existingHash.equals(rows.getString(3)),"Existing account password changed");
                        else {
                            require((name.equals("admindemo")?"admin":"customer").equals(rows.getString(2)),"Wrong demo role");
                            require(PasswordHashing.validHash(rows.getString(3)) && PasswordHashing.encoder().matches("123456",rows.getString(3)),"Demo password not encoded correctly");
                        }
                    }
                }
                new SecurityConfig().checkAccountMigration(prod).run(null);
                require(count(sql)==3,"Restart duplicated accounts");
                try(var rows=sql.executeQuery("SELECT username,password,auth_version FROM Users")) {
                    while(rows.next())require(hashes.get(rows.getString(1)).equals(rows.getString(2)) && rows.getInt(3)==0,"Restart reset password or session version");
                }
                var manager=new SecurityConfig().authenticationManager(new UserAccounts(),PasswordHashing.encoder());
                for(var entry:Map.of("admindemo","admin","khachdemo","customer").entrySet()) {
                    var authentication=manager.authenticate(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.unauthenticated(entry.getKey(),"123456"));
                    require(authentication.isAuthenticated() && ((AppUser)authentication.getPrincipal()).role().equals(entry.getValue()),"Demo login failed or role changed");
                }
                System.out.println("PASS real Spring authentication accepts both requested demo logins with distinct admin/customer authorities");
                System.out.println("PASS explicit demo bootstrap creates exactly two hashed accounts, correct roles and no restart/reset changes");

                sql.execute("UPDATE Users SET role='customer' WHERE username='admindemo'");
                boolean collision=false;try {new SecurityConfig().checkAccountMigration(prod).run(null);}catch(SQLException expected){collision=true;}
                require(collision,"Bootstrap elevated an existing account");
                sql.execute("UPDATE Users SET role='admin' WHERE username='admindemo'");
                try(var insert=connection.prepareStatement("INSERT Users(username,password,password_scheme,full_name,role) VALUES('otherweak',?,?,'Other weak','customer')")) {
                    insert.setString(1,PasswordHashing.encoder().encode("123456"));insert.setString(2,PasswordHashing.SCHEME);insert.executeUpdate();
                }
                boolean rejected=false;try {new SecurityConfig().checkAccountMigration(prod).run(null);}catch(IllegalStateException expected){rejected=true;}
                require(rejected,"Demo mode admitted an unrelated weak account");
                sql.execute("DELETE FROM Users WHERE username='otherweak'");
                System.setProperty("DEMO_ACCOUNTS_ENABLED","false");
                rejected=false;try {new SecurityConfig().checkAccountMigration(prod).run(null);}catch(IllegalStateException expected){rejected=true;}
                require(rejected,"Production accepted weak demo accounts without opt-in");
                System.out.println("PASS role collisions fail closed; unrelated weak passwords and disabled demo mode remain blocked");
            } finally {
                DBConnection.configure(new StandardEnvironment());
                if(previousEnabled==null)System.clearProperty("DEMO_ACCOUNTS_ENABLED");else System.setProperty("DEMO_ACCOUNTS_ENABLED",previousEnabled);
                if(previousPassword==null)System.clearProperty("DEMO_ACCOUNT_PASSWORD");else System.setProperty("DEMO_ACCOUNT_PASSWORD",previousPassword);
                sql.execute("USE master");sql.execute("ALTER DATABASE ["+database+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                sql.execute("DROP DATABASE ["+database+"]");System.out.println("Disposed isolated demo-account database.");
            }
        }
    }
    private static int count(Statement statement) throws SQLException {
        try(var rows=statement.executeQuery("SELECT COUNT(*) FROM Users")){rows.next();return rows.getInt(1);}
    }
}
