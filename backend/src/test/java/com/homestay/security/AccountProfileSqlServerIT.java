package com.homestay.security;

import com.homestay.controller.AuthController;
import com.homestay.db.DBConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Profile persistence checks use a disposable database, never operational accounts. */
public class AccountProfileSqlServerIT {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--child")) { checks(); return; }
        String database = "HomestayProfileTest_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conn = DBConnection.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE [" + database + "]");
            try {
                st.execute("USE [" + database + "]");
                st.execute("CREATE TABLE Users(user_id INT PRIMARY KEY,username VARCHAR(50),full_name NVARCHAR(100),auth_version INT)");
                String migration = Files.readString(Path.of("../database/migrations/007_account_profile.sql"));
                st.execute(migration); st.execute(migration);
                st.execute("CREATE TABLE Customers(customer_id INT PRIMARY KEY)");
                com.homestay.db.AccountRegistrationMigration.migrate(conn);
                st.execute("INSERT INTO Users(user_id,username,full_name,auth_version) VALUES(1,'customer','Staff',0),(2,'admin','Admin',0)");
                ProcessBuilder child = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                    "-cp",System.getProperty("java.class.path"),AccountProfileSqlServerIT.class.getName(),"--child");
                child.environment().put("DB_NAME",database);
                Process process=child.inheritIO().start();
                if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor(); throw new AssertionError("Profile tests timed out");
                }
                if (process.exitValue()!=0) throw new AssertionError("Profile tests failed");
            } finally {
                st.execute("USE master");
                st.execute("ALTER DATABASE ["+database+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                st.execute("DROP DATABASE ["+database+"]");
                System.out.println("Disposed isolated account profile test database.");
            }
        }
    }
    private static void checks() throws Exception {
        if (!System.getenv("DB_NAME").matches("HomestayProfileTest_[a-f0-9]{32}")) throw new AssertionError("Unsafe test database");
        AuthController controller=new AuthController(null,null,null,null,null,new com.homestay.security.AccountSettings(new org.springframework.core.env.StandardEnvironment()));
        for (String role:List.of("customer","admin")) {
            int id=role.equals("customer")?1:2;
            AppUser user=new AppUser(id,role,"unused",role,role,0);
            var auth=UsernamePasswordAuthenticationToken.authenticated(user,null,user.getAuthorities());
            Map<String,Object> input=new HashMap<>(Map.of("fullName","Profile Updated","phone","0901234567","email",role+"@gmail.com","birthDate","2000-02-29","city","Ho Chi Minh","userId",3-id,"role","admin"));
            if(controller.updateProfile(input,auth).getStatusCode().value()!=200) throw new AssertionError("Valid update rejected");
            var profile=(Map<?,?>)controller.me(auth).get("user");
            if(!"Profile Updated".equals(profile.get("fullName")) || !input.get("email").equals(profile.get("email")) || !role.equals(profile.get("role"))) throw new AssertionError("Profile persistence or identity mismatch");
            for (var invalid:Map.of("phone","123","email","bad-email","birthDate","2999-01-01","city","","fullName","").entrySet()) {
                Map<String,Object> bad=new HashMap<>(input); bad.put(invalid.getKey(),invalid.getValue());
                if(controller.updateProfile(bad,auth).getStatusCode().value()!=400) throw new AssertionError("Invalid field accepted: "+invalid.getKey());
            }
            var stale=new AppUser(id,role,"unused",role,role,1);
            if(controller.updateProfile(input,UsernamePasswordAuthenticationToken.authenticated(stale,null,stale.getAuthorities())).getStatusCode().value()!=401) throw new AssertionError("Stale session accepted");
            System.out.println("PASS "+role+" profile persistence, validation, own-account scope and stale-session rejection");
        }
    }
}
