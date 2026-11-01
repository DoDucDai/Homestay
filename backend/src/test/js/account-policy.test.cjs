const assert=require('node:assert/strict');
const {env,Element,root}=require('./frontend-test-env.cjs');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const {context:p}=env('profile');
for(const value of ['0901234567','0123456789'])assert.equal(p.validAccountPhone(value),true);
for(const value of ['901234567','09012345678','+84901234567','090 1234567','abcdefghij'])assert.equal(p.validAccountPhone(value),false);
for(const value of ['user.name@gmail.com','User1@GMAIL.COM'])assert.equal(p.validAccountEmail(value),true);
for(const value of ['user@example.com','user@gmail.com.evil','user@@gmail.com','.user@gmail.com','user..name@gmail.com','user name@gmail.com'])assert.equal(p.validAccountEmail(value),false);
assert.equal(p.validAccountUsername('admin1'),true);assert.equal(p.validAccountUsername('admin'),false);
assert.equal(p.validAccountUsername('user name'),false);
for(const value of ['Abc@12','  Abc@12  ','Password1!'])assert.equal(p.validAccountPassword(value),true);
for(const value of ['Ab@12','abc@12','ABC@12','Abcd@!','Abcd12','Abc 12','123456'])assert.equal(p.validAccountPassword(value),false);
console.log('PASS phone length, Gmail domain, six-character username and password complexity including six-character boundary');

const form=env('profile');
form.context.window.location.search='?view=register';
form.context.document.querySelector=()=>new Element('section');
form.context.document.body.classList.toggle=()=>{};
form.context.fetch=()=>{throw new Error('Preview must not transmit personal data');};
vm.runInContext(fs.readFileSync(path.join(root,'member-preview.js'),'utf8'),form.context);
const values={'member-name':'Demo Name','member-phone':'0901234567','member-email':'demo.name@gmail.com','member-username':'demo01','member-password':'Abc@12','member-confirm':'Abc@12'};
for(const [id,value] of Object.entries(values))form.el(id).value=value;
for(const [id,value] of [['member-phone','09012345678'],['member-email','demo@example.com'],['member-username','demo1'],['member-password','abc@12']]) {
    form.el(id).value=value;
    form.el('register-form').events.submit({preventDefault(){}});
    assert.equal(form.el(id).getAttribute('aria-invalid'),'true');
    assert.equal(form.el('register-status').hidden,true);
    form.el(id).value=values[id];
}
form.el('register-form').events.submit({preventDefault(){}});
assert.equal(form.el('register-status').hidden,false);
assert.match(form.el('register-status').textContent,/chưa được tạo/);
assert.equal(form.el('member-password').value,'');
console.log('PASS registration form rejects each invalid field and accepts a six-character strong password without creating or transmitting an account');
