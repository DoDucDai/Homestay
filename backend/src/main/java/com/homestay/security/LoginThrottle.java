package com.homestay.security;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;

@Component
// NOTE BACKEND: Giới hạn thử đăng nhập trong bộ nhớ theo IP và tên tài khoản trong cửa sổ 15 phút.
public class LoginThrottle {
    private record Bucket(long start,int count) {}
    private final Map<String,Bucket> buckets=new HashMap<>();
    private final Clock clock;
    private static final long WINDOW=15*60*1000L;
    public LoginThrottle() { this(Clock.systemUTC()); }
    LoginThrottle(Clock clock) { this.clock=clock; }
    // NOTE BACKEND: Dọn bộ đếm hết hạn, chặn IP từ 30 lần hoặc tài khoản từ 5 lần, tăng bộ đếm nếu còn được thử; synchronized tránh đua dữ liệu.
    public synchronized boolean allow(String address,String username) {
        long now=clock.millis();
        buckets.entrySet().removeIf(entry -> now-entry.getValue().start()>=WINDOW);
        if (buckets.size()>10000) return false;
        String ip="ip:"+address;
        String account="account:"+username.toLowerCase(Locale.ROOT);
        if (limited(ip,30,now) || limited(account,5,now)) return false;
        count(ip,now); count(account,now);
        return true;
    }
    // NOTE BACKEND: Xóa bộ đếm riêng của tài khoản sau khi xác thực thành công; không xóa bộ đếm theo IP.
    public synchronized void succeeded(String username) { buckets.remove("account:"+username.toLowerCase(Locale.ROOT)); }
    // NOTE BACKEND: Kiểm tra một bộ đếm còn trong cửa sổ thời gian và đã chạm hạn mức hay chưa.
    private boolean limited(String key,int limit,long now) {
        Bucket bucket=buckets.get(key);
        return bucket!=null && now-bucket.start()<WINDOW && bucket.count()>=limit;
    }
    // NOTE BACKEND: Tạo bộ đếm mới hoặc tăng số lần thử trong cửa sổ hiện tại.
    private void count(String key,long now) {
        Bucket old=buckets.get(key);
        buckets.put(key,old==null ? new Bucket(now,1) : new Bucket(old.start(),old.count()+1));
    }
}
