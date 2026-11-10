package com.homestay.controller;

import com.homestay.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Customer-only API. The caller cannot choose a user/customer identity. */
@RestController
@RequestMapping("/api/customer")
public class CustomerBookingController {
    private final CustomerBookingService service;
    private final Map<String,String> transferAccount;
    public CustomerBookingController(@Value("${booking.transfer-hold-minutes:15}") int holdMinutes,
            @Value("${booking.transfer-bank-name:}") String bank,@Value("${booking.transfer-account-number:}") String account,
            @Value("${booking.transfer-account-name:}") String name) {
        service=new CustomerBookingService(RoomSchedule.CLOCK,holdMinutes);
        transferAccount=bank.isBlank() || account.isBlank() || name.isBlank()?Map.of():Map.of("bankName",bank,"accountNumber",account,"accountName",name);
    }
    @GetMapping("/profile")
    public Map<String,Object> profile(@AuthenticationPrincipal AppUser user) {return service.profile(user);}
    @PutMapping("/profile")
    public Map<String,Object> saveProfile(@AuthenticationPrincipal AppUser user,@RequestBody Map<String,Object> body) {return service.saveProfile(user,body);}
    @GetMapping("/services")
    public List<Map<String,Object>> services(@AuthenticationPrincipal AppUser user) {return service.services(user);}
    @GetMapping("/availability")
    public List<Map<String,Object>> availability(@AuthenticationPrincipal AppUser user,
            @RequestParam(defaultValue="Night") String billingUnit,
            @RequestParam(required=false) String checkinDate,@RequestParam(required=false) String checkoutDate,
            @RequestParam(required=false) String checkinAt,@RequestParam(required=false) String checkoutAt,
            @RequestParam(required=false) Integer propertyId) {
        Map<String,Object> body=new HashMap<>();body.put("billingUnit",billingUnit);
        body.put("checkinDate",checkinDate);body.put("checkoutDate",checkoutDate);body.put("checkinAt",checkinAt);body.put("checkoutAt",checkoutAt);
        return service.availability(user,body,propertyId);
    }
    @PostMapping("/quotes")
    public Map<String,Object> quote(@AuthenticationPrincipal AppUser user,@RequestBody Map<String,Object> body) {return service.quote(user,body);}
    @PostMapping("/bookings")
    public Map<String,Object> create(@AuthenticationPrincipal AppUser user,@RequestBody Map<String,Object> body) {return service.create(user,body);}
    @GetMapping("/bookings")
    public List<Map<String,Object>> bookings(@AuthenticationPrincipal AppUser user) {return service.bookings(user);}
    @GetMapping("/bookings/{id}")
    public Map<String,Object> detail(@AuthenticationPrincipal AppUser user,@PathVariable int id) {return service.detail(user,id);}
    @PutMapping("/bookings/{id}/cancel")
    public Map<String,Object> cancel(@AuthenticationPrincipal AppUser user,@PathVariable int id) {return service.cancel(user,id);}
    @GetMapping("/bookings/{id}/payment")
    public Map<String,Object> payment(@AuthenticationPrincipal AppUser user,@PathVariable int id) {
        Map<String,Object> payment=service.payment(user,id);
        payment.put("transferConfigured",!transferAccount.isEmpty());
        if("Chuyển khoản".equals(payment.get("paymentMethod")) && !transferAccount.isEmpty()) payment.put("transferAccount",transferAccount);
        return payment;
    }
    @PostMapping("/bookings/{id}/payment-report")
    public Map<String,Object> report(@AuthenticationPrincipal AppUser user,@PathVariable int id,@RequestBody(required=false) Map<String,Object> body) {
        if(body!=null) CustomerOwnership.allowedFields(body,Set.of());
        return service.reportTransfer(user,id);
    }
    @GetMapping("/invoices")
    public List<Map<String,Object>> invoices(@AuthenticationPrincipal AppUser user) {return service.invoices(user);}
    @GetMapping("/invoices/{id}")
    public Map<String,Object> invoice(@AuthenticationPrincipal AppUser user,@PathVariable int id) {return service.invoice(user,id);}
}
