// NOTE KIỂM THỬ: Kiểm tra điều hướng trang công khai/bảo vệ, URL quay lại sau đăng nhập và đăng xuất trong môi trường giả.
// Code chạy bằng Node trong môi trường giả để kiểm tra frontend; các assert so kết quả thực tế với kết quả mong đợi.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../../../../frontend');
function script(name){return fs.readFileSync(path.join(root,'js',name),'utf8');}
class Element{
 constructor(){this.value='';this.hidden=false;this.textContent='';this.events={};this.children=[];this.attributes={};}
 append(item){this.children.push(item);}
 replaceChildren(...items){this.children=items;}
 addEventListener(event,callback){this.events[event]=callback;}
 setAttribute(key,value){this.attributes[key]=value;}
}
async function anonymousLanding(){
 const nodes=Object.fromEntries(['landing-account','landing-greeting','landing-sync','landing-room-type','landing-year','landing-date','landing-time','landing-search','landing-search-error'].map(id=>[id,new Element()]));
 const links=['support.html','assistant.html','policies.html#bao-mat','explore.html','bookings.html','account.html','statistics.html'].map(route=>({href:'http://homestay.test/pages/'+route}));
 let ready;
 const location={origin:'http://homestay.test',pathname:'/pages/landing.html',search:'',hash:'',href:'http://homestay.test/pages/landing.html'};
 const ctx={document:{getElementById:id=>nodes[id],querySelectorAll:()=>links,addEventListener:(event,cb)=>{if(event==='DOMContentLoaded')ready=cb;}},
  window:{location,addEventListener(){}},checkLogin:async()=>null,renderPublicAccount(){},selectOption:(value,text)=>({value,text}),
  vietnamNow:()=> '2026-11-02T10:00:00',Date,URL,URLSearchParams,Set,setInterval(){},clearInterval(){}};
 vm.runInNewContext(script('landing.js'),ctx);await ready();
 for(let i=0;i<4;i++)assert.ok(links[i].href.startsWith('http://homestay.test/pages/'),'Public destination must remain public: '+links[i].href);
 for(let i=4;i<links.length;i++)assert.ok(links[i].href.startsWith('login.html?next='),'Protected destination must retain login redirect');
 nodes['landing-date'].value='2026-11-10';nodes['landing-time'].value='13:45';nodes['landing-room-type'].value='VIP';
 nodes['landing-search'].events.submit({preventDefault(){}});
 const login=new URL(location.href,'http://homestay.test/pages/landing.html');
 assert.equal(login.pathname,'/pages/login.html');
 assert.equal(login.searchParams.get('next'),'/pages/bookings.html?date=2026-11-10&hour=13&mode=hours&type=VIP');
 location.href='landing.html';nodes['landing-date'].value='2020-01-01';nodes['landing-search'].events.submit({preventDefault(){}});
 assert.equal(location.href,'landing.html');assert.equal(nodes['landing-search-error'].hidden,false);
}
function loginRedirects(){
 const location={href:'http://homestay.test/pages/login.html',origin:'http://homestay.test',search:''};
 const ctx={document:{getElementById:()=>null,addEventListener(){}},window:{location,addEventListener(){}},URL,URLSearchParams};
 vm.runInNewContext(script('auth.js'),ctx);
 for(const route of ['/pages/assistant.html','/pages/support.html#contact-request','/pages/policies.html#bao-mat','/pages/bookings.html?date=2026-11-10&type=VIP']){
  location.search='?next='+encodeURIComponent(route);assert.equal(ctx.loginDestination(),route);
 }
 for(const route of ['https://evil.example/pages/bookings.html','/api/auth/logout','/pages/../admin','javascript:alert(1)']){
  location.search='?next='+encodeURIComponent(route);assert.equal(ctx.loginDestination(),'landing.html');
 }
}
async function logoutWithoutEndingRealSession(){
 const nodes=new Map();const get=id=>{if(!nodes.has(id))nodes.set(id,new Element());return nodes.get(id);};
 const calls=[];let finish,redirect;
 const ctx={document:{getElementById:get,querySelectorAll:()=>[],addEventListener(){},body:{classList:{add(){},remove(){}}}},
  window:{location:{pathname:'/pages/landing.html',replace:value=>{redirect=value;}},addEventListener(){}},
  apiPost:async endpoint=>{calls.push(endpoint);return new Promise(resolve=>{finish=resolve;});}};
 vm.runInNewContext(script('account.js'),ctx);
 const first=ctx.logoutAccount({preventDefault(){}});await ctx.logoutAccount({preventDefault(){}});
 assert.deepEqual(calls,['/auth/logout'],'Double-click must send one logout request');
 finish({success:true});await first;assert.equal(redirect,'login.html');
 for(const route of ['/pages/landing.html','/pages/explore.html','/pages/policies.html','/pages/support.html']){
  ctx.window.location.pathname=route;assert.equal(ctx.hasPublicHeader(),true);
 }
}
(async()=>{await anonymousLanding();loginRedirects();await logoutWithoutEndingRealSession();console.log('Landing navigation passed: public/protected links, search filters, invalid dates, safe login return URLs and mocked logout.');})().catch(error=>{console.error(error);process.exitCode=1;});
