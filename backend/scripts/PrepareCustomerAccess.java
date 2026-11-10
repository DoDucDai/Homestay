import com.homestay.db.*;
import com.homestay.security.CustomerOwnership;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Maintenance only: backup, inventory, apply migration 010 and explicitly link a verified account. */
public class PrepareCustomerAccess {
    static String literal(String value) {return "N'"+value.replace("'","''")+"'";}
    static String identifier(String value) {return "["+value.replace("]","]]")+"]";}
    static List<Map<String,Object>> query(Connection connection,String sql) throws SQLException {
        List<Map<String,Object>> result=new ArrayList<>();
        try(var statement=connection.createStatement();var rows=statement.executeQuery(sql)) {
            while(rows.next()) {
                Map<String,Object> row=new LinkedHashMap<>();
                for(int i=1;i<=rows.getMetaData().getColumnCount();i++) row.put(rows.getMetaData().getColumnLabel(i),rows.getObject(i));
                result.add(row);
            }
        }
        return result;
    }
    static Map<String,Object> inventory(Connection connection) throws SQLException {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("customerAccounts",query(connection,"SELECT user_id AS userId,customer_id AS customerId FROM Users WHERE role='customer' ORDER BY user_id"));
        result.put("totals",query(connection,"SELECT (SELECT COUNT_BIG(*) FROM Customers) AS customers,(SELECT COUNT_BIG(*) FROM Bookings) AS bookings,(SELECT COUNT_BIG(*) FROM Payments) AS payments,(SELECT COUNT_BIG(*) FROM Bookings b WHERE NOT EXISTS(SELECT 1 FROM Users u WHERE u.customer_id=b.customer_id AND u.role='customer')) AS bookingsWithoutCustomerAccount"));
        return result;
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=5 && args.length!=6) throw new IllegalArgumentException("Usage: PrepareCustomerAccess.java database userId customerId bookingId expectedUsername [backupArchiveDirectory]");
        String database=args[0];int userId=Integer.parseInt(args[1]),customerId=Integer.parseInt(args[2]),bookingId=Integer.parseInt(args[3]);
        Path directory=Path.of("target/customer-access-setup").toAbsolutePath().normalize();Files.createDirectories(directory);
        Map<String,Object> report=new LinkedHashMap<>();report.put("startedAt",OffsetDateTime.now().toString());report.put("database",database);
        var json=new ObjectMapper().writerWithDefaultPrettyPrinter();
        try(Connection connection=DBConnection.getConnection()) {
            if(!database.equals(connection.getCatalog())) throw new IllegalStateException("Connected database does not match the explicitly named database");
            report.put("before",inventory(connection));
            String filename=database.replaceAll("[^A-Za-z0-9_-]","_")+"_before_customer_access_"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))+"_"+UUID.randomUUID().toString().substring(0,8)+".bak";
            Path backup=directory.resolve(filename).normalize();
            if(!backup.getParent().equals(directory)) throw new IllegalStateException("Unexpected backup path");
            try(var statement=connection.createStatement()) {
                statement.setQueryTimeout(120);
                statement.execute("BACKUP DATABASE "+identifier(database)+" TO DISK="+literal(backup.toString())+" WITH COPY_ONLY,CHECKSUM");
                statement.execute("RESTORE VERIFYONLY FROM DISK="+literal(backup.toString())+" WITH CHECKSUM");
            }
            report.put("backup",backup.toString());report.put("backupVerified",true);
            if(args.length==6) {
                Path archive=Path.of(args[5]).toAbsolutePath().normalize();Files.createDirectories(archive);
                Path retained=archive.resolve(filename);Files.copy(backup,retained);
                report.put("retainedBackup",retained.toString());
                Files.writeString(archive.resolve("maintenance-before.json"),json.writeValueAsString(report));
            }
            Files.writeString(directory.resolve("maintenance.json"),json.writeValueAsString(report));
            // Data writes start only after the copy-only backup has been verified.
            CustomerBookingAccessMigration.migrate(connection);report.put("migration","010_customer_booking_access.sql");
            connection.setAutoCommit(false);
            try {
                try(var statement=connection.prepareStatement("SELECT u.user_id FROM Users u JOIN Bookings b ON b.booking_id=? AND b.customer_id=? WHERE u.user_id=? AND u.username=? AND u.role='customer'")) {
                    statement.setInt(1,bookingId);statement.setInt(2,customerId);statement.setInt(3,userId);statement.setString(4,args[4]);
                    try(var rows=statement.executeQuery()) {if(!rows.next()) throw new IllegalStateException("Explicit account/profile/booking mapping could not be established");}
                }
                CustomerOwnership.linkExisting(connection,userId,customerId);connection.commit();
            } catch(Exception error) {connection.rollback();throw error;}
            finally {connection.setAutoCommit(true);}
            report.put("linked",Map.of("userId",userId,"customerId",customerId,"bookingId",bookingId));
            report.put("after",inventory(connection));report.put("completedAt",OffsetDateTime.now().toString());
            Files.writeString(directory.resolve("maintenance.json"),json.writeValueAsString(report));
            if(args.length==6) Files.writeString(Path.of(args[5]).resolve("maintenance-after.json"),json.writeValueAsString(report));
        }
        System.out.println(json.writeValueAsString(report));
    }
}
