package com.homestay;

import com.homestay.db.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class HourlyPricingStartup implements ApplicationRunner {
    public HourlyPricingStartup(DatabaseSettings settings) {}
    @Override public void run(ApplicationArguments arguments) throws Exception {
        try(var connection=DBConnection.getConnection()){
            HourlyPricingMigration.migrate(connection);
            RoomCatalogMigration.migrate(connection);
            RoomPropertiesMigration.migrate(connection);
            RoomExpansionMigration.migrate(connection);
        }
        System.out.println("Hourly package pricing schema ready; existing booking prices preserved.");
    }
}
