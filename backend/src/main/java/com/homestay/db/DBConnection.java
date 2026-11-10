package com.homestay.db;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Shared JDBC configuration; production never falls back to a local .env. */
// NOTE BACKEND: Nơi duy nhất tạo kết nối JDBC dùng chung; đọc cấu hình database và kiểm tra các yêu cầu của môi trường prod.
public final class DBConnection {
    private static volatile Settings settings;
    private record Settings(String host,int port,String database,String username,String password,boolean encrypt,boolean trust) {}
    private DBConnection() {}

    // NOTE BACKEND: Nạp cấu hình từ Spring/môi trường; prod không lấy thông tin từ file .env cục bộ và yêu cầu cookie chạy qua HTTPS.
    public static synchronized void configure(Environment environment) {
        boolean prod=environment.acceptsProfiles(Profiles.of("prod"));
        Map<String,String> local=prod ? Map.of() : readLocalEnv();
        Function<String,String> value=key -> {
            if(prod) {
                String explicit=System.getenv(key);
                return explicit!=null && !explicit.isBlank()?explicit:System.getProperty(key);
            }
            String configured=environment.getProperty(Objects.requireNonNull(key));
            return configured!=null && !configured.isBlank() ? configured : local.get(key);
        };
        if(prod && !"true".equalsIgnoreCase(value.apply("COOKIE_SECURE"))) {
            throw new IllegalStateException("Production requires COOKIE_SECURE=true and HTTPS at the reverse proxy.");
        }
        settings=resolve(value,prod);
    }
    // NOTE BACKEND: Tìm file .env theo các đường dẫn làm việc thường dùng, đọc cặp tên=giá trị; chỉ phục vụ cấu hình cục bộ.
    private static Map<String,String> readLocalEnv() {
        for(Path file:List.of(Path.of(".env"),Path.of("../.env"),Path.of("backend/.env"))) {
            if(!Files.isRegularFile(file)) continue;
            Map<String,String> result=new HashMap<>();
            try {
                for(String raw:Files.readAllLines(file,StandardCharsets.UTF_8)) {
                    String line=raw.trim();if(line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                    int split=line.indexOf('=');String value=line.substring(split+1).trim();
                    if(value.length()>=2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) value=value.substring(1,value.length()-1);
                    result.putIfAbsent(line.substring(0,split).trim(),value);
                }
                return result;
            } catch(java.io.IOException error) {throw new IllegalStateException("Cannot read local database configuration.");}
        }
        return Map.of();
    }
    // NOTE BACKEND: Lấy giá trị cấu hình không rỗng, dùng giá trị mặc định hoặc báo lỗi nếu thiếu cấu hình bắt buộc.
    private static String value(Function<String,String> source,String key,String fallback) {
        String value=source.apply(key);if(value!=null && !value.isBlank()) return value;
        if(fallback==null) throw new IllegalStateException("Missing required configuration: "+key);
        return fallback;
    }
    // NOTE BACKEND: Chỉ chấp nhận true/false cho các cờ cấu hình để tránh hiểu nhầm một giá trị gõ sai.
    private static boolean flag(Function<String,String> source,String key,boolean fallback) {
        String raw=value(source,key,Boolean.toString(fallback));
        if(!Set.of("true","false").contains(raw.toLowerCase(Locale.ROOT))) throw new IllegalStateException("Invalid boolean configuration: "+key);
        return Boolean.parseBoolean(raw);
    }
    // NOTE BACKEND: Kiểm tra host, cổng, database, tài khoản và TLS; ở prod phải mã hóa kết nối và kiểm tra chứng chỉ SQL Server.
    private static Settings resolve(Function<String,String> source,boolean prod) {
        String host=value(source,"DB_HOST",prod?null:"localhost"),database=value(source,"DB_NAME",prod?null:"HomestayDB"),user=value(source,"DB_USER",prod?null:"sa");
        int port;
        try {port=Integer.parseInt(value(source,"DB_PORT",prod?null:"1433"));if(port<1 || port>65535) throw new NumberFormatException();}
        catch(NumberFormatException error) {throw new IllegalStateException("DB_PORT must be between 1 and 65535.");}
        if(host.contains(";") || database.contains(";")) throw new IllegalStateException("Database host/name cannot contain JDBC delimiters.");
        String password=value(source,"DB_PASSWORD",null);
        boolean encrypt=flag(source,"DB_ENCRYPT",true),trust=flag(source,"DB_TRUST_SERVER_CERTIFICATE",!prod);
        if(prod && (!encrypt || trust)) throw new IllegalStateException("Production requires DB_ENCRYPT=true and DB_TRUST_SERVER_CERTIFICATE=false.");
        return new Settings(host,port,database,user,password,encrypt,trust);
    }
    // NOTE BACKEND: Dùng cấu hình đã nạp; khi chạy công cụ Java độc lập thì tự đọc môi trường/system property và .env.
    private static Settings current() {
        Settings configured=settings;if(configured!=null) return configured;
        Map<String,String> local=readLocalEnv();
        return resolve(key -> {
            String value=System.getenv(key);if(value!=null && !value.isBlank()) return value;
            value=System.getProperty(key);return value!=null && !value.isBlank()?value:local.get(key);
        },false);
    }
    // NOTE BACKEND: Tạo URL JDBC và mở kết nối SQL Server bằng Properties chứa thông tin đăng nhập; bên gọi chịu trách nhiệm đóng kết nối.
    public static Connection getConnection() throws SQLException {
        Settings cfg=current();
        String url="jdbc:sqlserver://"+cfg.host()+":"+cfg.port()+";databaseName="+cfg.database()+";encrypt="+cfg.encrypt()+";trustServerCertificate="+cfg.trust();
        Properties credentials=new Properties();credentials.setProperty("user",cfg.username());credentials.setProperty("password",cfg.password());
        return DriverManager.getConnection(url,credentials);
    }
    // NOTE BACKEND: Công cụ kiểm tra mở kết nối; không đọc, sửa dữ liệu nghiệp vụ hoặc in mật khẩu.
    public static void main(String[] args) throws SQLException {
        try(Connection conn=getConnection()) {
            if(!conn.isValid(5)) throw new SQLException("Database connection validation failed.");
            System.out.println("Database connection verified.");
        }
    }
}
