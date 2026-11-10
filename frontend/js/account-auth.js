// NOTE FRONTEND: Chọn màn hình đăng ký khách hàng, quên mật khẩu hoặc đặt lại mật khẩu theo query view.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
'use strict';
const customerViews={register:'register-view','forgot-password':'forgot-password-view','reset-password':'reset-password-view'};
const requestedCustomerView=new URLSearchParams(window.location.search).get('view');
const selectedCustomerView=customerViews[requestedCustomerView] || 'login-view';
for(const id of ['login-view',...Object.values(customerViews)])document.getElementById(id).hidden=id!==selectedCustomerView;
const customerTitles={register:'Đăng ký khách hàng', 'forgot-password':'Quên mật khẩu', 'reset-password':'Đặt lại mật khẩu'};
document.title=(customerTitles[requestedCustomerView] || 'Đăng nhập')+' - Homestay';
document.querySelector('.login-form-panel').setAttribute('aria-labelledby',requestedCustomerView==='register'?'register-title':requestedCustomerView==='forgot-password'?'forgot-password-title':requestedCustomerView==='reset-password'?'reset-password-title':'login-title');
document.body.classList.toggle('customer-register-page',selectedCustomerView==='register-view');
let resetToken=new URLSearchParams((window.location.hash || '').slice(1)).get('token') || '';
if(requestedCustomerView==='reset-password' && window.history)window.history.replaceState(null,'',window.location.pathname+window.location.search);

// NOTE FRONTEND: Cập nhật aria-invalid và lỗi ngay dưới trường đăng ký/khôi phục.
function customerFieldError(id,message='') {
    const field=document.getElementById(id),error=document.getElementById(id+'-error');
    field.setAttribute('aria-invalid',String(!!message));error.textContent=message;error.hidden=!message;
}
// NOTE FRONTEND: Dùng chung quy tắc email với hạ tầng tài khoản, không tự đặt một định dạng khác.
function validCustomerEmail(value) { return validAccountEmail(value); }
// NOTE FRONTEND: Hiển thị kết quả trả về từ server trong vùng trạng thái của biểu mẫu.
function customerPreviewResult(id,message) { const target=document.getElementById(id);target.textContent=message;target.hidden=!message; }
for(const id of ['customer-password','customer-confirm','reset-password','reset-confirm'])bindPasswordVisibility(document.getElementById(id),document.getElementById(id+'-toggle'));
// NOTE FRONTEND: Khóa input/nút và đánh dấu aria-busy trong lúc yêu cầu đang gửi để tránh thao tác lặp.
function lockCustomerForm(form,locked) {
    form.setAttribute('aria-busy',String(locked));form.querySelectorAll('input,button').forEach(control=>control.disabled=locked);
}
let registering=false,recovering=false,resetting=false;
// NOTE FRONTEND: Kiểm tra họ tên, liên hệ, username và hai mật khẩu rồi POST /auth/register; quyền customer do backend chọn, không gửi role.
document.getElementById('register-form').addEventListener('submit',async event=>{
    event.preventDefault();if(registering)return;customerPreviewResult('register-status','');
    const value=id=>document.getElementById(id).value;
    const name=value('customer-name').trim(),phone=value('customer-phone').trim(),email=value('customer-email').trim(),username=value('customer-username').trim();
    const password=value('customer-password'),confirmation=value('customer-confirm');
    const errors=[
        ['customer-name',!name || name.length>100?'Nhập họ tên, tối đa 100 ký tự.':''],
        ['customer-phone',!validAccountPhone(phone)?'Số điện thoại cần đúng 10 chữ số, bắt đầu bằng 0.':''],
        ['customer-email',!validCustomerEmail(email)?'Nhập email hợp lệ.':''],
        ['customer-username',!validAccountUsername(username)?'Dùng 6–50 ký tự gồm chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang.':''],
        ['customer-password',!validAccountPassword(password)?'Mật khẩu cần 6–128 ký tự, có chữ hoa, chữ thường, chữ số và ký tự đặc biệt.':''],
        ['customer-confirm',!confirmation || confirmation!==password?'Mật khẩu nhập lại chưa khớp.':'']
    ];
    errors.forEach(([id,message])=>customerFieldError(id,message));const first=errors.find(([,message])=>message);
    if(first){document.getElementById(first[0]).focus();return;}
    registering=true;lockCustomerForm(event.currentTarget,true);customerPreviewResult('register-status','Đang tạo tài khoản...');
    try {
        await apiPost('/auth/register',{fullName:name,phone,email,username,password});
        for(const id of ['customer-password','customer-confirm'])document.getElementById(id).value='';
        const user=await checkLogin(true);
        customerPreviewResult('register-status','Tài khoản đã được tạo. Đang mở trang chủ...');
        window.location.replace(user?loginDestination(user):'login.html?registered=1');
    } catch(error) { customerPreviewResult('register-status',error.message || 'Chưa xác nhận được đăng ký. Hãy thử đăng nhập trước khi gửi lại.');registering=false;lockCustomerForm(document.getElementById('register-form'),false); }
});
// NOTE FRONTEND: Gửi email đến /auth/forgot-password và hiện thông báo chung của server, không suy email có tài khoản từ thông báo.
document.getElementById('forgot-password-form').addEventListener('submit',async event=>{
    event.preventDefault();if(recovering)return;customerPreviewResult('recovery-status','');
    const email=document.getElementById('recovery-email').value.trim(),error=validCustomerEmail(email)?'':'Nhập email đăng ký hợp lệ.';
    customerFieldError('recovery-email',error);if(error){document.getElementById('recovery-email').focus();return;}
    recovering=true;lockCustomerForm(event.currentTarget,true);
    try { const result=await apiPost('/auth/forgot-password',{email});customerPreviewResult('recovery-status',result.message); }
    catch(error) { customerPreviewResult('recovery-status',error.message || 'Không thể yêu cầu đặt lại mật khẩu.'); }
    finally { recovering=false;lockCustomerForm(document.getElementById('forgot-password-form'),false); }
});
// NOTE FRONTEND: Gửi token lấy từ liên kết cùng mật khẩu mới đã xác nhận; thành công thì xóa token và chuyển về đăng nhập.
document.getElementById('reset-password-form').addEventListener('submit',async event=>{
    event.preventDefault();if(resetting)return;
    const password=document.getElementById('reset-password').value,confirmation=document.getElementById('reset-confirm').value;
    if(!resetToken){customerPreviewResult('reset-status','Liên kết không hợp lệ. Vui lòng yêu cầu đặt lại mật khẩu lần nữa.');return;}
    if(!validAccountPassword(password) || password!==confirmation){customerPreviewResult('reset-status','Mật khẩu cần 6–128 ký tự, đủ chữ hoa, thường, số, ký tự đặc biệt; hai lần nhập phải khớp.');return;}
    resetting=true;lockCustomerForm(event.currentTarget,true);
    try { await apiPost('/auth/reset-password',{token:resetToken,password});resetToken='';window.location.replace('login.html?changed=1'); }
    catch(error) { customerPreviewResult('reset-status',error.message || 'Không thể đặt lại mật khẩu.');resetting=false;lockCustomerForm(document.getElementById('reset-password-form'),false); }
});
for(const id of ['customer-name','customer-phone','customer-email','customer-username','customer-password','customer-confirm','recovery-email'])document.getElementById(id).addEventListener('input',()=>{
    customerFieldError(id);customerPreviewResult(id==='recovery-email'?'recovery-status':'register-status','');if(id==='customer-password')customerFieldError('customer-confirm');
});
window.addEventListener('pagehide',()=>{
    resetToken='';for(const id of ['customer-password','customer-confirm','reset-password','reset-confirm'])document.getElementById(id).value='';
});
