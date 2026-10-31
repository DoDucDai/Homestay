let dashboardDays=7,dashboardVersion=0,dashboardReady=false,dashboardData=null;
function dashboardAdd(date,days){return new Date(Date.parse(date+'T00:00Z')+days*86400000).toISOString().slice(0,10);}
function dashboardDate(date){return date.slice(8,10)+'/'+date.slice(5,7);}
function dashboardCompact(money){return new Intl.NumberFormat('vi-VN',{notation:'compact',maximumFractionDigits:1}).format(money);}
function dashboardInPeriod(date,start,end){return date && date.slice(0,10)>=start && date.slice(0,10)<end;}
function dashboardRangeBookings(rows,start,end){return rows.filter(b=>dashboardInPeriod(b.checkinDate,start,end));}
function dashboardPeriodMetrics(data,start,end){
    const invoices=data.invoices.filter(p=>dashboardInPeriod(p.paidAt,start,end));
    const arrivals=dashboardRangeBookings(data.bookings,start,end);
    // Occupancy is based on recorded nightly intervals, deduplicated per room/day.
    const sold=new Set();
    for(const b of data.bookings){
        if(b.billingUnit==='Hour' || b.status==='Cancelled')continue;
        let day=b.checkinDate>start?b.checkinDate:start;
        const last=b.checkoutDate<end?b.checkoutDate:end;
        for(;day<last;day=dashboardAdd(day,1))sold.add(b.roomId+'|'+day);
    }
    const days=(Date.parse(end)-Date.parse(start))/86400000;
    const count=arrivals.length,cancelled=arrivals.filter(b=>b.status==='Cancelled').length;
    return {invoices,revenue:invoices.reduce((sum,p)=>sum+p.grandTotal,0),
        roomRevenue:invoices.reduce((sum,p)=>sum+p.roomTotal,0),
        occupancy:data.rooms.length?sold.size/(data.rooms.length*days)*100:0,
        count,cancelled,cancelRate:count?cancelled/count*100:0,
        hourly:arrivals.filter(b=>b.billingUnit==='Hour' && b.status!=='Cancelled').length,
        paid:invoices.length};
}
function setDashboardDays(days){dashboardDays=days;loadDashboard();}
async function loadDashboard(){
    const version=++dashboardVersion;dashboardReady=false;
    const status=document.getElementById('dashboard-status');status.textContent='Đang cập nhật số liệu…';
    document.getElementById('dashboard-export').disabled=true;
    document.querySelectorAll('[data-days]').forEach(button=>{button.classList.toggle('active',Number(button.dataset.days)===dashboardDays);button.setAttribute('aria-pressed',String(Number(button.dataset.days)===dashboardDays));});
    try {
        const today=vietnamToday(),end=dashboardAdd(today,1),start=dashboardAdd(end,-dashboardDays),previous=dashboardAdd(start,-dashboardDays);
        const months=new Set();for(let day=previous;day<end;day=dashboardAdd(day,1))months.add(day.slice(0,7));
        const [rooms,bookings,...reports]=await Promise.all([apiGet('/rooms'),apiGet('/bookings'),...[...months].map(month=>apiGet('/statistics/revenue?month='+Number(month.slice(5,7))+'&year='+month.slice(0,4)))]);
        if(version!==dashboardVersion)return;
        const invoices=reports.flatMap(report=>expectList(report));
        for(const p of invoices)for(const key of ['roomTotal','serviceTotal','grandTotal'])if(!Number.isSafeInteger(p[key]) || p[key]<0)throw new Error('Số liệu thanh toán không hợp lệ.');
        const data={rooms:expectList(rooms),bookings:expectList(bookings),invoices,start,end,previous,today};
        data.current=dashboardPeriodMetrics(data,start,end);data.prior=dashboardPeriodMetrics(data,previous,start);
        dashboardData=data;dashboardReady=true;renderDashboard(data);
        document.getElementById('dashboard-export').disabled=false;status.textContent='';
    }catch(err){if(version!==dashboardVersion)return;status.textContent='Chưa tải được báo cáo mới. '+err.message+' Bấm lại khoảng ngày để thử lại.';}
}
function dashboardDelta(current,prior,points=false){
    if(!points && !prior)return current?'Chưa có số liệu kỳ trước':'Không đổi so với kỳ trước';
    const change=points?current-prior:(current-prior)/prior*100;
    return (change>0?'↑ +':change<0?'↓ ':'↔ ')+change.toLocaleString('vi-VN',{maximumFractionDigits:1})+(points?' điểm':'%')+' so với kỳ trước';
}
function renderDashboard(data){
    const {current:c,prior:p}=data;
    document.getElementById('dashboard-period').textContent=dashboardDate(data.start)+' – '+dashboardDate(data.today)+'/'+data.today.slice(0,4)+' · so sánh với '+dashboardDays+' ngày liền trước.';
    const kpis=document.getElementById('dashboard-kpis');kpis.replaceChildren();
    for(const [label,value,delta,help] of [
        ['Doanh thu đã thu',dashboardCompact(c.revenue),dashboardDelta(c.revenue,p.revenue),'VNĐ · gồm tiền phòng & dịch vụ'],
        ['Lượt đặt theo giờ',c.hourly,dashboardDelta(c.hourly,p.hourly),'Không gồm lượt đã hủy'],
        ['Tiền phòng đã thu',dashboardCompact(c.roomRevenue),dashboardDelta(c.roomRevenue,p.roomRevenue),'VNĐ · không gồm dịch vụ'],
        ['Lượt đặt trong kỳ',c.count,dashboardDelta(c.count,p.count),'Theo ngày nhận · gồm theo giờ'],
        ['Tỷ lệ hủy',c.cancelRate.toLocaleString('vi-VN',{maximumFractionDigits:1})+'%',dashboardDelta(c.cancelRate,p.cancelRate,true),c.cancelled+'/'+c.count+' lượt nhận trong kỳ']
    ]){
        const card=textNode('article','','card dashboard-kpi');
        card.append(textNode('p',label,'dashboard-kpi-label'),textNode('strong',value,'dashboard-kpi-value'),textNode('p',delta,'dashboard-kpi-delta'),textNode('p',help,'dashboard-kpi-help'));kpis.append(card);
    }
    renderDashboardChart(data);renderDashboardToday(data);renderDashboardDistributions(data);renderDashboardRooms(data);
}
function renderDashboardChart(data){
    const target=document.getElementById('daily-revenue-chart');target.replaceChildren();
    const list=[];for(let date=data.start;date<data.end;date=dashboardAdd(date,1)){
        const amount=data.current.invoices.filter(p=>p.paidAt.slice(0,10)===date).reduce((sum,p)=>sum+p.grandTotal,0);list.push({date,amount});
    }
    const max=Math.max(1,...list.map(p=>p.amount));
    const chart=textNode('div','','revenue-columns');chart.style.setProperty('--bar-count',list.length);
    for(const {date,amount} of list){
        const column=textNode('div','','revenue-column');
        const bar=textNode('div','','revenue-bar');bar.style.height=(amount/max*155)+'px';bar.title=dashboardDate(date)+': '+formatMoney(amount);
        const value=textNode('span',amount?dashboardCompact(amount):'0','revenue-bar-value');
        column.append(value,bar,textNode('span',dashboardDate(date),'revenue-bar-date'));chart.append(column);
    }
    target.append(chart,textNode('p','Cao nhất '+formatMoney(max===1?0:max)+' · Tổng kỳ '+formatMoney(data.current.revenue),'chart-caption'));
}
function renderDashboardToday(data){
    const counts=document.getElementById('dashboard-today-counts');counts.replaceChildren();
    const arriving=data.bookings.filter(b=>b.status==='Confirmed' && (b.checkinAt || b.checkinDate).slice(0,10)===data.today);
    const leaving=data.bookings.filter(b=>b.status==='CheckedIn' && (b.checkoutAt || b.checkoutDate).slice(0,10)<=data.today);
    const occupied=data.rooms.filter(r=>r.status==='Đang sử dụng').length;
    for(const [label,count] of [['Đến hôm nay',arriving.length],['Đang ở',occupied],['Cần trả phòng',leaving.length]]){
        const item=textNode('a','','today-count');item.href='bookings.html';item.append(textNode('span',label),textNode('strong',count));counts.append(item);
    }
    const attention=document.getElementById('dashboard-attention');attention.replaceChildren();
    for(const b of leaving.slice(0,2)){
        const link=textNode('a','','attention-item');link.href='checkout.html?bookingId='+b.bookingId;
        link.append(textNode('span',b.checkoutDate<data.today?'QUÁ NGÀY TRẢ':'TRẢ PHÒNG','attention-eyebrow'),textNode('strong',b.roomCode+' · '+b.customerName),textNode('small','Dự kiến trả '+dashboardDate(b.checkoutDate)+' · Kiểm tra lưu trú'));attention.append(link);
    }
    const maintenance=data.rooms.filter(room=>room.status==='Bảo trì');
    if(maintenance.length){const link=textNode('a','','attention-item');link.href='rooms.html';link.append(textNode('span','PHÒNG','attention-eyebrow'),textNode('strong',maintenance.length+' phòng đang bảo trì'),textNode('small','Chưa mở nhận đặt · Xem danh mục phòng'));attention.append(link);}
    if(arriving.length){const link=textNode('a','','attention-item');link.href='bookings.html';link.append(textNode('span','KHÁCH ĐẾN','attention-eyebrow'),textNode('strong',arriving.length+' lượt chờ nhận phòng hôm nay'));attention.append(link);}
    if(!attention.children.length)attention.append(textNode('p','Chưa có việc cần chú ý hôm nay.','text-muted'));
}
function dashboardProgress(target,label,count,total,color){
    const item=textNode('div','','distribution-item'),head=textNode('div','','distribution-label');
    head.append(textNode('span',label),textNode('strong',count+' · '+(total?count/total*100:0).toLocaleString('vi-VN',{maximumFractionDigits:1})+'%'));
    const track=textNode('div','','distribution-track'),bar=textNode('div','','distribution-fill');bar.style.width=(total?count/total*100:0)+'%';bar.style.background=color;track.append(bar);item.append(head,track);target.append(item);
}
function renderDashboardDistributions(data){
    const target=document.getElementById('dashboard-distribution');target.replaceChildren();
    for(const [status,label,color] of [['Trống','Còn trống','var(--color-success)'],['Đang sử dụng','Đang có khách','var(--color-primary)'],['Đã đặt','Đã đặt hiện tại','var(--color-accent)'],['Bảo trì','Bảo trì','var(--color-neutral)']])dashboardProgress(target,label,data.rooms.filter(r=>r.status===status).length,data.rooms.length,color);
    const billing=document.getElementById('dashboard-billing');billing.replaceChildren();
    const rows=dashboardRangeBookings(data.bookings,data.start,data.end).filter(b=>b.status!=='Cancelled');
    dashboardProgress(billing,'Theo giờ',rows.filter(b=>b.billingUnit==='Hour').length,rows.length,'var(--color-primary)');
    dashboardProgress(billing,'Theo đêm',rows.filter(b=>b.billingUnit!=='Hour').length,rows.length,'var(--color-accent)');
    billing.append(textNode('p','Các lượt đã xác nhận, đang ở và đã trả; không gồm lượt đã hủy.','dashboard-data-note'));
}
function renderDashboardRooms(data){
    const grouped=new Map();
    for(const inv of data.current.invoices){
        const booking=data.bookings.find(b=>b.roomCode===inv.roomCode);
        const code=inv.roomCode || booking?.roomCode || 'Chưa xác định';
        const current=grouped.get(code) || {code,name:booking?.roomName || '',count:0,room:0,total:0};
        current.count++;current.room+=inv.roomTotal;current.total+=inv.grandTotal;grouped.set(code,current);
    }
    const target=document.getElementById('dashboard-top-rooms');target.replaceChildren();
    const rows=[...grouped.values()].sort((a,b)=>b.room-a.room).slice(0,5);
    if(!rows.length){appendTableRow(target,['—','Chưa có hóa đơn đã thu trong kỳ','—','—','—']);}
    rows.forEach((row,i)=>{const room=textNode('div','','top-room-name');room.append(textNode('strong',row.name || row.code),textNode('span',row.code));appendTableRow(target,[i+1,room,row.count,formatMoney(row.room),formatMoney(row.total)]);});
    document.getElementById('dashboard-room-count').textContent=grouped.size+' phòng có doanh thu';
}
function exportDashboard(){
    if(!dashboardReady || !dashboardData)return;
    downloadCsv('bao-cao-'+dashboardData.start+'-'+dashboardData.today+'.csv',[
        {label:'Hóa đơn',value:p=>'HD'+p.paymentId},{label:'Ngày thu',value:p=>p.paidAt},
        {label:'Khách hàng',value:p=>p.customerName,text:true},{label:'Phòng',value:p=>p.roomCode,text:true},
        {label:'Tiền phòng (VND)',value:p=>p.roomTotal},{label:'Dịch vụ (VND)',value:p=>p.serviceTotal},{label:'Tổng đã thu (VND)',value:p=>p.grandTotal}
    ],dashboardData.current.invoices,true);
}
