// UI preview only. These forms deliberately make no account or email requests.
const memberViews={register:'register-view','forgot-password':'forgot-password-view'};
const requestedMemberView=new URLSearchParams(window.location.search).get('view');
const selectedMemberView=memberViews[requestedMemberView] || 'login-view';
for(const id of ['login-view','register-view','forgot-password-view'])document.getElementById(id).hidden=id!==selectedMemberView;
const memberTitle=selectedMemberView==='register-view'?'register-title':selectedMemberView==='forgot-password-view'?'forgot-password-title':'login-title';
document.querySelector('.login-form-panel').setAttribute('aria-labelledby',memberTitle);
document.title=selectedMemberView==='register-view'?'Đăng ký thành viên - Homestay':selectedMemberView==='forgot-password-view'?'Quên mật khẩu - Homestay':'Đăng nhập - Homestay';
document.body.classList.toggle('member-register-page',selectedMemberView==='register-view');

function memberFieldError(id,message=''){
    const field=document.getElementById(id),error=document.getElementById(id+'-error');
    field.setAttribute('aria-invalid',String(!!message));error.textContent=message;error.hidden=!message;
}
function validMemberEmail(value){return value.length<=100 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);}
function memberPreviewResult(id,message){const target=document.getElementById(id);target.textContent=message;target.hidden=!message;}
for(const [input,toggle,label] of [['member-password','member-password-toggle','mật khẩu'],['member-confirm','member-confirm-toggle','mật khẩu nhập lại']])bindPasswordVisibility(document.getElementById(input),document.getElementById(toggle),label);

document.getElementById('register-form').addEventListener('submit',event=>{
    event.preventDefault();memberPreviewResult('register-status','');
    const value=id=>document.getElementById(id).value;
    const name=value('member-name').trim(),phone=value('member-phone').replace(/[\s()-]/g,''),email=value('member-email').trim(),username=value('member-username').trim();
    const password=value('member-password'),confirmation=value('member-confirm');
    const errors=[
        ['member-name',!name || name.length>100?'Nhập họ tên, tối đa 100 ký tự.':''],
        ['member-phone',!/^\+?\d{9,15}$/.test(phone)?'Nhập số điện thoại hợp lệ, từ 9–15 chữ số.':''],
        ['member-email',!validMemberEmail(email)?'Nhập địa chỉ email hợp lệ.':''],
        ['member-username',!/^[A-Za-z0-9_.-]{3,50}$/.test(username)?'Dùng 3–50 ký tự gồm chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang.':''],
        ['member-password',password.length<12 || password.length>128 || !password.trim()?'Mật khẩu cần 12–128 ký tự, không chỉ chứa khoảng trắng.':''],
        ['member-confirm',!confirmation || confirmation!==password?'Mật khẩu nhập lại chưa khớp.':'']
    ];
    errors.forEach(([id,message])=>memberFieldError(id,message));
    const first=errors.find(([,message])=>message);
    if(first){document.getElementById(first[0]).focus();return;}
    for(const id of ['member-password','member-confirm']){
        document.getElementById(id).value='';
        setPasswordVisibility(document.getElementById(id),document.getElementById(id+'-toggle'));
    }
    memberPreviewResult('register-status','Các trường đã được điền đúng định dạng. Đây là bản xem trước; tài khoản chưa được tạo.');
});
document.getElementById('forgot-password-form').addEventListener('submit',event=>{
    event.preventDefault();memberPreviewResult('recovery-status','');
    const email=document.getElementById('recovery-email').value.trim();
    const error=validMemberEmail(email)?'':'Nhập địa chỉ email hợp lệ để khôi phục tài khoản.';
    memberFieldError('recovery-email',error);
    if(error){document.getElementById('recovery-email').focus();return;}
    memberPreviewResult('recovery-status','Email đúng định dạng. Đây là bản xem trước; chưa gửi hướng dẫn và chưa thay đổi mật khẩu.');
});
for(const id of ['member-name','member-phone','member-email','member-username','member-password','member-confirm','recovery-email']){
    document.getElementById(id).addEventListener('input',()=>{
        memberFieldError(id);memberPreviewResult(id==='recovery-email'?'recovery-status':'register-status','');
        if(id==='member-password')memberFieldError('member-confirm');
    });
}
window.addEventListener('pagehide',()=>{
    for(const id of ['member-password','member-confirm']){
        document.getElementById(id).value='';setPasswordVisibility(document.getElementById(id),document.getElementById(id+'-toggle'));
    }
});
