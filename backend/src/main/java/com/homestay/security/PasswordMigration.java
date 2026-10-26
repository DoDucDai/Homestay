package com.homestay.security;

import com.homestay.db.DBConnection;
import java.sql.*;
import java.util.*;

/** Offline migration only: preserves passwords, never enables plaintext login. */
public final class PasswordMigration {
    private PasswordMigration() {}
    public static int migrate(Connection conn) throws SQLException {
        if (!conn.getAutoCommit()) throw new SQLException("Migration requires its own connection");
        conn.setAutoCommit(false);
        boolean transactionEnded=false;
        try {
            record Row(int id,String password,String scheme) {}
            List<Row> rows=new ArrayList<>();
            try(PreparedStatement stmt=conn.prepareStatement("SELECT user_id,password,password_scheme FROM Users WITH(TABLOCKX,HOLDLOCK)");
                ResultSet rs=stmt.executeQuery()) {
                while(rs.next()) rows.add(new Row(rs.getInt(1),rs.getString(2),rs.getString(3)));
            }
            int migrated=0;
            var encoder=PasswordHashing.encoder();
            for(Row row:rows) {
                if (PasswordHashing.SCHEME.equals(row.scheme())) {
                    if (!PasswordHashing.validHash(row.password())) throw new SQLException("Invalid encoded password; manual review required");
                    continue;
                }
                if (!"legacy".equals(row.scheme())) throw new SQLException("Unknown password scheme; manual review required");
                String hash=encoder.encode(row.password());
                try(PreparedStatement stmt=conn.prepareStatement("UPDATE Users SET password=?,password_scheme=?,auth_version=auth_version+1 WHERE user_id=? AND password_scheme='legacy'")) {
                    stmt.setString(1,hash);stmt.setString(2,PasswordHashing.SCHEME);stmt.setInt(3,row.id());
                    if (stmt.executeUpdate()!=1) throw new SQLException("Account changed during migration");
                }
                migrated++;
            }
            conn.commit();transactionEnded=true;return migrated;
        } catch(Exception e) {
            try { conn.rollback();transactionEnded=true; }
            catch(SQLException rollbackError) { e.addSuppressed(rollbackError); }
            throw new SQLException("Password migration failed; verify rollback before retrying. No passwords were printed or reset",e);
        } finally {
            // Switching to auto-commit can commit pending work if rollback failed.
            // The caller owns/closes the connection; leave an uncertain TX alone.
            if (transactionEnded) conn.setAutoCommit(true);
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=1 || !"--apply".equals(args[0])) {
            throw new IllegalArgumentException("Stop app writes and back up first. Run with --apply to migrate the configured database.");
        }
        try(Connection conn=DBConnection.getConnection()) {
            System.out.println("Password accounts migrated: "+migrate(conn));
        }
    }
}
