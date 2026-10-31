const assert=require('node:assert/strict');
const {env,payload}=require('./frontend-test-env.cjs');
const rooms=[
    {roomId:1,roomCode:'P101',roomName:payload,roomType:'Single',status:'Trống',price:300000,hourlyPrice:null,description:payload},
    {roomId:2,roomCode:'P102',roomName:'Double',roomType:'Double',status:'Đang sử dụng',price:500000,hourlyPrice:80000},
    {roomId:3,roomCode:'P103',roomName:'Maintenance',roomType:'Single',status:'Bảo trì',price:300000,hourlyPrice:null}
];
(async()=>{
    const e=env('landing');e.context.apiGet=async endpoint=>{assert.equal(endpoint,'/rooms');return rooms;};
    await e.context.loadLandingRooms();
    assert.equal(e.el('landing-total').textContent,'3');assert.equal(e.el('landing-free').textContent,'1');assert.equal(e.el('landing-types-count').textContent,'2');
    console.log('PASS landing statistics reflect actual inventory, availability and room types');

    e.context.apiGet=async()=>{throw Error('database unavailable')};await e.context.loadLandingRooms();assert.equal(e.el('landing-total').textContent,'3');assert.match(e.el('landing-sync').textContent,/lần tải trước/);
    console.log('PASS refresh failure retains last known inventory with an explicit stale-data status');
    console.log('Landing: 2 groups passed.');
})().catch(error=>{console.error(error);process.exitCode=1;});
