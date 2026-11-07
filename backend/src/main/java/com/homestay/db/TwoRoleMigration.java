package com.homestay.db;

import java.sql.Connection;

/** Retire legacy roles without changing credentials, guest records or invoices. */
public final class TwoRoleMigration {
    private TwoRoleMigration() {}
    public static void migrate(Connection connection) throws Exception {
        AccountRegistrationMigration.runScript(connection,"/db/009_two_roles.sql");
    }
    public static void main(String[] args) throws Exception {
        try(var connection=DBConnection.getConnection()) {
            migrate(connection);
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT role,COUNT(*) FROM Users GROUP BY role")) {
                while(rows.next())System.out.println(rows.getString(1)+": "+rows.getInt(2));
            }
        }
    }
}
