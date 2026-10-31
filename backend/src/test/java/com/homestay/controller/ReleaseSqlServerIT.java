package com.homestay.controller;

import com.homestay.db.DBConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/** Builds/migrates/backs up/restores UUID databases, then boots the release JAR from an empty directory. */
public class ReleaseSqlServerIT {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static int passed;
    private static void require(boolean condition,String message) {if(!condition) throw new AssertionError(message);}
    private static void pass(String name) {passed++;System.out.println("PASS "+name);}
    private static String quote(String input) {return input.replace("'","''");}
    private static String scalar(Statement stmt,String query) throws SQLException {try(ResultSet rs=stmt.executeQuery(query)){require(rs.next(),"Missing scalar result");return rs.getString(1);}}
    private static void batches(Statement stmt,String script) throws SQLException {
        for(String batch:script.split("(?im)^\\s*GO\\s*$")) if(!batch.isBlank()) stmt.execute(batch);
    }
    public static void main(String[] args) throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","");
        String source="HomestayReleaseTest_"+suffix,restored="HomestayRestoreTest_"+suffix;
        Path jar=Path.of("target/homestay-backend-1.0.0.jar").toAbsolutePath();
        require(Files.isRegularFile(jar),"Build release JAR before running release checks");
        try(JarFile archive=new JarFile(jar.toFile())) {
            require(archive.getEntry("BOOT-INF/classes/static/pages/login.html")!=null,"JAR lacks frontend");
            require(archive.getEntry("BOOT-INF/classes/static/js/account.js")!=null,"JAR lacks account controls");
            require(archive.getEntry("BOOT-INF/classes/static/css/invoice-print.css")!=null,"JAR lacks invoice print stylesheet");
            require(archive.stream().noneMatch(e->e.getName().endsWith("/.env") || e.getName().equals(".env")),"JAR contains .env");
        }
        pass("RELEASE-01 executable JAR embeds frontend and excludes .env");
        boolean created=false,restoredCreated=false;String backup=null;
        try(Connection connection=DBConnection.getConnection();Statement stmt=connection.createStatement()) {
            try {
                stmt.execute("CREATE DATABASE ["+source+"]");created=true;stmt.execute("USE ["+source+"]");
                String schema=Files.readString(Path.of("../database/schema.sql"));batches(stmt,schema.substring(schema.indexOf("CREATE TABLE Users")));
                batches(stmt,Files.readString(Path.of("../database/seed_data.sql")).replace("USE HomestayDB;","USE ["+source+"];") );
                try(var migrations=Files.list(Path.of("../database/migrations"))) {
                    for(Path file:migrations.filter(p->p.toString().endsWith(".sql")).sorted().toList()) {String script=Files.readString(file);batches(stmt,script);batches(stmt,script);}
                }
                require("2".equals(scalar(stmt,"SELECT COUNT(*) FROM Users WHERE password_scheme='pbkdf2-v58'")),"Migration changed account schemes");
                require("1".equals(scalar(stmt,"SELECT COUNT(*) FROM Payments WHERE ISJSON(invoice_snapshot)=1")),"Migration lost invoice snapshot");
                pass("RELEASE-01 schema/seed and migrations 001-005 run repeatedly without losing accounts/invoices");
                var isolatedEnvironment=new org.springframework.core.env.StandardEnvironment();
                isolatedEnvironment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("release-fixture",Map.of("DB_NAME",source)));
                DBConnection.configure(isolatedEnvironment);
                var productionEnvironment=new org.springframework.core.env.StandardEnvironment();productionEnvironment.setActiveProfiles("prod");
                boolean demoRejected=false;
                try {new com.homestay.security.SecurityConfig().checkAccountMigration(productionEnvironment).run(null);}
                catch(IllegalStateException expected) {demoRejected=expected.getMessage().contains("demo credentials");}
                finally {DBConnection.configure(new org.springframework.core.env.StandardEnvironment());}
                require(demoRejected,"Production accepted seed demo passwords");
                pass("RELEASE-01 production refuses seeded demo credentials without changing any password");
                String backupDirectory=scalar(stmt,"SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))");
                String dataDirectory=scalar(stmt,"SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultDataPath'))");
                String logDirectory=scalar(stmt,"SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultLogPath'))");
                require(dataDirectory!=null && logDirectory!=null,"SQL Server default data/log paths unavailable");
                if(backupDirectory==null) backupDirectory=dataDirectory;
                backup=backupDirectory+(backupDirectory.endsWith("\\")||backupDirectory.endsWith("/")?"":"\\")+source+".bak";
                stmt.execute("BACKUP DATABASE ["+source+"] TO DISK=N'"+quote(backup)+"' WITH COPY_ONLY,CHECKSUM,INIT");
                stmt.execute("RESTORE VERIFYONLY FROM DISK=N'"+quote(backup)+"' WITH CHECKSUM");
                List<String> moves=new ArrayList<>();
                try(ResultSet files=stmt.executeQuery("RESTORE FILELISTONLY FROM DISK=N'"+quote(backup)+"'")) {
                    int index=0;while(files.next()) {
                        String type=files.getString("Type"),folder=type.equals("L")?logDirectory:dataDirectory;
                        String path=folder+(folder.endsWith("\\")||folder.endsWith("/")?"":"\\")+restored+"_"+(index++)+(type.equals("L")?".ldf":".mdf");
                        moves.add("MOVE N'"+quote(files.getString("LogicalName"))+"' TO N'"+quote(path)+"'");
                    }
                }
                stmt.execute("USE [master]");
                stmt.execute("RESTORE DATABASE ["+restored+"] FROM DISK=N'"+quote(backup)+"' WITH CHECKSUM,"+String.join(",",moves));restoredCreated=true;
                stmt.execute("USE ["+restored+"]");
                require("6".equals(scalar(stmt,"SELECT COUNT(*) FROM Rooms")),"Restored room count mismatch");
                require("5".equals(scalar(stmt,"SELECT COUNT(*) FROM Bookings")),"Restored booking count mismatch");
                require("1".equals(scalar(stmt,"SELECT COUNT(*) FROM Payments WHERE ISJSON(invoice_snapshot)=1")),"Restored invoice invalid");
                stmt.execute("USE [master]");
                pass("RELEASE-01 COPY_ONLY/CHECKSUM backup verifies and restores complete data to a separate database");
                smokeJar(jar,restored);
                invalidProduction(jar,false);invalidProduction(jar,true);productionIgnoresLocalFile(jar);
            } finally {
                stmt.execute("USE [master]");
                for(String database:restoredCreated?List.of(restored,source):created?List.of(source):List.<String>of()) {
                    require(database.matches("Homestay(Release|Restore)Test_[a-f0-9]{32}"),"Unsafe cleanup target");
                    stmt.execute("ALTER DATABASE ["+database+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");stmt.execute("DROP DATABASE ["+database+"]");
                }
                if(backup!=null) try {Files.deleteIfExists(Path.of(backup));} catch(java.io.IOException retained) {System.out.println("Test backup retained in SQL Server backup directory; cleanup needs its service-directory permissions.");}
                System.out.println("Disposed isolated RELEASE-01 databases.");
            }
        }
        System.out.println("SQL Server + packaged release: "+passed+" groups passed.");
    }
    private static Map<String,String> localConfiguration() throws Exception {
        Map<String,String> config=new HashMap<>();
        Path path=Path.of("../.env");
        if(Files.isRegularFile(path)) for(String line:Files.readAllLines(path)) {
            if(line.isBlank() || line.stripLeading().startsWith("#") || !line.contains("=")) continue;
            int at=line.indexOf('=');String value=line.substring(at+1).trim();
            if(value.length()>1 && ((value.startsWith("\"")&&value.endsWith("\"")) || (value.startsWith("'")&&value.endsWith("'")))) value=value.substring(1,value.length()-1);
            config.put(line.substring(0,at).trim(),value);
        }
        for(String key:List.of("DB_HOST","DB_PORT","DB_NAME","DB_USER","DB_PASSWORD")) {
            String value=System.getenv(key);if(value!=null && !value.isBlank()) config.put(key,value);
        }
        return config;
    }
    private static ProcessBuilder process(Path jar,Path directory,int port) {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-jar",jar.toString(),"--server.port="+port)
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve("app.log").toFile());
    }
    @SuppressWarnings("unchecked")
    private static void smokeJar(Path jar,String database) throws Exception {
        Path directory=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"release-smoke-");
        int port;try(java.net.ServerSocket socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        ProcessBuilder builder=process(jar,directory,port);builder.environment().putAll(localConfiguration());
        builder.environment().putAll(Map.of("DB_NAME",database,"SPRING_PROFILES_ACTIVE","dev","COOKIE_SECURE","false","DB_ENCRYPT","true","DB_TRUST_SERVER_CERTIFICATE","true","SERVER_ADDRESS","127.0.0.1"));
        Process app=builder.start();
        try {
            CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();String base="http://127.0.0.1:"+port;
            boolean ready=false;
            for(int i=0;i<100 && app.isAlive();i++) {
                try {ready=http.send(HttpRequest.newBuilder(URI.create(base+"/pages/login.html")).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()==200;if(ready)break;}
                catch(java.io.IOException ignored) {} Thread.sleep(250);
            }
            require(ready,"Release JAR did not start from empty directory; inspect target/release-smoke-*/app.log");
            Map<String,Object> csrf=JSON.readValue(http.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body(),Map.class);
            var login=http.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login")).header("Content-Type","application/json").header("X-CSRF-TOKEN",(String)csrf.get("token"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"123456\"}")).build(),HttpResponse.BodyHandlers.ofString());require(login.statusCode()==200,"Release login failed");
            for(String path:List.of("/api/rooms","/api/statistics/yearly?year=2026","/pages/statistics.html","/js/account.js","/js/api.js","/css/style.css"))
                require(http.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()==200,"Release resource/API missing: "+path);
            pass("RELEASE-01 JAR starts from clean cwd, serves frontend/APIs on one origin and authenticates against restored database");
        } finally {app.destroy();if(!app.waitFor(10,TimeUnit.SECONDS))app.destroyForcibly().waitFor();}
    }
    private static void invalidProduction(Path jar,boolean insecureTls) throws Exception {
        Path directory=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"prod-guard-");
        ProcessBuilder builder=process(jar,directory,0);builder.environment().put("SPRING_PROFILES_ACTIVE","prod");
        for(String key:List.of("DB_HOST","DB_PORT","DB_NAME","DB_USER","DB_PASSWORD","DB_ENCRYPT","DB_TRUST_SERVER_CERTIFICATE","COOKIE_SECURE")) builder.environment().remove(key);
        if(insecureTls) builder.environment().putAll(Map.of("DB_HOST","localhost","DB_PORT","1433","DB_NAME","missing","DB_USER","dummy","DB_PASSWORD","dummy","COOKIE_SECURE","true","DB_ENCRYPT","true","DB_TRUST_SERVER_CERTIFICATE","true"));
        Process app=builder.start();
        try {
            require(app.waitFor(25,TimeUnit.SECONDS),"Production guard did not fail promptly");require(app.exitValue()!=0,"Invalid production config accepted");
            String log=Files.readString(directory.resolve("app.log"));
            require(log.contains(insecureTls?"Production requires DB_ENCRYPT=true":"Production requires COOKIE_SECURE=true"),"Production validation is not explicit");
            pass(insecureTls?"RELEASE-01 production rejects untrusted SQL TLS before attempting login":"RELEASE-01 production fails clearly when mandatory environment/HTTPS configuration is missing");
        } finally {if(app.isAlive())app.destroyForcibly().waitFor();}
    }
    private static void productionIgnoresLocalFile(Path jar) throws Exception {
        Path directory=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"prod-dotenv-");
        Files.writeString(directory.resolve(".env"),"DB_HOST=localhost\nDB_PORT=1433\nDB_NAME=dummy\nDB_USER=dummy\nDB_PASSWORD=dummy\nCOOKIE_SECURE=true\nDB_ENCRYPT=true\nDB_TRUST_SERVER_CERTIFICATE=false\n");
        ProcessBuilder builder=process(jar,directory,0);
        for(String key:List.of("DB_HOST","DB_PORT","DB_NAME","DB_USER","DB_PASSWORD","DB_ENCRYPT","DB_TRUST_SERVER_CERTIFICATE")) builder.environment().remove(key);
        builder.environment().putAll(Map.of("SPRING_PROFILES_ACTIVE","prod","COOKIE_SECURE","true"));
        Process app=builder.start();
        try {
            require(app.waitFor(25,TimeUnit.SECONDS),"Production .env guard did not fail promptly");
            require(app.exitValue()!=0 && Files.readString(directory.resolve("app.log")).contains("Missing required configuration: DB_HOST"),"Production used implicit .env credentials");
            pass("RELEASE-01 production ignores credentials present only in a local .env");
        } finally {if(app.isAlive())app.destroyForcibly().waitFor();}
    }
}
