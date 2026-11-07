// NOTE KIỂM THỬ: Kiểm tra thông tin tài khoản trên trang chủ, số liệu phòng và cách giữ số liệu lần trước khi tải lại lỗi.
// Code chạy bằng Node trong môi trường giả để kiểm tra frontend; các assert so kết quả thực tế với kết quả mong đợi.
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {env,payload,Element,root}=require('./frontend-test-env.cjs');
const rooms=[
    {roomId:1,roomCode:'P101',roomName:payload,roomType:'Single',status:'Trống',price:300000,hourlyPrice:null,description:payload},
    {roomId:2,roomCode:'P102',roomName:'Double',roomType:'Double',status:'Đang sử dụng',price:500000,hourlyPrice:80000},
    {roomId:3,roomCode:'P103',roomName:'Maintenance',roomType:'Single',status:'Bảo trì',price:300000,hourlyPrice:null}
];
(async()=>{
    const header=env('landing');
    vm.runInContext(fs.readFileSync(path.join(root,'account.js'),'utf8'),header.context);
    header.context.window.location={pathname:'/pages/landing.html',search:'',hash:''};
    const host=header.el('landing-account'),account=header.el('account-controls');
    const profile=new Element('a'),actions=new Element('div');
    account.append(profile,actions);
    account.querySelector=selector=>selector==='.account-profile'?profile:selector==='.account-actions'?actions:account.children.find(child=>child.className==='stay-account-menu')||null;
    header.context.addAccountControls=()=>{};
    header.context.renderLandingAccount(null);
    assert.equal(host.children.length,1);assert.equal(host.textContent,'Đăng nhập');
    header.context.renderLandingAccount({role:'admin'});
    assert.equal(host.children.length,1);assert.equal(host.children[0],account);
    assert.equal(host.textContent.includes('Đăng nhập'),false);
    header.context.renderLandingAccount({role:'admin'});
    assert.equal(host.children.length,1);
    assert.equal(account.children.filter(child=>child.className==='stay-account-menu').length,1);
    assert.equal(host.getAttribute('aria-busy'),'false');
    console.log('PASS verified admin identity replaces the guest login button and repeated rendering keeps one account menu');

    const e=env('landing');e.context.apiGet=async endpoint=>{assert.equal(endpoint,'/rooms');return rooms;};
    await e.context.loadLandingRooms();
    assert.equal(e.el('landing-total').textContent,'3');assert.equal(e.el('landing-free').textContent,'1');assert.equal(e.el('landing-types-count').textContent,'2');
    console.log('PASS landing statistics reflect actual inventory, availability and room types');

    e.context.apiGet=async()=>{throw Error('database unavailable')};await e.context.loadLandingRooms();assert.equal(e.el('landing-total').textContent,'3');assert.match(e.el('landing-sync').textContent,/lần tải trước/);
    console.log('PASS refresh failure retains last known inventory with an explicit stale-data status');
    console.log('Landing: 3 groups passed.');
})().catch(error=>{console.error(error);process.exitCode=1;});
