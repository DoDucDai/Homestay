package com.homestay.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/** Reviewed poster metadata, imported once. Reservations and invoice snapshots retain their prices. */
public final class RoomCatalogMigration {
    private static final String KEY="poster-room-catalog-20261103-v1";
    private static final Pattern CODE=Pattern.compile("^(?:(?:DEMO-)?P|ROOM\\s*)(\\d{3})$",Pattern.CASE_INSENSITIVE);
    private RoomCatalogMigration() {}
    private record Room(int id,String code,String number) {}

    public static int resolveId(Connection conn,int id) throws SQLException {
        // Older databases and standalone controller tests may not have run this migration yet.
        try(var st=conn.createStatement();var rs=st.executeQuery("SELECT OBJECT_ID('dbo.RoomCatalogAliases','U')")) {
            rs.next();if(rs.getObject(1)==null)return id;
        }
        try(var st=conn.prepareStatement("SELECT room_id FROM dbo.RoomCatalogAliases WHERE legacy_room_id=?")) {
            st.setInt(1,id);try(var rs=st.executeQuery()){if(rs.next())return rs.getInt(1);}
        }
        return id;
    }

    public static void migrate(Connection conn) throws SQLException,IOException {
        List<Map<String,String>> catalog;
        try(var source=RoomCatalogMigration.class.getResourceAsStream("/room-catalog.json")) {
            if(source==null)throw new IOException("Missing reviewed room catalog");
            catalog=new ObjectMapper().readValue(source,new TypeReference<>(){});
        }
        conn.setAutoCommit(false);
        try(var st=conn.createStatement()) {
            st.execute("SET XACT_ABORT ON; SET LOCK_TIMEOUT 30000;");
            try(var rs=st.executeQuery("SELECT COUNT(*) FROM dbo.Rooms WITH(TABLOCKX,HOLDLOCK)")){rs.next();}
            try(var rs=st.executeQuery("SELECT COUNT(*) FROM dbo.Bookings WITH(TABLOCKX,HOLDLOCK)")){rs.next();}
            st.execute("IF OBJECT_ID('dbo.RoomCatalogMigrations','U') IS NULL CREATE TABLE dbo.RoomCatalogMigrations(migration_key VARCHAR(80) PRIMARY KEY,before_json NVARCHAR(MAX) NOT NULL,migrated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());");
            st.execute("IF OBJECT_ID('dbo.RoomCatalogAliases','U') IS NULL CREATE TABLE dbo.RoomCatalogAliases(legacy_room_id INT PRIMARY KEY,room_id INT NOT NULL REFERENCES dbo.Rooms(room_id),legacy_code NVARCHAR(20) NOT NULL);");
            try(var check=conn.prepareStatement("SELECT 1 FROM dbo.RoomCatalogMigrations WHERE migration_key=?")) {
                check.setString(1,KEY);try(var rs=check.executeQuery()){if(rs.next()){conn.commit();return;}}
            }
            // Store all original rows and reservation-to-room assignments inside the same transaction.
            try(var backup=conn.prepareStatement("INSERT dbo.RoomCatalogMigrations(migration_key,before_json) SELECT ?, (SELECT JSON_QUERY((SELECT * FROM dbo.Rooms FOR JSON PATH,INCLUDE_NULL_VALUES)) AS rooms,JSON_QUERY((SELECT booking_id,room_id FROM dbo.Bookings FOR JSON PATH)) AS bookingRooms FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)")) {
                backup.setString(1,KEY);backup.executeUpdate();
            }
            Map<String,List<Room>> groups=new TreeMap<>();
            try(var rs=st.executeQuery("SELECT room_id,room_code FROM dbo.Rooms ORDER BY room_id")) {
                while(rs.next()) {
                    var code=rs.getString(2);var match=CODE.matcher(code.trim());
                    if(match.matches())groups.computeIfAbsent(match.group(1),n->new ArrayList<>()).add(new Room(rs.getInt(1),code,match.group(1)));
                }
            }
            for(var entry:groups.entrySet()) {
                var rooms=entry.getValue();
                // Keep an existing canonical ID, then the imported poster room ID, then the oldest ID.
                rooms.sort(Comparator.comparingInt((Room r)->r.code.equalsIgnoreCase("Room "+r.number)?0:r.code.startsWith("DEMO-")?1:2).thenComparingInt(Room::id));
                var keep=rooms.get(0);
                for(var old:rooms.subList(1,rooms.size())) {
                    try(var overlap=conn.prepareStatement("SELECT TOP 1 a.booking_id FROM dbo.Bookings a JOIN dbo.Bookings b ON a.room_id=? AND b.room_id=? AND a.status IN ('Confirmed','CheckedIn') AND b.status IN ('Confirmed','CheckedIn') AND COALESCE(a.checkin_at,CAST(a.checkin_date AS DATETIME2))<COALESCE(b.checkout_at,CAST(b.checkout_date AS DATETIME2)) AND COALESCE(b.checkin_at,CAST(b.checkin_date AS DATETIME2))<COALESCE(a.checkout_at,CAST(a.checkout_date AS DATETIME2))")) {
                        overlap.setInt(1,keep.id);overlap.setInt(2,old.id);
                        try(var rs=overlap.executeQuery()){if(rs.next())throw new SQLException("Cannot merge Room "+keep.number+": active reservations overlap. No catalog changes committed.");}
                    }
                    try(var update=conn.prepareStatement("UPDATE dbo.Bookings SET room_id=? WHERE room_id=?")) {update.setInt(1,keep.id);update.setInt(2,old.id);update.executeUpdate();}
                    try(var alias=conn.prepareStatement("UPDATE dbo.RoomCatalogAliases SET room_id=? WHERE room_id=?; INSERT dbo.RoomCatalogAliases(legacy_room_id,room_id,legacy_code) VALUES(?,?,?)")) {
                        alias.setInt(1,keep.id);alias.setInt(2,old.id);alias.setInt(3,old.id);alias.setInt(4,keep.id);alias.setString(5,old.code);alias.executeUpdate();
                    }
                    try(var remove=conn.prepareStatement("DELETE dbo.Rooms WHERE room_id=?")){remove.setInt(1,old.id);remove.executeUpdate();}
                }
                var poster=catalog.stream().filter(p->p.get("number").equals(keep.number)).findFirst();
                String name=poster.map(p->p.get("name")).orElse("Room "+keep.number);
                String description=poster.map(p->p.get("description")).orElse("Thông tin phòng đang được cập nhật.");
                try(var update=conn.prepareStatement("UPDATE dbo.Rooms SET room_code=?,room_name=?,description=?,room_type=COALESCE(?,room_type) WHERE room_id=?")) {
                    update.setString(1,"Room "+keep.number);update.setString(2,name);update.setString(3,description);
                    update.setString(4,poster.map(p->p.get("type")).orElse(null));update.setInt(5,keep.id);update.executeUpdate();
                }
            }
            conn.commit();
        } catch(SQLException|RuntimeException error) {try{conn.rollback();}catch(SQLException rollback){error.addSuppressed(rollback);}throw error;}
    }
    public static void main(String[] args) throws Exception {
        try(var conn=DBConnection.getConnection()){migrate(conn);}
        System.out.println("Room catalog merged and imported; original catalog archived in database.");
    }
}
