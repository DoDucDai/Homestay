package com.homestay;

import com.homestay.db.DBConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseSettings {
    public DatabaseSettings(Environment environment) {DBConnection.configure(environment);}
}
