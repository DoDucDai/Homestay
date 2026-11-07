// NOTE KIỂM THỬ: Kiểm tra hộp chi tiết khách hiển thị đầy đủ giá trị gốc, chỉ ra dữ liệu thiếu và không dựng HTML từ dữ liệu khách.
// Code chạy bằng Node trong môi trường giả để kiểm tra frontend; các assert so kết quả thực tế với kết quả mong đợi.
const assert=require('node:assert/strict');
const {env,payload}=require('./frontend-test-env.cjs');
const e=env('customers');
const customer={customerId:9,fullName:payload,documentType:'CCCD',cccd:'012345678901',phone:'0901234567',email:'long-address@example.com',address:payload.repeat(5),validationWarnings:[]};
let opened;
e.context.openModal=id=>{opened=id;};
e.context.renderTable([customer]);
const row=e.el('customers-tbody').children[0];
assert.equal(row.children.length,6);
assert.equal(row.children[1].getAttribute('data-label'),'Họ tên / Địa chỉ');
const name=row.children[1].children[0].children[0];
name.events.click();
assert.equal(opened,'customer-details-modal');
assert.equal(e.el('customer-details-title').textContent,'Hồ sơ KH9');
const fields=e.el('customer-details-fields');
assert.equal(fields.children.length,6);
assert.equal(fields.children[0].children[1].textContent,payload);
assert.equal(fields.children[5].children[1].textContent,customer.address);
assert.equal(e.context.pwned,undefined);
console.log('PASS compact customer row opens complete, unmodified details using safe text rendering');
e.context.openCustomerDetails({...customer,email:null,address:null});
assert.equal(fields.children[4].children[1].textContent,'Chưa cập nhật');
assert.equal(fields.children[5].children[1].textContent,'Chưa cập nhật');
console.log('PASS missing customer contact fields are explicitly identified in the details dialog');
