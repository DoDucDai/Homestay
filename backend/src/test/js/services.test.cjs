const assert=require('node:assert/strict');
const {env}=require('./frontend-test-env.cjs');
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {resolve,promise};};
const service={serviceId:1,serviceName:'Dịch vụ thử',unitPrice:100000,status:'Hoạt động'};
let passed=0;const pass=name=>{passed++;console.log('PASS '+name);};
(async()=>{
 let e=env('services'),pending=deferred();e.context.apiGet=()=>pending.promise;
 const old=e.context.loadServices();e.context.apiGet=async()=>[];await e.context.loadServices();pending.resolve([service]);await old;
 assert.match(e.el('services-tbody').textContent,/Chưa có dịch vụ/);
 const list=Array.from({length:45},(_,i)=>({...service,serviceId:i+1}));e.context.apiGet=async()=>list;await e.context.loadServices();
 assert.equal(e.el('services-tbody').children.length,20);e.el('pager-services').children[2].events.click();e.el('pager-services').children[2].events.click();assert.equal(e.el('services-tbody').children.length,5);
 e.context.apiGet=async()=>{throw Error('Offline');};await e.context.loadServices();assert.equal(e.el('services-tbody').textContent,'Offline');assert.equal(e.el('pager-services').hidden,true);
 pass('service reloads ignore stale data; 45 rows paginate 20/20/5 and errors clear old rows and navigation');

 e=env('services');e.context.openAddModal();e.el('service-price').value='-1';e.el('service-status').value='INVALID';await e.context.saveService();
 assert.equal(e.writes.length,0);assert.equal(e.el('service-name').focused,true);
 for(const key of ['name','price','status']) {assert.equal(e.el('service-'+key).getAttribute('aria-invalid'),'true');assert.equal(e.el('service-'+key+'-error').hidden,false);}
 e.context.openAddModal();assert.equal(e.el('service-price-error').hidden,true);e.el('service-name').value='Miễn phí';e.el('service-price').value='0';await e.context.saveService();
 assert.equal(e.writes[0].body.unitPrice,0);assert.equal(e.writes[0].body.status,'Hoạt động');assert.equal(e.writes[0].endpoint,'/services');
 pass('service field errors focus and reset correctly; zero price remains valid and posts the original API payload');

 e=env('services');e.context.openEditModal(service);pending=deferred();let saves=0,closed=0;e.el('service-modal').classList.remove=()=>closed++;
 e.context.apiPut=(url,data)=>{saves++;assert.equal(url,'/services/1');assert.equal(data.unitPrice,100000);return pending.promise;};
 const saving=e.context.saveService();await e.context.saveService();e.context.closeServiceModal();e.context.openAddModal();
 assert.equal(saves,1);assert.equal(closed,0);assert.equal(e.el('service-id').value,1);assert.equal(e.el('service-save-btn').getAttribute('aria-busy'),'true');
 pending.resolve({success:false,message:'Rejected'});await saving;assert.equal(e.el('service-form-error').textContent,'Rejected');assert.equal(e.el('service-form-error').hidden,false);assert.equal(e.el('service-save-btn').getAttribute('aria-busy'),'false');
 e.context.closeServiceModal();assert.equal(closed,1);
 pass('service save blocks duplicate submission, modal dismissal and replacement while pending; server errors remain visible');

 e=env('services');e.context.apiGet=async()=>[service];await e.context.loadServices();pending=deferred();let deletes=0;
 e.context.apiDelete=()=>{deletes++;return pending.promise;};const deleting=e.context.deleteService(1,service.serviceName);await Promise.resolve();await e.context.deleteService(1,service.serviceName);
 assert.equal(deletes,1);assert.equal(e.el('services-tbody').children[0].children[4].children[0].disabled,true);
 pending.resolve({success:false,message:'Dịch vụ đã sử dụng'});await deleting;await Promise.resolve();
 assert.ok(e.el('services-tbody').textContent.includes(service.serviceName));assert.equal(e.el('services-tbody').children[0].children[4].children[0].disabled,false);
 pass('service deletion confirms once, disables row actions while pending and preserves a service rejected by the server');
 console.log('SERVICES frontend: '+passed+' groups passed (instrumented DOM/API).');
})().catch(e=>{console.error(e);process.exitCode=1;});
