package com.homestay.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/** Add reviewed new room numbers once; never change an existing room or historical booking. */
public final class RoomExpansionMigration {
    private static final String KEY="room-expansion-74-20261103-v1";
    private RoomExpansionMigration() {}
    public static void migrate(Connection conn) throws SQLException,IOException {
        com.fasterxml.jackson.databind.JsonNode data;
        try(var source=RoomExpansionMigration.class.getResourceAsStream("/room-expansion.json")) {
            if(source==null)throw new IOException("Missing reviewed room expansion");
            data=new ObjectMapper().readTree(source);
        }
        conn.setAutoCommit(false);
        try(var st=conn.createStatement()) {
            st.execute("SET XACT_ABORT ON; SET LOCK_TIMEOUT 30000;");
            try(var rs=st.executeQuery("SELECT COUNT(*) FROM dbo.Rooms WITH(TABLOCKX,HOLDLOCK)")){rs.next();}
            st.execute("IF OBJECT_ID('dbo.RoomExpansionImports','U') IS NULL CREATE TABLE dbo.RoomExpansionImports(import_key VARCHAR(80) PRIMARY KEY,room_codes NVARCHAR(MAX) NOT NULL,created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());");
            try(var check=conn.prepareStatement("SELECT 1 FROM dbo.RoomExpansionImports WHERE import_key=?")) {
                check.setString(1,KEY);try(var rs=check.executeQuery()){if(rs.next()){conn.commit();return;}}
            }
            for(var property:data.get("properties"))try(var insert=conn.prepareStatement("IF NOT EXISTS(SELECT 1 FROM dbo.Properties WHERE property_id=?) INSERT dbo.Properties(property_id,property_name,address) VALUES(?,?,?)")) {
                insert.setInt(1,property.get("propertyId").asInt());insert.setInt(2,property.get("propertyId").asInt());
                insert.setString(3,property.get("propertyName").asText());insert.setString(4,property.get("propertyAddress").asText());insert.executeUpdate();
            }
            List<String> inserted=new ArrayList<>();
            for(var room:data.get("rooms")) {
                String number=room.get("number").asText(),code="Room "+number;
                try(var check=conn.prepareStatement("SELECT 1 FROM dbo.Rooms WHERE room_code IN (?,?,?)")) {
                    check.setString(1,code);check.setString(2,"P"+number);check.setString(3,"DEMO-P"+number);
                    try(var rs=check.executeQuery()){if(rs.next())continue;}
                }
                try(var insert=conn.prepareStatement("INSERT dbo.Rooms(room_code,room_name,room_type,price,status,description,property_id,hourly_price,hourly_package_2,hourly_package_4,hourly_package_8,weekend_price,weekend_days) VALUES(?,?,'Double',?,N'Trống',?,?,?,?,?,?,?,?)")) {
                    insert.setString(1,code);insert.setString(2,room.get("name").asText());insert.setLong(3,data.get("price").asLong());
                    insert.setString(4,room.get("description").asText());insert.setInt(5,room.get("propertyId").asInt());
                    insert.setLong(6,data.get("hourlyPrice").asLong());insert.setLong(7,data.get("twoHours").asLong());
                    insert.setLong(8,data.get("fourHours").asLong());insert.setLong(9,data.get("eightHours").asLong());
                    insert.setLong(10,data.get("weekendPrice").asLong());insert.setString(11,data.get("weekendDays").asText());
                    insert.executeUpdate();inserted.add(code);
                }
            }
            try(var mark=conn.prepareStatement("INSERT dbo.RoomExpansionImports(import_key,room_codes) VALUES(?,?)")) {
                mark.setString(1,KEY);mark.setString(2,new ObjectMapper().writeValueAsString(inserted));mark.executeUpdate();
            }
            conn.commit();
        } catch(SQLException|IOException|RuntimeException error){try{conn.rollback();}catch(SQLException rollback){error.addSuppressed(rollback);}throw error;}
    }
    public static void main(String[] args) throws Exception {
        try(var conn=DBConnection.getConnection()){migrate(conn);}
        System.out.println("Reviewed new rooms imported once; existing room codes skipped.");
    }
}
