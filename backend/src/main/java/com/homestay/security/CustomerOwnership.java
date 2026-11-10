package com.homestay.security;

import com.homestay.api.*;
import java.sql.*;
import java.util.*;

/** All customer ownership comes from the authenticated user, never request IDs. */
public final class CustomerOwnership {
    private CustomerOwnership() {}

    public static AppUser requireCustomer(AppUser user) {
        if(user==null) throw new ApiProblem(401,"UNAUTHENTICATED","Vui lòng đăng nhập!");
        if(!"customer".equals(user.role())) throw new ApiProblem(403,"FORBIDDEN","Chức năng dành cho tài khoản khách hàng.");
        return user;
    }

    public static void allowedFields(Map<String,Object> body,Set<String> allowed) {
        if(body==null) throw ApiErrors.invalid("Thiếu nội dung yêu cầu!");
        if(!allowed.containsAll(body.keySet())) throw ApiErrors.invalid("Yêu cầu chứa trường không được phép.");
    }

    public static Map<String,Object> profile(Connection connection,AppUser user,boolean lock) throws SQLException {
        requireCustomer(user);
        String sql="SELECT user_id,full_name,phone,email,customer_id FROM Users "+(lock?"WITH(UPDLOCK,HOLDLOCK) ":"")
            +"WHERE user_id=? AND role='customer' AND auth_version=?";
        try(var statement=connection.prepareStatement(sql)) {
            statement.setInt(1,user.userId());statement.setInt(2,user.authVersion());
            try(var rows=statement.executeQuery()) {
                if(!rows.next()) throw new ApiProblem(401,"UNAUTHENTICATED","Phiên đăng nhập không còn hợp lệ.");
                Map<String,Object> profile=new LinkedHashMap<>();
                profile.put("fullName",rows.getString("full_name"));profile.put("phone",rows.getString("phone"));
                profile.put("email",rows.getString("email"));profile.put("customerId",rows.getObject("customer_id"));
                return profile;
            }
        }
    }

    public static Integer customerId(Connection connection,AppUser user) throws SQLException {
        return (Integer)profile(connection,user,false).get("customerId");
    }

    public static int requireProfile(Connection connection,AppUser user) throws SQLException {
        Integer id=customerId(connection,user);
        if(id==null) throw new ApiProblem(409,"CUSTOMER_PROFILE_REQUIRED","Vui lòng hoàn thiện hồ sơ người đặt phòng.");
        return id;
    }

    public static Map<String,Object> saveProfile(Connection connection,AppUser user,Map<String,Object> body) throws SQLException {
        allowedFields(body,Set.of("fullName","phone","documentType","documentNumber","address"));
        Map<String,Object> account=profile(connection,user,true);
        String name=Input.text(body,"fullName",100,false,(String)account.get("fullName"));
        if(name==null || name.isBlank()) throw ApiErrors.invalid("Vui lòng nhập họ tên.");
        String phone=Input.text(body,"phone",10,false,(String)account.get("phone"));
        if(phone==null) throw ApiErrors.invalid("Vui lòng nhập số điện thoại.");
        Input.phone(phone);
        String type=Input.oneOf(Input.text(body,"documentType",12,true,null),Set.of("CCCD","PASSPORT"),"Loại giấy tờ");
        String number=Input.text(body,"documentNumber",20,true,null);Input.document(type,number);
        String address=Input.text(body,"address",200,false,"");
        Integer id=(Integer)account.get("customerId");
        // A matching document is not proof of ownership. Never adopt an existing profile.
        try(var statement=connection.prepareStatement("SELECT customer_id FROM Customers WITH(UPDLOCK,HOLDLOCK) WHERE cccd=?")) {
            statement.setString(1,number);
            try(var rows=statement.executeQuery()) {
                if(rows.next() && !Objects.equals(id,rows.getInt(1)))
                    throw new ApiProblem(409,"DOCUMENT_IN_USE","Giấy tờ đã có hồ sơ. Vui lòng liên hệ quản trị viên để kiểm tra.");
            }
        }
        if(id==null) {
            try(var statement=connection.prepareStatement("INSERT Customers(full_name,phone,email,document_type,cccd,address) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1,name);statement.setString(2,phone);statement.setString(3,(String)account.get("email"));
                statement.setString(4,type);statement.setString(5,number);statement.setString(6,address);statement.executeUpdate();
                try(var keys=statement.getGeneratedKeys()) {if(!keys.next()) throw new SQLException("Missing customer key");id=keys.getInt(1);}
            }
        } else {
            try(var statement=connection.prepareStatement("UPDATE Customers SET full_name=?,phone=?,email=?,document_type=?,cccd=?,address=? WHERE customer_id=?")) {
                statement.setString(1,name);statement.setString(2,phone);statement.setString(3,(String)account.get("email"));
                statement.setString(4,type);statement.setString(5,number);statement.setString(6,address);statement.setInt(7,id);
                if(statement.executeUpdate()!=1) throw new ApiProblem(409,"DATA_INCONSISTENT","Hồ sơ tài khoản cần được quản trị viên kiểm tra.");
            }
        }
        try(var statement=connection.prepareStatement("UPDATE Users SET full_name=?,phone=?,customer_id=? WHERE user_id=? AND role='customer' AND auth_version=?")) {
            statement.setString(1,name);statement.setString(2,phone);statement.setInt(3,id);statement.setInt(4,user.userId());statement.setInt(5,user.authVersion());
            if(statement.executeUpdate()!=1) throw new ApiProblem(401,"UNAUTHENTICATED","Phiên đăng nhập không còn hợp lệ.");
        }
        return Map.of("success",true,"customerId",id,"message","Đã cập nhật hồ sơ người đặt phòng.");
    }

    /** Explicit administrator operation. An existing link cannot be silently reassigned. */
    public static void linkExisting(Connection connection,int userId,int customerId) throws SQLException {
        Input.id(userId);Input.id(customerId);
        try(var statement=connection.prepareStatement("SELECT customer_id FROM Users WITH(UPDLOCK,HOLDLOCK) WHERE user_id=? AND role='customer'")) {
            statement.setInt(1,userId);
            try(var rows=statement.executeQuery()) {
                if(!rows.next()) throw ApiErrors.notFound("Không tìm thấy tài khoản khách hàng.");
                Integer current=(Integer)rows.getObject(1);
                if(current!=null && current!=customerId) throw new ApiProblem(409,"ALREADY_LINKED","Tài khoản đã được liên kết với một hồ sơ khác.");
            }
        }
        try(var statement=connection.prepareStatement("SELECT customer_id FROM Customers WITH(UPDLOCK,HOLDLOCK) WHERE customer_id=?")) {
            statement.setInt(1,customerId);
            try(var rows=statement.executeQuery()) {if(!rows.next()) throw ApiErrors.notFound("Không tìm thấy hồ sơ khách hàng.");}
        }
        try(var statement=connection.prepareStatement("UPDATE Users SET customer_id=? WHERE user_id=?")) {
            statement.setInt(1,customerId);statement.setInt(2,userId);statement.executeUpdate();
        }
    }
}
