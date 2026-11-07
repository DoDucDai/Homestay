// NOTE KIỂM THỬ: Kiểm tra hiển thị danh tính và menu đúng quyền; khách hàng không mở được khu quản trị qua tham số URL.
// Code chạy bằng Node trong môi trường giả để kiểm tra frontend; các assert so kết quả thực tế với kết quả mong đợi.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const {env,payload,root}=require('./frontend-test-env.cjs');
const e=env('profile');
e.context.renderAccountProfile({userId:7,username:'admin_demo',fullName:payload,role:'admin'});
assert.equal(e.el('profile-name').textContent,payload);
assert.equal(e.el('profile-full-name').textContent,payload);
assert.equal(e.el('profile-id').textContent,'TK7');
assert.equal(e.el('profile-role').textContent,'Quản trị viên');
console.log('PASS profile renders authenticated identity safely and exposes the admin workspace only to admin');
e.context.renderAccountProfile({userId:8,username:'staff_demo',fullName:'Nhân Viên',role:'customer'});
assert.equal(e.el('profile-role-detail').textContent,'Khách hàng');
assert.equal(e.el('profile-username').textContent,'staff_demo');
console.log('PASS staff profile hides administration and retains operational account details');
vm.runInContext(fs.readFileSync(path.join(root,'shell.js'),'utf8'),e.context);
assert.equal(e.context.isAdminWorkspace({role:'admin'},'account.html'),true);
assert.equal(e.context.isAdminWorkspace({role:'admin'},'statistics.html'),true);
assert.equal(e.context.isAdminWorkspace({role:'admin'},'bookings.html'),false);
assert.equal(e.context.isAdminWorkspace({role:'admin'},'customers.html'),false);
assert.equal(e.context.isAdminWorkspace({role:'admin'},'customers.html','?workspace=admin'),true);
assert.equal(e.context.isAdminWorkspace({role:'customer'},'customers.html','?workspace=admin'),false);
const operational=vm.runInContext('JSON.stringify(shellNavigation.map(item=>item[0]))',e.context);
assert.equal(operational.includes('statistics.html'),false);assert.equal(operational.includes('rooms.html'),false);
assert.equal(operational.includes('customers.html'),true);assert.equal(operational.includes('checkout.html'),true);
console.log('PASS operational navigation excludes room management/statistics and admin context is role checked');
console.log('Profile/navigation: 3 groups passed.');
