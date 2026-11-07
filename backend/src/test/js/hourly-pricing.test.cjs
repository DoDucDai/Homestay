// NOTE KIỂM THỬ: Kiểm tra báo giá gói giờ ở frontend, các mốc đổi gói, dữ liệu giá sai và đối chiếu tổng hóa đơn.
// Code chạy bằng Node trong môi trường giả để kiểm tra frontend; các assert so kết quả thực tế với kết quả mong đợi.
const {env}=require('./frontend-test-env.cjs');
const assert=require('node:assert/strict');
const e=env('checkout'),c=e.context,p={twoHours:250000,fourHours:350000,eightHours:550000};
[250000,250000,330000,350000,430000,510000,550000,550000,630000].forEach((total,index)=>assert.equal(c.quoteHourly(80000,p,index+1).total,total));
assert.equal(c.quoteHourly(70000,null,3).total,210000);
assert.throws(()=>c.quoteHourly(80000,{twoHours:1},3));
assert.throws(()=>c.quoteHourly(999999999999,null,2));
const invoice={bookingId:1,billingUnit:'Hour',checkinAt:'2031-01-15T10:00',checkoutAt:'2031-01-15T13:00',billedHours:3,pricePerHour:80000,hourlyPackages:p,pricePerNight:0,nights:0,roomTotal:330000,serviceTotal:0,grandTotal:330000,services:[]};
assert.equal(c.isValidCheckoutPreview(invoice,1),true);
assert.equal(c.isValidCheckoutPreview({...invoice,roomTotal:240000,grandTotal:240000},1),false);
c.renderInvoice(invoice);assert.ok(e.el('inv-price-per-night').textContent.includes('Gói 2 giờ'));assert.ok(e.el('inv-price-per-night').textContent.includes('1 giờ lẻ'));
const stay=env('rooms');assert.equal(stay.context.validStayInvoice(invoice,1),true);assert.equal(stay.context.validStayInvoice({...invoice,hourlyPackages:null},1),false);
console.log('PASS package boundaries, legacy rates, malformed prices, total limits, package invoice validation and charge breakdown');
