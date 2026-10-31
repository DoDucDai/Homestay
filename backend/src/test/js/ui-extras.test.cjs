const assert=require('node:assert/strict');
const vm=require('node:vm');
const {env,Element,payload}=require('./frontend-test-env.cjs');
let passed=0;const pass=name=>{passed++;console.log('PASS '+name);};
const defer=()=>{let resolve;const promise=new Promise(r=>{resolve=r;});return {promise,resolve};};
(async()=>{
 let e=env('statistics');
 const csv=e.context.makeCsv([{label:'Tên',value:r=>r.name},{label:'Giấy tờ',value:r=>r.id,text:true},{label:'Tiền',value:r=>r.amount}],
  [{name:'Đức, "Đại"\nxuống dòng',id:'001234567890',amount:123},{name:'\t=HYPERLINK("bad")',id:'AB123456',amount:0},{name:'@SUM(1)',id:'0',amount:1}]);
 assert.ok(csv.startsWith('\ufeff'));assert.ok(csv.includes('"Đức, ""Đại""\nxuống dòng"'));assert.ok(csv.includes('"\'001234567890"'));assert.ok(csv.includes('"\'\t=HYPERLINK'));assert.ok(csv.includes('"\'@SUM(1)"'));assert.ok(csv.includes(',"123"'));
 for(const value of ['=1','+1','-1','@x',' \r\n=1','\u0001=1'])assert.ok(e.context.makeCsv([{label:'x',value:()=>value}],[{}]).includes('"\''));
 pass('CSV has BOM, quoted commas/quotes/newlines, text identifiers and formula/control-character neutralization');

 let revoked=[],created=[],timers=[];e.context.Blob=Blob;e.context.URL={createObjectURL:blob=>{created.push(blob);return 'blob:test';},revokeObjectURL:url=>revoked.push(url)};e.context.setTimeout=fn=>timers.push(fn);
 Element.prototype.click=function(){this.clicked=true;};
 assert.equal(e.context.downloadCsv('x.csv',[],[],true),false);assert.equal(e.context.downloadCsv('x.csv',[],[{}],false),false);assert.equal(created.length,0);
 assert.equal(e.context.downloadCsv('x.csv',[{label:'x',value:()=>1}],[{}]),true);assert.equal(created.length,1);assert.equal(e.context.document.body.children.length,0);timers.forEach(fn=>fn());assert.deepEqual(revoked,['blob:test']);
 pass('CSV blocks loading/errors/empty data and releases temporary links/object URLs');

 e=env('statistics');let slice;const rows=Array.from({length:45},(_,id)=>({id}));const redraw=all=>{slice=e.context.pageRows('test',all,redraw);};redraw(rows);
 assert.equal(slice.length,20);assert.equal(e.el('pager-test').children[0].disabled,true);e.el('pager-test').children[2].events.click();assert.equal(slice[0].id,20);e.el('pager-test').children[2].events.click();assert.equal(slice.length,5);assert.equal(e.el('pager-test').children[2].disabled,true);
 redraw([{id:100}]);assert.equal(slice[0].id,100);assert.equal(e.el('pager-test').hidden,true);
 pass('pagination moves 45 rows across 3 pages, clamps navigation and resets on a new filter result');

 e=env('rooms');e.context.apiGet=async()=>rows.map(r=>({roomId:r.id,roomCode:'P'+r.id,roomName:'Room',roomType:'Single',price:1,status:'Trống'}));await e.context.loadRooms();let exported;
 e.context.downloadCsv=(file,columns,data,ready)=>{exported={file,columns,data,ready};return true;};e.context.exportRooms();assert.equal(e.el('rooms-tbody').children.length,20);assert.equal(exported.data.length,45);assert.equal(exported.ready,true);assert.ok(exported.columns.every(c=>!c.label.includes('Thao tác')));
 e.context.apiGet=async()=>{throw Error('Offline');};await e.context.loadRooms();e.context.exportRooms();assert.equal(exported.ready,false);assert.equal(e.el('pager-rooms').hidden,true);assert.equal(e.el('pager-rooms').children.length,0);
 pass('filtered CSV exports all matching rows across pages and becomes unavailable after load failure');

 e=env('statistics');e.el('stat-month').value='1';e.el('stat-year').value='2031';e.el('chart-year').value='2031';
 const invoice={paymentId:1,customerName:payload,roomCode:'P1',roomTotal:600000,serviceTotal:170000,grandTotal:770000,paymentMethod:'Tiền mặt',paidAt:'2031-01-20'};
 e.context.apiGet=async url=>url.startsWith('/statistics/yearly')?[770000,...Array(11).fill(0)]:[invoice];
 await e.context.loadRevenue();assert.equal(e.el('revenue-tbody').children[0].children.length,8);assert.ok(e.el('revenue-tbody').textContent.includes(payload));assert.equal(e.el('revenue-total').textContent,e.context.formatMoney(770000n));
 await e.context.loadYearly();assert.equal(e.el('yearly-tbody').children.length,12);assert.equal(e.el('yearly-total').textContent,e.context.formatMoney(770000n));assert.equal(e.el('yearly-chart').children.filter(n=>n.tagName==='RECT').length,12);
 await e.context.loadYearly();assert.equal(e.el('yearly-chart').children.filter(n=>n.tagName==='RECT').length,12);
 e.context.apiGet=async()=>({totalRooms:4,totalCustomers:1,totalBookingsThisMonth:1,revenueThisMonth:770000,availableRooms:1,bookedRooms:1,occupiedRooms:1,maintenanceRooms:1});await e.context.loadOverview();assert.equal(e.el('stat-total-rooms').textContent,'4');assert.ok(e.el('room-chart').children.length>1);
 pass('monthly/annual totals agree, charts replace old content, snapshot text is safe and room chart uses the same KPI data');

 const pending=defer();e.context.apiGet=()=>pending.promise;const old=e.context.loadYearly();e.context.apiGet=async()=>Array(12).fill(0);await e.context.loadYearly();pending.resolve(Array(12).fill(99));await old;
 assert.equal(e.el('yearly-total').textContent,e.context.formatMoney(0n));e.context.apiGet=async()=>{throw Error('Unavailable');};await e.context.loadYearly();assert.equal(e.el('yearly-total').textContent,'--');assert.equal(e.el('yearly-chart').children.length,0);
 e.el('chart-year').value='10000';await e.context.loadYearly();assert.match(e.el('yearly-status').textContent,/1–9999/);
 pass('stale yearly responses cannot overwrite the latest year; outages and invalid years do not appear as zero revenue');

 e=env('statistics');e.el('stat-month').value='1';e.el('stat-year').value='2031';
 let oldReport=defer();e.context.apiGet=()=>oldReport.promise;const monthlyRequest=e.context.loadRevenue();
 e.el('stat-month').value='2';e.context.invalidateRevenueFilter();oldReport.resolve([invoice]);await monthlyRequest;
 assert.equal(e.el('revenue-total').textContent,'--');assert.equal(e.el('revenue-export').disabled,true);assert.match(e.el('revenue-status').textContent,/Bộ lọc đã thay đổi/);
 e.context.downloadCsv=(file,columns,rows,ready)=>{assert.equal(ready,false);assert.equal(rows.length,0);};e.context.exportRevenue();
 e.el('chart-year').value='2031';oldReport=defer();e.context.apiGet=()=>oldReport.promise;const yearlyRequest=e.context.loadYearly();
 e.el('chart-year').value='2032';e.context.invalidateYearlyFilter();oldReport.resolve(Array(12).fill(770000));await yearlyRequest;
 assert.equal(e.el('yearly-total').textContent,'--');assert.equal(e.el('yearly-chart').children.length,0);assert.match(e.el('yearly-status').textContent,/Năm đã thay đổi/);
 pass('changing report filters clears old totals/CSV/charts and invalidates pending responses before the next load');

 e=env('statistics');e.context.checkLogin=async()=>({role:'staff'});let statisticsReads=0;e.context.apiGet=async()=>{statisticsReads++;return [];};
 e.el('statistics-content').hidden=true;e.el('statistics-denied').hidden=true;await e.events.DOMContentLoaded();
 assert.equal(statisticsReads,0);assert.equal(e.el('statistics-content').hidden,true);assert.equal(e.el('statistics-denied').hidden,false);
 pass('staff direct navigation shows an explicit denial without requesting statistics or exposing report controls');

 e=env('statistics',{realConfirmation:true});e.context.document.activeElement=null;
 const decision=e.context.askConfirmation(payload,'Xác nhận thử');let overlay=e.context.document.body.children[0];assert.equal(overlay.getAttribute('role'),'dialog');assert.ok(overlay.textContent.includes(payload));assert.equal(await e.context.askConfirmation('duplicate'),false);
 const footer=overlay.children[0].children[2];footer.children[1].events.click();assert.equal(await decision,true);assert.equal(e.context.document.body.children.length,0);
 const cancelled=e.context.askConfirmation('Cancel');overlay=e.context.document.body.children[0];overlay.events.keydown({key:'Escape',preventDefault(){}});assert.equal(await cancelled,false);
 pass('DOM confirmation renders untrusted text safely, prevents overlapping prompts and supports accept/Escape cancel');
 e=env('bookings');
 const bookings=Array.from({length:45},(_,i)=>({bookingId:i+1,customerName:i===44?payload:'Nguyễn An',roomCode:'P101',status:i===44?'CheckedIn':'Confirmed'}));
 e.context.renderTable(bookings);
 e.el('pager-bookings').children[2].events.click();assert.equal(e.el('bookings-tbody').children.length,20);assert.ok(e.el('bookings-tbody').textContent.includes('BK21'));
 e.el('booking-search').value='  NGUYỄN  ';e.el('booking-status-filter').value='Confirmed';e.context.filterBookings();
 assert.ok(e.el('bookings-tbody').textContent.includes('BK1'));assert.equal(e.el('bookings-tbody').children.length,20);
 e.el('pager-bookings').children[2].events.click();assert.ok(e.el('bookings-tbody').textContent.includes('BK21'));
 e.el('booking-search').value='bk44';e.context.filterBookings();assert.equal(e.el('bookings-tbody').children.length,1);assert.ok(e.el('bookings-tbody').textContent.includes('BK44'));assert.equal(e.el('pager-bookings').hidden,true);
 pass('booking search combines case-insensitive Vietnamese name/ID/room with status and preserves pagination navigation');
 e.el('booking-search').value='<img';e.el('booking-status-filter').value='CheckedIn';e.context.filterBookings();
 assert.ok(e.el('bookings-tbody').textContent.includes(payload));assert.equal(e.el('bookings-tbody').children[0].children[7].children.length,0);assert.equal(e.context.pwned,undefined);
 e.el('booking-status-filter').value='Cancelled';e.context.filterBookings();assert.equal(e.el('pager-bookings').hidden,true);assert.equal(e.el('bookings-tbody').children.length,1);
 pass('booking filters render hostile text safely, hide cancellation for checked-in guests and handle no matches');
 const waiting=defer();e.context.apiGet=()=>waiting.promise;const loading=e.context.loadBookings();e.el('booking-search').value='BK1';e.context.filterBookings();assert.ok(e.el('bookings-tbody').textContent.includes('Đang tải'));
 waiting.resolve([]);await loading;e.context.apiGet=async()=>{throw Error('Unavailable');};await e.context.loadBookings();e.context.filterBookings();assert.ok(e.el('bookings-tbody').textContent.includes('Unavailable'));
 pass('booking filtering cannot restore stale rows while loading or after an API failure');
 console.log('REPORT/UX frontend: '+passed+' groups passed.');
})().catch(error=>{console.error(error);process.exitCode=1;});
