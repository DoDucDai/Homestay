const assert=require('node:assert/strict'),vm=require('node:vm');
const {env}=require('./frontend-test-env.cjs');
const e=env('explore');
const rooms=[{roomId:1,roomCode:'Room 401',roomName:'Existing',roomType:'VIP',price:400000,propertyId:1},
 {roomId:2,roomCode:'Room 104',roomName:'Japandi',roomType:'Double',price:400000,propertyId:2},
 {roomId:3,roomCode:'Room 501',roomName:'Champagne',roomType:'Double',price:400000,propertyId:3}];
assert.deepEqual(Array.from(e.context.filterExploreRooms(rooms,'','','code','2'),r=>r.roomCode),['Room 104']);
assert.deepEqual(Array.from(e.context.filterExploreRooms(rooms,'','','code','3'),r=>r.roomCode),['Room 501']);
e.context.setSelectedProperty(2);
assert.equal(e.context.propertyScopedEndpoint('/rooms?type=Double'),'/rooms?type=Double&propertyId=2');
assert.equal(e.context.propertyScopedEndpoint('/bookings'),'/bookings?propertyId=2');
assert.equal(e.context.propertyScopedEndpoint('/payments?bookingId=7'),'/payments?bookingId=7&propertyId=2');
assert.equal(e.context.propertyScopedEndpoint('/statistics/revenue?month=11&year=2026'),'/statistics/revenue?month=11&year=2026&propertyId=2');
assert.equal(e.context.propertyScopedEndpoint('/rooms/7'),'/rooms/7');
assert.equal(e.context.propertyScopedEndpoint('/auth/me'),'/auth/me');
assert.deepEqual(Array.from(e.context.propertyRooms(rooms),r=>r.roomId),[2]);
e.context.renderExploreDetail({...rooms[2],propertyName:'Cơ sở 3',propertyAddress:'789 Đường Bình Minh, TP. Hồ Chí Minh'});
assert.match(e.el('explore-book-link').href,/property=3/);
assert.equal(vm.runInContext("RoomPhotos.lookup({roomCode:'Room 104'}).number",e.context),'104');
assert.equal(vm.runInContext("RoomPhotos.lookup({roomCode:'Room 807'}).number",e.context),'807');
assert.equal(vm.runInContext("RoomPhotos.lookup({roomCode:'Room 408'})",e.context),null);
e.context.setSelectedProperty(null);
assert.equal(e.context.propertyScopedEndpoint('/rooms'),'/rooms');
console.log('PASS independent property catalogs, scoped room/booking/payment/report requests, correct booking property handoff, matching new posters and duplicate exclusion');
