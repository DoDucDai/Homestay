package com.homestay.controller;

import com.homestay.db.DBConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;

/** Manual, append-only CSV demo import. No endpoints, schema changes or account edits. */
public final class DemoDataImport {
 static final ObjectMapper JSON=new ObjectMapper();
 static final String DATASET="DEMO-LH-20261029",PREFIX="["+DATASET+":";
 static final String[] TABLES={"Users","Rooms","Customers","Services","Bookings","Booking_Services","Payments"};
 static final String[] KEYS={"user_id","room_id","customer_id","service_id","booking_id","id","payment_id"};
 static Map<String,Object> input;static Map<String,List<Map<String,String>>> data;
 static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 static String quote(String s){return s.replace("'","''");}
 static String scalar(Connection c,String sql)throws Exception{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){require(r.next(),"Missing query result");return r.getString(1);}}
 static int insert(Connection c,String sql,Object...args)throws Exception{
  try(PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);require(s.executeUpdate()==1,"Write affected unexpected row count");try(ResultSet r=s.getGeneratedKeys()){require(r.next(),"No generated key");return r.getInt(1);}}
 }
 static String nullable(String s){return s==null||s.isEmpty()?null:s;}
 static BigDecimal money(String s){BigDecimal n=new BigDecimal(s);require(n.scale()==0&&n.signum()>=0&&n.compareTo(new BigDecimal("999999999999"))<=0,"Invalid VND");return n;}
 static String note(Map<String,String> b){return PREFIX+b.get("booking_code")+"] "+b.get("note");}
 static String idList(Collection<Integer> ids){return String.join(",",ids.stream().map(Object::toString).toList());}
 static Map<String,Object> baseline(Connection c)throws Exception{
  Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<TABLES.length;i++){
   int max=Integer.parseInt(scalar(c,"SELECT COALESCE(MAX("+KEYS[i]+"),0) FROM "+TABLES[i]));
   result.put(TABLES[i],Map.of("maxId",max,"count",Integer.parseInt(scalar(c,"SELECT COUNT(*) FROM "+TABLES[i])),"sha256",hash(c,TABLES[i],KEYS[i],max)));
  }return result;
 }
 static String hash(Connection c,String table,String key,int max)throws Exception{
  StringBuilder text=new StringBuilder();try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM "+table+" WHERE "+key+"<="+max+" ORDER BY "+key+" FOR JSON PATH,INCLUDE_NULL_VALUES")){while(r.next())text.append(r.getString(1));}
  return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 }
 static void unchanged(Connection c,Map<String,Object> before)throws Exception{
  for(int i=0;i<TABLES.length;i++){Map<String,Object> b=(Map<String,Object>)before.get(TABLES[i]);require(b.get("sha256").equals(hash(c,TABLES[i],KEYS[i],((Number)b.get("maxId")).intValue())),"Existing rows changed: "+TABLES[i]);}
 }
 static int find(Connection c,String table,String key,String column,String value)throws Exception{
  try(PreparedStatement s=c.prepareStatement("SELECT "+key+" FROM "+table+" WHERE "+column+"=?")){s.setString(1,value);try(ResultSet r=s.executeQuery()){if(!r.next())return 0;int id=r.getInt(1);require(!r.next(),"Ambiguous existing key: "+table);return id;}}
 }
 static Map<String,Integer> bookingIds(Connection c)throws Exception{
  Map<String,Integer> ids=new LinkedHashMap<>();for(var b:data.get("bookings")){int id=find(c,"Bookings","booking_id","note",note(b));if(id!=0)ids.put(b.get("booking_code"),id);}return ids;
 }
 static Map<String,Object> verify(Connection c)throws Exception{
  var ids=bookingIds(c);require(ids.size()==60,"Incomplete existing demo dataset; no automatic repair performed");
  Map<String,String> names=new HashMap<>();for(var s:data.get("services"))names.put(s.get("service_code"),s.get("service_name"));
  for(var b:data.get("bookings")){
   int id=ids.get(b.get("booking_code"));var customer=data.get("customers").stream().filter(r->r.get("customer_code").equals(b.get("customer_code"))).findFirst().orElseThrow();
   try(PreparedStatement s=c.prepareStatement("SELECT b.*,r.room_code,c.cccd FROM Bookings b JOIN Rooms r ON r.room_id=b.room_id JOIN Customers c ON c.customer_id=b.customer_id WHERE b.booking_id=?")){s.setInt(1,id);try(ResultSet r=s.executeQuery()){require(r.next(),"Missing demo booking");require(r.getString("room_code").equals("DEMO-"+b.get("room_code"))&&r.getString("cccd").equals(customer.get("document_number"))&&r.getString("status").equals(b.get("status"))&&r.getDate("checkin_date").toString().equals(b.get("checkin_date"))&&r.getDate("checkout_date").toString().equals(b.get("checkout_date"))&&r.getBigDecimal("price_per_night").compareTo(money(b.get("price_per_night")))==0,"Existing demo booking differs: "+b.get("booking_code"));}}
   var expected=data.get("booking_services").stream().filter(r->r.get("booking_code").equals(b.get("booking_code"))).toList();
   require(Integer.parseInt(scalar(c,"SELECT COUNT(*) FROM Booking_Services WHERE booking_id="+id))==expected.size(),"Demo service line count changed");
   for(var line:expected)try(PreparedStatement s=c.prepareStatement("SELECT quantity,unit_price_snapshot,total_price FROM Booking_Services WHERE booking_id=? AND service_name_snapshot=?")){s.setInt(1,id);s.setString(2,names.get(line.get("service_code")));try(ResultSet r=s.executeQuery()){require(r.next(),"Missing captured service");BigDecimal price=money(line.get("unit_price_at_booking"));int qty=Integer.parseInt(line.get("quantity"));require(r.getInt(1)==qty&&r.getBigDecimal(2).compareTo(price)==0&&r.getBigDecimal(3).compareTo(price.multiply(BigDecimal.valueOf(qty)))==0&&!r.next(),"Captured service differs");}}
   var paid=data.get("payments").stream().filter(r->r.get("booking_code").equals(b.get("booking_code"))).findFirst();
   require(Integer.parseInt(scalar(c,"SELECT COUNT(*) FROM Payments WHERE booking_id="+id))==(paid.isPresent()?1:0),"Payment lifecycle differs");
   if(paid.isPresent())try(PreparedStatement s=c.prepareStatement("SELECT * FROM Payments WHERE booking_id=?")){s.setInt(1,id);try(ResultSet r=s.executeQuery()){require(r.next(),"Missing payment");var p=paid.get();for(String field:List.of("room_total","service_total","grand_total"))require(r.getBigDecimal(field).compareTo(money(p.get(field)))==0,"Payment total differs");require(r.getString("payment_status").equals("Đã thanh toán")&&r.getString("payment_method").equals(p.get("payment_method"))&&r.getTimestamp("paid_at").equals(Timestamp.valueOf(p.get("paid_at"))),"Payment metadata differs");var snapshot=JSON.readValue(r.getString("invoice_snapshot"),Map.class);require(new BigDecimal(snapshot.get("grandTotal").toString()).compareTo(money(p.get("grand_total")))==0&&new BigDecimal(snapshot.get("roomTotal").toString()).compareTo(money(p.get("room_total")))==0&&new BigDecimal(snapshot.get("serviceTotal").toString()).compareTo(money(p.get("service_total")))==0&&snapshot.get("roomCode").equals("DEMO-"+b.get("room_code"))&&snapshot.get("customerName").equals(customer.get("full_name"))&&((Number)snapshot.get("bookingId")).intValue()==id&&((Number)snapshot.get("paymentId")).intValue()==r.getInt("payment_id")&&((List<?>)snapshot.get("services")).size()==expected.size(),"Frozen invoice differs");}}
  }
  var monthly=new LinkedHashMap<String,Object>();try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CONVERT(char(7),paid_at,126),SUM(grand_total) FROM Payments WHERE booking_id IN("+idList(ids.values())+") AND payment_status=N'Đã thanh toán' GROUP BY CONVERT(char(7),paid_at,126) ORDER BY 1")){while(r.next())monthly.put(r.getString(1),r.getBigDecimal(2).longValueExact());}
  var expectedMonths=(Map<String,Number>)input.get("monthlyTotals");require(monthly.size()==12,"Expected twelve revenue months");for(var entry:expectedMonths.entrySet())require(((Number)monthly.get(entry.getKey())).longValue()==entry.getValue().longValue(),"Monthly revenue mismatch");
  return Map.of("dataset",DATASET,"bookingIds",ids,"payments",40,"serviceLines",52,"monthlyTotals",monthly,"totalRevenue",input.get("totalRevenue"));
 }
 static Map<String,Object> apply(Connection c,boolean failAfterLines)throws Exception{
  var before=baseline(c);c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);c.setAutoCommit(false);
  try{
   // JDBC uses implicit transactions: a table read starts it before the transaction-owned app lock.
   scalar(c,"SELECT COUNT_BIG(*) FROM dbo.Bookings WITH (UPDLOCK,HOLDLOCK)");
   try(Statement s=c.createStatement()){s.execute("DECLARE @r int; EXEC @r=sp_getapplock @Resource=N'Homestay:DEMO-LH-20261029',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000; IF @r<0 THROW 51000,'Cannot lock demo import',1;");}
   if(!bookingIds(c).isEmpty()){var result=new LinkedHashMap<>(verify(c));unchanged(c,before);c.rollback();result.put("alreadyImported",true);return result;}
   Map<String,Integer> customers=new HashMap<>(),services=new HashMap<>(),rooms=new HashMap<>(),bookings=new LinkedHashMap<>();Map<String,String> names=new HashMap<>();
   for(var r:data.get("customers")){
    int id=find(c,"Customers","customer_id","cccd",r.get("document_number"));
    if(id==0)id=insert(c,"INSERT Customers(full_name,document_type,cccd,phone,email,address) VALUES(?,?,?,?,?,?)",r.get("full_name"),r.get("document_type"),r.get("document_number"),r.get("phone"),nullable(r.get("email")),nullable(r.get("address")));
    else try(Statement s=c.createStatement();ResultSet row=s.executeQuery("SELECT full_name,document_type,phone,email,address FROM Customers WHERE customer_id="+id)){require(row.next(),"Missing existing customer");for(String col:List.of("full_name","document_type","phone","email","address"))require(Objects.equals(nullable(row.getString(col)),nullable(r.get(col))),"Existing customer document collision; aborting import");}
    customers.put(r.get("customer_code"),id);
   }
   for(var r:data.get("services")){
    int id=find(c,"Services","service_id","service_name",r.get("service_name"));
    if(id==0)id=insert(c,"INSERT Services(service_name,unit_price,status) VALUES(?,?,?)",r.get("service_name"),money(r.get("unit_price")),r.get("status"));
    else if(!scalar(c,"SELECT status FROM Services WHERE service_id="+id).equals(r.get("status"))){
     // A stopped operational service must stay stopped. Give the demo its own catalog entry.
     String scoped="DEMO-LH "+r.get("service_code")+" - "+r.get("service_name");
     require(find(c,"Services","service_id","service_name",scoped)==0,"Scoped demo service already exists without a complete import");
     id=insert(c,"INSERT Services(service_name,unit_price,status) VALUES(?,?,?)",scoped,money(r.get("unit_price")),r.get("status"));
    }
    services.put(r.get("service_code"),id);names.put(r.get("service_code"),r.get("service_name"));
   }
   for(var r:data.get("rooms")){
    String code="DEMO-"+r.get("room_code");require(find(c,"Rooms","room_id","room_code",code)==0,"Demo room code already exists without complete dataset");
    rooms.put(r.get("room_code"),insert(c,"INSERT Rooms(room_code,room_name,room_type,price,status,description) VALUES(?,?,?,?,?,?)",code,r.get("room_name"),r.get("room_type"),money(r.get("price")),r.get("status"),r.get("description")));
   }
   for(var r:data.get("bookings"))bookings.put(r.get("booking_code"),insert(c,"INSERT Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night,created_at,note) VALUES(?,?,?,?,?,?,?,?)",customers.get(r.get("customer_code")),rooms.get(r.get("room_code")),java.sql.Date.valueOf(r.get("checkin_date")),java.sql.Date.valueOf(r.get("checkout_date")),r.get("status"),money(r.get("price_per_night")),Timestamp.valueOf(r.get("created_at")),note(r)));
   for(var r:data.get("booking_services")){int qty=Integer.parseInt(r.get("quantity"));BigDecimal price=money(r.get("unit_price_at_booking"));insert(c,"INSERT Booking_Services(booking_id,service_id,quantity,service_name_snapshot,unit_price_snapshot,total_price) VALUES(?,?,?,?,?,?)",bookings.get(r.get("booking_code")),services.get(r.get("service_code")),qty,names.get(r.get("service_code")),price,price.multiply(BigDecimal.valueOf(qty)));}
   if(failAfterLines)throw new SQLException("Injected failure before payment writes");
   for(var p:data.get("payments")){
    var invoice=BookingInvoiceSnapshot.build(c,bookings.get(p.get("booking_code")));
    for(var pair:Map.of("roomTotal","room_total","serviceTotal","service_total","grandTotal","grand_total").entrySet())require(((BigDecimal)invoice.get(pair.getKey())).compareTo(money(p.get(pair.getValue())))==0,"Source payment differs from captured booking/services");
    BookingInvoiceSnapshot.store(c,invoice,p.get("payment_method"),Timestamp.valueOf(p.get("paid_at")).toLocalDateTime());
   }
   for(int id:rooms.values())RoomSchedule.refresh(c,id,LocalDate.parse(((Map<String,String>)input.get("manifest")).get("cutoff")));
   var result=new LinkedHashMap<>(verify(c));unchanged(c,before);c.commit();result.put("alreadyImported",false);result.put("existingRowsUnchanged",true);result.put("before",before);result.put("after",baseline(c));return result;
  }catch(Exception error){c.rollback();throw error;}finally{c.setAutoCommit(true);}
 }
 static String backup(Connection c)throws Exception{
  String folder=scalar(c,"SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))");if(folder==null)folder=scalar(c,"SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultDataPath'))");require(folder!=null,"No SQL backup directory");
  String db=c.getCatalog(),path=folder+(folder.endsWith("\\")||folder.endsWith("/")?"":"\\")+db+"_before_"+DATASET+"_"+UUID.randomUUID().toString().replace("-","")+".bak";
  require(db.matches("[A-Za-z0-9_]+"),"Unexpected database name");try(Statement s=c.createStatement()){s.execute("BACKUP DATABASE ["+db+"] TO DISK=N'"+quote(path)+"' WITH COPY_ONLY,CHECKSUM");s.execute("RESTORE VERIFYONLY FROM DISK=N'"+quote(path)+"' WITH CHECKSUM");}return path;
 }
 static void batches(Statement s,String sql)throws Exception{for(String b:sql.split("(?im)^\\s*GO\\s*$"))if(!b.isBlank())s.execute(b);}
 static List<List<Map<String,String>>> resultSets(Statement s,String sql)throws Exception{
  var sets=new ArrayList<List<Map<String,String>>>();boolean found=s.execute(sql);
  while(true){if(found)try(ResultSet rs=s.getResultSet()){var rows=new ArrayList<Map<String,String>>();while(rs.next()){var row=new LinkedHashMap<String,String>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.put(rs.getMetaData().getColumnLabel(i),rs.getString(i));rows.add(row);}sets.add(rows);}else if(s.getUpdateCount()==-1)break;found=s.getMoreResults();}return sets;
 }
 static void integration(Connection c,Path evidence)throws Exception{
  String original=c.getCatalog(),db="HomestayDemoImportTest_"+UUID.randomUUID().toString().replace("-","");boolean created=false;
  try(Statement s=c.createStatement()){
   try{
    s.execute("CREATE DATABASE ["+db+"]");created=true;s.execute("USE ["+db+"]");String schema=Files.readString(Path.of("../database/schema.sql"));batches(s,schema.substring(schema.indexOf("CREATE TABLE Users")));batches(s,Files.readString(Path.of("../database/seed_data.sql")).replace("USE HomestayDB;","USE ["+db+"];"));
    s.executeUpdate("UPDATE Services SET status=N'Ngừng' WHERE service_name=N'Giặt ủi'");
    var before=baseline(c);try{apply(c,true);throw new AssertionError("Injected failure was not observed");}catch(SQLException expected){if(!expected.getMessage().contains("Injected failure"))throw expected;}unchanged(c,before);require(bookingIds(c).isEmpty(),"Rollback left demo bookings");System.out.println("PASS rollback removes every imported row after a late failure and preserves all original rows");
    var imported=apply(c,false);require(Boolean.FALSE.equals(imported.get("alreadyImported")),"Initial import skipped");System.out.println("PASS complete import reconciles 60 bookings, 52 service lines, 40 frozen invoices and all 12 revenue months");
    var after=baseline(c);var repeated=apply(c,false);require(Boolean.TRUE.equals(repeated.get("alreadyImported")),"Repeat import did not detect existing data");require(after.equals(baseline(c)),"Repeat changed rows or invoices");System.out.println("PASS repeat import verifies existing data without duplicates or snapshot rewrites");
    var p=data.get("payments").get(0);String saved=p.get("grand_total");p.put("grand_total",Long.toString(Long.parseLong(saved)+1));boolean rejected=false;try{apply(c,false);}catch(IllegalStateException expected){rejected=true;}finally{p.put("grand_total",saved);}require(rejected&&after.equals(baseline(c)),"Changed input was not rejected safely");System.out.println("PASS changed payment input is rejected without modifying an existing import");
    Path checks=Path.of(System.getProperty("demo.checks","../contributions/long/queries_check.sql"));
    String queries=Files.readString(checks);var clean=resultSets(s,queries);require(clean.size()==9,"Expected nine read-only result sets");
    Set<String> demoIds=new HashSet<>(bookingIds(c).values().stream().map(Object::toString).toList());
    for(int index:new int[]{1,2,3,7,8})for(var row:clean.get(index))require(!demoIds.contains(row.get("booking_id"))&&!demoIds.contains(row.get("booking_a"))&&!demoIds.contains(row.get("booking_b")),"New demo data failed SQL check "+index);
    System.out.println("PASS corrected SELECT checks accept every new booking/payment, while retaining any pre-existing seed diagnostics");
    s.executeUpdate("UPDATE Rooms SET price=price+100000 WHERE room_code LIKE 'DEMO-%'");
    require(clean.get(3).equals(resultSets(s,queries).get(3)),"Reconciliation incorrectly follows current room catalog price");
    System.out.println("PASS changing current demo room prices does not change captured-price invoice reconciliation");
    int cancelled=bookingIds(c).get("BK043");insert(c,"INSERT Payments(booking_id,room_total,service_total,grand_total,payment_method,payment_status,paid_at) VALUES(?,100,0,100,N'Tiền mặt',N'Chưa thanh toán','2026-10-10')",cancelled);
    var unpaid=resultSets(s,queries);for(int index:new int[]{4,5,6})require(clean.get(index).equals(unpaid.get(index)),"Unpaid fixture contaminated revenue");
    require(unpaid.get(7).stream().anyMatch(row->row.get("booking_id").equals(Integer.toString(cancelled))),"Lifecycle diagnostic missed unpaid fixture");
    System.out.println("PASS unpaid fixtures are excluded from month/year/method revenue and reported by lifecycle checks");
    Files.writeString(evidence.resolve("integration-receipt.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(imported));
   }finally{c.setAutoCommit(true);s.execute("USE ["+original+"]");if(created){s.execute("ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");s.execute("DROP DATABASE ["+db+"]");System.out.println("Disposed isolated demo-import test database.");}}
  }
 }
 public static void main(String[] args)throws Exception{
  require(args.length==4,"Usage: DemoDataImport preview|apply|test validated-data.json expected-database evidence-directory");
  input=JSON.readValue(Files.readString(Path.of(args[1])),Map.class);data=(Map<String,List<Map<String,String>>>)input.get("data");
  require(((Map<String,Object>)input.get("manifest")).get("datasetId").equals(DATASET),"Unexpected dataset");
  require(data.get("customers").size()==50&&data.get("services").size()==12&&data.get("rooms").size()==20&&data.get("bookings").size()==60&&data.get("booking_services").size()==52&&data.get("payments").size()==40,"Unexpected data counts");
  Path evidence=Path.of(args[3]);Files.createDirectories(evidence);
  try(Connection c=DBConnection.getConnection()){
   require(c.getCatalog().equals(args[2]),"Configured database differs from explicit expected database; no writes performed");
   if(args[0].equals("test")){integration(c,evidence);return;}
   require(args[0].equals("preview")||args[0].equals("apply"),"Unknown operation");
   var result=new LinkedHashMap<String,Object>();result.put("database",c.getCatalog());result.put("before",baseline(c));result.put("dataset",DATASET);result.put("expectedRevenue",input.get("totalRevenue"));
   if(args[0].equals("apply")){
    require(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString().equals(((Map<?,?>)input.get("manifest")).get("cutoff")),"Dataset cutoff is no longer today; review active stays before importing");
    String backup=backup(c);Files.writeString(evidence.resolve("verified-backup.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("database",c.getCatalog(),"backup",backup,"checksumVerified",true)));
    result.put("backup",backup);result.put("import",apply(c,false));
   }else if(!bookingIds(c).isEmpty())result.put("existingImport",verify(c));
   Files.writeString(evidence.resolve(args[0]+"-receipt.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
   System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
  }
 }
}
