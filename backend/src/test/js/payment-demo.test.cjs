const assert=require('node:assert/strict');
const {env}=require('./frontend-test-env.cjs');

const preview={success:true,bookingId:42,customerName:'Khách Demo',roomCode:'DEMO',roomName:'Phòng demo',
    checkinDate:'2026-11-10',checkoutDate:'2026-11-11',nights:1,pricePerNight:300000,
    roomTotal:300000,serviceTotal:0,grandTotal:300000,services:[]};

// Test the reusable display directly: checkout now reads an already-paid invoice,
// so selecting a payment method during legacy checkout is no longer the current flow.
const e=env('rooms'),host=e.el('stay-payment-demo');
e.context.renderDemoPayment(host,preview,'Tiền mặt');
assert.equal(host.hidden,false);
assert.ok(host.textContent.includes('Thanh toán tiền mặt'));
assert.equal(host.querySelector('img'),null);
assert.equal(e.writes.length,0);

e.context.renderDemoPayment(host,preview,'Chuyển khoản');
const qr=host.querySelector('img');
assert.match(qr.src,/^data:image\/gif;base64,/);
for(const expected of ['DEMO BANK','0000123456789','NGUYEN AN DEMO','HOMESTAY BK42'])assert.ok(host.textContent.includes(expected));
const payload=e.context.demoPaymentPayload(preview);
assert.equal(payload.demo,true);assert.equal(payload.amount,300000);assert.equal(payload.reference,'HOMESTAY BK42');
assert.equal(e.writes.length,0,'Rendering a QR must never record a payment');

const refreshed={...preview,serviceTotal:20000,grandTotal:320000};
e.context.renderDemoPayment(host,refreshed,'Chuyển khoản');
assert.notEqual(host.querySelector('img').src,qr.src,'A changed amount must refresh the QR');
assert.equal(e.context.demoPaymentPayload(refreshed).amount,320000);
e.context.renderDemoPayment(host,refreshed,'Tiền mặt');
assert.equal(host.querySelector('img'),null);
for(const invalid of [{...preview,grandTotal:NaN},{...preview,grandTotal:1.5},{...preview,bookingId:0}]) {
    assert.equal(e.context.demoPaymentPayload(invalid),null);
    e.context.renderDemoPayment(host,invalid,'Chuyển khoản');
    assert.equal(host.hidden,true);assert.equal(host.children.length,0);
}
e.context.renderDemoPayment(host,preview,'Chuyển khoản');
e.context.renderDemoPayment(host,null,'Chuyển khoản');
assert.equal(host.hidden,true);assert.equal(host.children.length,0);
assert.equal(e.writes.length,0);
console.log('PASS demo payment display: cash/QR switching, fictional account, refreshed amount, invalid/stale QR removal and no automatic writes');
