const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const {env,Element,root}=require('./frontend-test-env.cjs');
const {context:p}=env('profile');
for(const value of ['0901234567','0123456789'])assert.equal(p.validAccountPhone(value),true);
for(const value of ['901234567','09012345678','+84901234567','090 1234567','abcdefghij'])assert.equal(p.validAccountPhone(value),false);
for(const value of ['user.name@gmail.com','User1@GMAIL.COM','user+tag@example.org','a@sub.example.com'])assert.equal(p.validAccountEmail(value),true);
for(const value of ['user@example','user@-example.com','user@@gmail.com','.user@gmail.com','user..name@gmail.com','user name@gmail.com'])assert.equal(p.validAccountEmail(value),false);
assert.equal(p.validAccountUsername('admin1'),true);assert.equal(p.validAccountUsername('admin'),false);assert.equal(p.validAccountUsername('user name'),false);
for(const value of ['Abc@12','  Abc@12  ','Password1!'])assert.equal(p.validAccountPassword(value),true);
for(const value of ['Ab@12','abc@12','ABC@12','Abcd@!','Abcd12','Abc 12','123456'])assert.equal(p.validAccountPassword(value),false);
console.log('PASS general email, phone, username and password policy boundaries');
function authEnv(view='register',token='') {
    const e=env('profile');e.context.window.location={pathname:'/pages/login.html',search:'?view='+view,hash:token?'#token='+token:'',replace:value=>{e.redirect=value;}};
    e.context.window.history={replaceState:(_,__,value)=>{e.cleaned=value;}};e.context.document.querySelector=()=>new Element('section');e.context.document.body.classList.toggle=()=>{};
    e.context.loginDestination=()=>'/pages/landing.html';e.context.checkLogin=async()=>({role:'customer'});
    vm.runInContext(fs.readFileSync(path.join(root,'account-auth.js'),'utf8'),e.context);return e;
}
(async()=>{
    let e=authEnv(),calls=[];e.context.apiPost=async(path,body)=>{calls.push({path,body});return {success:true};};
    const values={'customer-name':'Demo Name','customer-phone':'0901234567','customer-email':'demo+tag@example.org','customer-username':'demo01','customer-password':'Abc@12','customer-confirm':'Abc@12'};
    for(const [id,value] of Object.entries(values))e.el(id).value=value;
    const event={preventDefault(){},currentTarget:e.el('register-form')};
    for(const [id,value] of [['customer-phone','09012345678'],['customer-email','demo@example'],['customer-username','demo1'],['customer-password','abc@12']]) {
        e.el(id).value=value;await e.el('register-form').events.submit(event);assert.equal(e.el(id).getAttribute('aria-invalid'),'true');assert.equal(calls.length,0);e.el(id).value=values[id];
    }
    let finish;e.context.apiPost=(path,body)=>{calls.push({path,body});return new Promise(resolve=>{finish=resolve;});};
    const submitting=e.el('register-form').events.submit(event);await e.el('register-form').events.submit(event);
    assert.equal(calls.length,1);assert.equal(calls[0].path,'/auth/register');assert.equal(calls[0].body.password,'Abc@12');assert.equal(calls[0].body.role,undefined);
    finish({success:true});await submitting;assert.equal(e.redirect,'/pages/landing.html');assert.equal(e.el('customer-password').value,'');
    console.log('PASS registration validates and posts once, never selects role, clears passwords and continues customer session');
    e=authEnv('forgot-password');e.el('recovery-email').value='unknown@example.org';let recovery;
    e.context.apiPost=async(path,body)=>{recovery={path,body};return {success:true,message:'Generic recovery message'};};
    await e.el('forgot-password-form').events.submit({preventDefault(){},currentTarget:e.el('forgot-password-form')});
    assert.equal(recovery.path,'/auth/forgot-password');assert.equal(e.el('recovery-status').textContent,'Generic recovery message');
    console.log('PASS recovery posts email and displays the server generic response');
    const token='12345678-1234-1234-1234-123456789012.'+'a'.repeat(43);e=authEnv('reset-password',token);assert.equal(e.cleaned,'/pages/login.html?view=reset-password');
    e.el('reset-password').value='NewPassword@123';e.el('reset-confirm').value='Mismatch';let resets=0;
    e.context.apiPost=async(path,body)=>{resets++;assert.equal(path,'/auth/reset-password');assert.equal(body.token,token);return {success:true};};
    const resetEvent={preventDefault(){},currentTarget:e.el('reset-password-form')};await e.el('reset-password-form').events.submit(resetEvent);assert.equal(resets,0);
    e.el('reset-confirm').value='NewPassword@123';await e.el('reset-password-form').events.submit(resetEvent);assert.equal(resets,1);assert.equal(e.redirect,'login.html?changed=1');
    console.log('PASS reset removes fragment, checks confirmation and submits a one-use token');
})().catch(error=>{console.error(error);process.exitCode=1;});
