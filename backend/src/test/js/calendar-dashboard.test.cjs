const {env,root,payload}=require('./frontend-test-env.cjs');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
function setup(){
    const e=env('bookings');
    vm.runInContext(fs.readFileSync(path.join(root,'booking-calendar.js'),'utf8'),e.context);
    vm.runInContext("calendarStart='2026-10-31';calendarMode='days'",e.context);
    e.context.vietnamToday=()=> '2026-10-31';e.context.vietnamNow=()=> '2026-10-31T14:00:00';
    return e;
}
async function run(){
    let e=setup(),c=e.context;
    assert.equal(c.calendarDateAdd('2026-10-31',1),'2026-11-01');
    assert.equal(c.calendarDateAdd('2026-12-31',1),'2027-01-01');
    const hourly={roomId:1,bookingId:1,status:'CheckedIn',billingUnit:'Hour',checkinDate:'2026-10-31',checkoutDate:'2026-11-01',checkinAt:'2026-10-31T10:00:00',checkoutAt:'2026-10-31T15:00:00'};
    assert.equal(c.calendarBookingDate(hourly,'checkout'),'2026-10-31');
    assert.equal(c.calendarAttentionMatches(hourly,'departures'),true);
    const [a,z]=c.calendarBounds(hourly);assert.equal(z-a,5*3600000);assert.equal(a,Date.parse('2026-10-31T03:00:00Z'));
    console.log('PASS Vietnam dates cross month/year correctly; hourly departure uses actual timestamp, not legacy DATE envelope');

    vm.runInContext(`calendarRooms=[{roomId:1,roomCode:'P1',roomName:'Single',roomType:'Single',status:'Trống'},{roomId:2,roomCode:'P2',roomName:'VIP',roomType:'VIP',status:'Bảo trì'}];bookingRows=[{bookingId:10,roomId:1,customerName:'Nguyễn An',phone:'0901234567',status:'Confirmed',checkinDate:'2026-11-01',checkoutDate:'2026-11-02'}];`,c);
    e.el('booking-search').value='nguyễn';assert.equal(c.calendarVisibleRooms().length,1);
    e.el('booking-search').value='BK10';assert.equal(c.calendarVisibleRooms()[0].roomId,1);
    e.el('booking-search').value='';vm.runInContext("calendarType='VIP'",c);assert.equal(c.calendarVisibleRooms()[0].roomId,2);
    console.log('PASS room and guest/booking search combine with room-type filters against actual inventory');

    vm.runInContext(`hourlyBookingReady=true;hourlyRooms=[{roomId:1,hourlyPrice:80000}];hourlyCustomers=[{customerId:3}];bookingRows=[];`,c);
    e.el('hourly-room').value='1';e.el('hourly-customer').value='3';e.el('hourly-start').value='2026-10-31T15:00';e.el('hourly-end').value='2026-10-31T16:01';
    assert.equal(c.hourlyBookingInput().hours,2);assert.equal(c.hourlyBookingInput().total,160000);
    vm.runInContext(`bookingRows=[{roomId:1,status:'Confirmed',billingUnit:'Hour',checkinAt:'2026-10-31T16:00',checkoutAt:'2026-10-31T17:00'}]`,c);
    assert.throws(()=>c.hourlyBookingInput(),/đã có lượt đặt/);
    e.el('hourly-end').value='2026-10-31T16:00';assert.equal(c.hourlyBookingInput().hours,1);
    e.el('hourly-start').value='2026-10-31T13:59';assert.throws(()=>c.hourlyBookingInput(),/quá khứ/);
    console.log('PASS hourly estimate rounds up, rejects overlaps/past times and permits adjacent bookings');

    e=setup();c=e.context;
    vm.runInContext('calendarReady=true; calendarRooms=[{roomId:1}]; bookingRows=[{bookingId:99}];',c);
    c.apiGet=async()=>{throw new Error('offline')};await c.loadCalendarData();
    assert.equal(vm.runInContext('bookingRows[0].bookingId',c),99);assert.equal(e.el('calendar-export').disabled,true);
    assert.match(e.el('calendar-error').textContent,/lần đồng bộ trước/);
    console.log('PASS sync failure retains last known data with explicit warning and disables stale CSV export');

    e=env('statistics');c=e.context;
    vm.runInContext(fs.readFileSync(path.join(root,'dashboard.js'),'utf8'),c);
    const data={rooms:[{roomId:1},{roomId:2}],bookings:[
        {roomId:1,status:'CheckedOut',checkinDate:'2026-10-30',checkoutDate:'2026-11-01'},
        {roomId:1,status:'Confirmed',checkinDate:'2026-10-31',checkoutDate:'2026-11-01'},
        {roomId:2,status:'Cancelled',checkinDate:'2026-10-31',checkoutDate:'2026-11-02'},
        {roomId:2,status:'CheckedIn',billingUnit:'Hour',checkinDate:'2026-10-31',checkoutDate:'2026-11-01'}
    ],invoices:[{paidAt:'2026-10-31 12:00:00',grandTotal:550000,roomTotal:500000},{paidAt:'2026-10-29',grandTotal:999999,roomTotal:999999}]};
    const result=c.dashboardPeriodMetrics(data,'2026-10-30','2026-11-01');
    assert.equal(result.revenue,550000);assert.equal(result.roomRevenue,500000);assert.equal(result.occupancy,50);
    assert.equal(result.count,4);assert.equal(result.cancelRate,25);
    console.log('PASS reporting uses actual paid dates, excludes cancelled/hourly occupancy and deduplicates room nights');

    console.log('Calendar/dashboard: 5 groups passed.');
}
run().catch(err=>{console.error(err);process.exitCode=1});
