'use strict';
let verificationUser=null,verificationBusy=false,verificationNextSend=0,verificationTimer=null;
function emailReturnTarget() {
    try {
        const requested=new URLSearchParams(window.location.search).get('next');
        const url=new URL(requested || 'bookings.html',window.location.href);
        if(url.origin===window.location.origin && url.pathname==='/pages/bookings.html')return url.pathname+url.search;
    } catch(_) {}
    return 'bookings.html';
}
function renderEmailVerification(user) {
    verificationUser=user;
    const enabled=user.emailVerificationEnabled!==false;
    document.getElementById('verification-email').textContent=user.email || 'Chưa có email';
    document.getElementById('verification-description').textContent=enabled?'Xác thực email đã lưu để bảo vệ tài khoản và khôi phục mật khẩu. Nếu đổi email, bạn cần xác thực lại địa chỉ mới.':'Xác thực email đang tạm tắt. Bạn vẫn có thể sử dụng các chức năng của tài khoản.';
    document.getElementById('verification-state').textContent=user.emailVerified?'Đã xác thực':'Chưa xác thực';
    document.getElementById('verification-form').hidden=!enabled || !!user.emailVerified;
    document.getElementById('verification-send').hidden=!enabled || !!user.emailVerified;
    const back=document.getElementById('verification-return');back.hidden=user.role!=='customer';back.href=emailReturnTarget();
    updateVerificationControls();
}
function updateVerificationControls() {
    const wait=Math.max(0,Math.ceil((verificationNextSend-Date.now())/1000));
    const enabled=verificationUser && verificationUser.emailVerificationEnabled!==false;
    const send=document.getElementById('verification-send');send.disabled=verificationBusy || !enabled || !!wait;
    send.textContent=wait?'Gửi lại sau '+wait+' giây':'Gửi mã xác thực';
    document.getElementById('verification-confirm').disabled=verificationBusy || !enabled;
    if(!wait && verificationTimer){clearInterval(verificationTimer);verificationTimer=null;}
}
document.addEventListener('DOMContentLoaded',()=>{
    const status=document.getElementById('verification-status');
    document.getElementById('verification-send').addEventListener('click',async()=>{
        if(verificationBusy || verificationUser?.emailVerificationEnabled===false)return;
        verificationBusy=true;updateVerificationControls();status.textContent='Đang gửi mã...';
        try {
            const result=await apiPost('/auth/email-verification/request',{});status.textContent=result.message;
            verificationNextSend=Date.now()+result.retryAfter*1000;
            if(!verificationTimer)verificationTimer=setInterval(updateVerificationControls,1000);
            document.getElementById('verification-code').focus();
        } catch(error) { status.textContent=error.message; }
        finally { verificationBusy=false;updateVerificationControls(); }
    });
    document.getElementById('verification-form').addEventListener('submit',async event=>{
        event.preventDefault();if(verificationBusy || verificationUser?.emailVerificationEnabled===false)return;
        const code=document.getElementById('verification-code').value.trim();
        if(!/^\d{6}$/.test(code)){status.textContent='Nhập mã gồm đúng 6 chữ số.';return;}
        verificationBusy=true;updateVerificationControls();
        try {
            const result=await apiPost('/auth/email-verification/confirm',{code});
            const user=await checkLogin(true);if(user)renderEmailVerification(user);
            status.textContent=result.message;document.getElementById('verification-code').value='';
        } catch(error) { status.textContent=error.message; }
        finally { verificationBusy=false;updateVerificationControls(); }
    });
});
