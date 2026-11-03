package com.homestay.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class NightlyPricingChecks {
    public static void main(String[] args) throws Exception {
        var p=NightlyPricing.validate(450000,List.of(6,7));
        String[] starts={"2026-11-06","2026-11-07","2026-11-08","2026-11-06","2026-11-02","2026-11-01","2026-11-09"};
        String[] ends={"2026-11-07","2026-11-08","2026-11-09","2026-11-09","2026-11-09","2026-11-09","2026-11-16"};
        long[] expected={400000,450000,450000,1300000,2900000,3350000,2900000};
        for(int i=0;i<expected.length;i++)if(NightlyPricing.quote(BigDecimal.valueOf(400000),p,LocalDate.parse(starts[i]),LocalDate.parse(ends[i])).total().longValueExact()!=expected[i])throw new AssertionError("Nightly range "+i);
        if(NightlyPricing.quote(BigDecimal.valueOf(350000),null,LocalDate.parse("2026-11-06"),LocalDate.parse("2026-11-09")).total().longValueExact()!=1050000)throw new AssertionError("Legacy rate");
        if(!p.equals(NightlyPricing.decode(NightlyPricing.encode(p))))throw new AssertionError("Snapshot roundtrip");
        try{NightlyPricing.validate(450000,List.of(6,6));throw new AssertionError("Duplicate day accepted");}catch(com.homestay.api.ApiProblem expectedError){}
        System.out.println("PASS Friday weekday, Sat/Sun weekends, departure exclusion, full weeks/remainders, legacy and captured tariffs");
    }
}
