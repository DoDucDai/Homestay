package com.homestay.security;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;

@Component
public class LoginThrottle {
    private record Bucket(long start,int count) {}
    private final Map<String,Bucket> buckets=new HashMap<>();
    private final Clock clock;
    private static final long WINDOW=15*60*1000L;
    public LoginThrottle() { this(Clock.systemUTC()); }
    LoginThrottle(Clock clock) { this.clock=clock; }
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
    public synchronized void succeeded(String username) { buckets.remove("account:"+username.toLowerCase(Locale.ROOT)); }
    private boolean limited(String key,int limit,long now) {
        Bucket bucket=buckets.get(key);
        return bucket!=null && now-bucket.start()<WINDOW && bucket.count()>=limit;
    }
    private void count(String key,long now) {
        Bucket old=buckets.get(key);
        buckets.put(key,old==null ? new Bucket(now,1) : new Bucket(old.start(),old.count()+1));
    }
}
