// NOTE KIỂM THỬ: Kiểm tra báo giá đêm có ngày cuối tuần, loại ngày trả, tuần đầy đủ và đối chiếu số học hóa đơn.
// Code chạy bằng Node trong môi trường giả để kiểm tra frontend; các assert so kết quả thực tế với kết quả mong đợi.
const {env}=require('./frontend-test-env.cjs');
const assert=require('node:assert/strict');
const e=env('checkout'),c=e.context,p={weekendPrice:450000,weekendDays:[6,7]};
const cases=[['2026-11-06','2026-11-07',400000],['2026-11-07','2026-11-08',450000],['2026-11-08','2026-11-09',450000],['2026-11-06','2026-11-09',1300000],['2026-11-02','2026-11-09',2900000],['2026-11-01','2026-11-09',3350000]];
for(const [a,z,total] of cases)assert.equal(c.quoteNightly(400000,p,a,z).total,total);
assert.equal(c.quoteNightly(350000,null,'2026-11-06','2026-11-09').total,1050000);
assert.throws(()=>c.quoteNightly(400000,{weekendPrice:450000,weekendDays:[6,6]},'2026-11-06','2026-11-09'));
const invoice={bookingId:1,billingUnit:'Night',checkinDate:'2026-11-06',checkoutDate:'2026-11-09',nights:3,pricePerNight:400000,nightlyPricing:p,roomTotal:1300000,serviceTotal:0,grandTotal:1300000,services:[]};
assert.equal(c.isValidCheckoutPreview(invoice,1),true);assert.equal(c.isValidCheckoutPreview({...invoice,roomTotal:1200000,grandTotal:1200000},1),false);
c.renderInvoice(invoice);assert.ok(e.el('inv-price-per-night').textContent.includes('2 đêm cuối tuần'));assert.equal(env('rooms').context.validStayInvoice(invoice,1),true);
console.log('PASS Sat/Sun rates, departure exclusion, mixed nights, full weeks/remainders, legacy and invoice arithmetic');
