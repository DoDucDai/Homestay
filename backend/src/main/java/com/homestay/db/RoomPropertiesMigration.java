package com.homestay.db;

import java.sql.*;

/** Initial property assignment; later property/address edits are never overwritten. */
// NOTE BACKEND: Tạo bảng cơ sở và gán cơ sở ban đầu cho phòng cũ; không ghi đè thông tin cơ sở ở những lần chạy sau.
public final class RoomPropertiesMigration {
    private RoomPropertiesMigration() {}
    // NOTE BACKEND: Trong giao dịch, tạo cơ sở mặc định và cột property_id nếu chưa có, kèm liên kết khóa ngoại từ Rooms sang Properties.
    public static void migrate(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try(var st=conn.createStatement()) {
            st.execute("SET XACT_ABORT ON; SET LOCK_TIMEOUT 30000;");
            try(var rs=st.executeQuery("SELECT COUNT(*) FROM dbo.Rooms WITH(TABLOCKX,HOLDLOCK)")){rs.next();}
            st.execute("IF OBJECT_ID('dbo.Properties','U') IS NULL CREATE TABLE dbo.Properties(property_id INT PRIMARY KEY,property_name NVARCHAR(100) NOT NULL,address NVARCHAR(250) NOT NULL);");
            st.execute("IF NOT EXISTS(SELECT 1 FROM dbo.Properties WHERE property_id=1) INSERT dbo.Properties(property_id,property_name,address) VALUES(1,N'Cơ sở 1',N'123 Đường Hoa Mai, TP. Hồ Chí Minh');");
            st.execute("IF COL_LENGTH('dbo.Rooms','property_id') IS NULL ALTER TABLE dbo.Rooms ADD property_id INT NOT NULL CONSTRAINT DF_Rooms_Property DEFAULT 1 CONSTRAINT FK_Rooms_Property REFERENCES dbo.Properties(property_id);");
            conn.commit();
        } catch(SQLException error){try{conn.rollback();}catch(SQLException rollback){error.addSuppressed(rollback);}throw error;}
    }
    // NOTE BACKEND: Điểm chạy riêng migration cơ sở khi cần thao tác từ công cụ dòng lệnh.
    public static void main(String[] args) throws Exception {
        try(var conn=DBConnection.getConnection()){migrate(conn);}
        System.out.println("Existing rooms assigned to Co so 1; subsequent property changes preserved.");
    }
}
