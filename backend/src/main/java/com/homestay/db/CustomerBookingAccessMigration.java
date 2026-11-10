package com.homestay.db;

import java.sql.Connection;

/** Prepare ownership queries and payment metadata without reassigning existing people. */
public final class CustomerBookingAccessMigration {
    private CustomerBookingAccessMigration() {}
    public static void migrate(Connection connection) throws Exception {
        AccountRegistrationMigration.runScript(connection,"/db/010_customer_booking_access.sql");
        AccountRegistrationMigration.runScript(connection,"/db/011_service_units.sql");
    }
}
