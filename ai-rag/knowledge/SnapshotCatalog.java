import com.homestay.db.DBConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;

/** Read-only export: room/service presentation data only, never guests or bookings. */
public class SnapshotCatalog {
    public static void main(String[] args) throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        try(Connection db=DBConnection.getConnection()) {
            for(String kind:List.of("rooms","services")) {
                String sql=kind.equals("rooms")
                    ? "SELECT room_id,room_code,room_name,room_type,price,hourly_price,description FROM Rooms WHERE status<>N'Bảo trì' ORDER BY room_code"
                    : "SELECT service_id,service_name,unit_price,status FROM Services ORDER BY service_id";
                List<Map<String,Object>> rows=new ArrayList<>();
                try(PreparedStatement query=db.prepareStatement(sql);ResultSet rs=query.executeQuery()) {
                    ResultSetMetaData meta=rs.getMetaData();
                    while(rs.next()) {
                        Map<String,Object> row=new LinkedHashMap<>();
                        for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i),rs.getObject(i));
                        rows.add(row);
                    }
                }
                result.put(kind,rows);
            }
        }
        System.out.println(new ObjectMapper().writeValueAsString(result));
    }
}
