checkLogin();
let overviewVersion = 0, revenueVersion = 0, yearlyVersion = 0;
let revenueRows = [], revenueReady = false, revenuePeriod = null;
document.addEventListener('DOMContentLoaded',async () => {
    const user = await checkLogin();if (!user) return;
    if (user.role !== 'admin') { document.getElementById('statistics-denied').hidden = false; return; }
    document.getElementById('statistics-content').hidden = false;
    const today = vietnamToday();
    document.getElementById('stat-month').value = Number(today.slice(5,7));
    document.getElementById('stat-year').value = today.slice(0,4);
    document.getElementById('chart-year').value = today.slice(0,4);
    ['stat-month','stat-year'].forEach(id => document.getElementById(id).addEventListener('input',invalidateRevenueFilter));
    document.getElementById('chart-year').addEventListener('input',invalidateYearlyFilter);
    loadOverview();loadRevenue();loadYearly();
    if(typeof loadDashboard === 'function')loadDashboard();
});
function invalidateRevenueFilter() {
    revenueVersion++;revenueReady = false;revenueRows = [];revenuePeriod = null;
    document.getElementById('revenue-export').disabled = true;document.getElementById('revenue-total').textContent = '--';
    document.getElementById('revenue-status').textContent = 'Bộ lọc đã thay đổi. Bấm Xem tháng để tải kỳ mới.';
    tableState('revenue-tbody',8,'Bấm Xem tháng để tải dữ liệu theo bộ lọc mới.');
}
function invalidateYearlyFilter() {
    yearlyVersion++;document.getElementById('yearly-chart').replaceChildren();document.getElementById('yearly-total').textContent = '--';
    document.getElementById('yearly-status').textContent = 'Năm đã thay đổi. Bấm Xem năm để tải dữ liệu mới.';
    tableState('yearly-tbody',2,'Chưa tải năm mới.');
}
function svgNode(tag,attrs = {},content = '') {
    const node = document.createElementNS('http://www.w3.org/2000/svg',tag);
    Object.entries(attrs).forEach(([key,value]) => node.setAttribute(key,String(value)));
    node.textContent = String(content);return node;
}
function drawRevenueChart(amounts,year) {
    const svg = document.getElementById('yearly-chart');svg.replaceChildren();
    svg.append(svgNode('title',{},'Doanh thu đã thu năm '+year));
    const max = Math.max(1,...amounts);
    svg.append(svgNode('line',{x1:40,y1:240,x2:760,y2:240,stroke:'var(--color-text-muted)'}));
    amounts.forEach((amount,index) => {
        const height = amount/max*190,x = 50+index*59;
        const bar = svgNode('rect',{x,y:240-height,width:36,height,fill:'var(--color-primary)',rx:3});
        bar.append(svgNode('title',{},'Tháng '+(index+1)+': '+formatMoney(amount)));
        svg.append(bar,svgNode('text',{x:x+18,y:264,'text-anchor':'middle','font-size':14},index+1));
    });
    svg.append(svgNode('text',{x:40,y:22,'font-size':14},'Cao nhất: '+formatMoney(max === 1 && amounts.every(v=>v===0) ? 0 : max)));
}
function drawRoomChart(values) {
    const svg = document.getElementById('room-chart');svg.replaceChildren();
    svg.append(svgNode('title',{},'Trạng thái phòng hôm nay theo giờ Việt Nam'));
    const colors = ['var(--color-success)','var(--color-warning)','var(--color-primary)','var(--color-danger)'],total = values.reduce((a,b)=>a+b,0),radius = 65,circumference = 2*Math.PI*radius;
    svg.append(svgNode('circle',{cx:110,cy:110,r:radius,fill:'none',stroke:'var(--color-border)','stroke-width':32}));
    let offset = 0;
    values.forEach((value,index) => {
        if (!total || !value) return;
        const length = value/total*circumference;
        svg.append(svgNode('circle',{cx:110,cy:110,r:radius,fill:'none',stroke:colors[index],'stroke-width':32,
            'stroke-dasharray':length+' '+(circumference-length),'stroke-dashoffset':-offset,transform:'rotate(-90 110 110)'}));offset += length;
    });
    svg.append(svgNode('text',{x:110,y:116,'text-anchor':'middle','font-size':24},total));
}
async function loadOverview() {
    const version = ++overviewVersion,ids = ['stat-total-rooms','stat-total-customers','stat-total-bookings','stat-revenue'];
    ids.forEach(id => {document.getElementById(id).textContent = '--';});
    document.getElementById('overview-status').textContent = 'Đang tải tổng quan hiện tại…';
    document.getElementById('room-chart').replaceChildren();tableState('room-status-tbody',2,'Đang tải dữ liệu...');
    try {
        const data = await apiGet('/statistics/overview');if (version !== overviewVersion) return;
        for (const key of ['totalRooms','totalCustomers','totalBookingsThisMonth','revenueThisMonth','availableRooms','bookedRooms','occupiedRooms','maintenanceRooms']) {
            if (!Number.isSafeInteger(data[key]) || data[key] < 0) throw new Error('Dữ liệu thống kê không hợp lệ hoặc vượt giới hạn hiển thị chính xác.');
        }
        const values = [data.availableRooms,data.bookedRooms,data.occupiedRooms,data.maintenanceRooms];
        if (values.reduce((a,b)=>a+b,0) !== data.totalRooms) throw new Error('Tổng trạng thái phòng không khớp.');
        ids.forEach((id,index) => {document.getElementById(id).textContent = [data.totalRooms,data.totalCustomers,data.totalBookingsThisMonth,formatMoney(data.revenueThisMonth)][index];});
        const body = document.getElementById('room-status-tbody');body.replaceChildren();
        ['Trống','Đã đặt','Đang sử dụng','Bảo trì'].forEach((label,index) => appendTableRow(body,[textNode('span',label,'badge '+['badge-available','badge-booked','badge-occupied','badge-maintenance'][index]),values[index]]));
        drawRoomChart(values);
        document.getElementById('overview-status').textContent = 'Đã tải tổng quan hiện tại; doanh thu và booking theo tháng hiện tại, giờ Việt Nam.';
    } catch (err) {
        if (version !== overviewVersion) return;
        ids.forEach(id => {document.getElementById(id).textContent = '--';});
        document.getElementById('overview-status').textContent = err.message || 'Chưa xác định được số liệu tổng quan.';
        tableState('room-status-tbody',2,err.message || 'Không tải được thống kê.');showRequestError(err,'Không tải được thống kê.');
    }
}
function validReportYear(value) {
    const year = Number(value);if (!Number.isInteger(year) || year < 1 || year > 9999) throw new Error('Năm cần từ 1–9999.');return year;
}
async function loadRevenue() {
    const version = ++revenueVersion;revenueReady = false;revenueRows = [];
    document.getElementById('revenue-export').disabled = true;document.getElementById('revenue-total').textContent = '--';
    tableState('revenue-tbody',8,'Đang tải dữ liệu...');
    document.getElementById('revenue-status').textContent = 'Đang tải doanh thu tháng…';
    try {
        const month = Number(document.getElementById('stat-month').value),year = validReportYear(document.getElementById('stat-year').value);
        if (!Number.isInteger(month) || month < 1 || month > 12) throw new Error('Tháng cần từ 1–12.');
        const list = expectList(await apiGet(`/statistics/revenue?month=${month}&year=${year}`));if (version !== revenueVersion) return;
        let total = 0n;
        list.forEach(inv => {
            for (const key of ['roomTotal','serviceTotal','grandTotal']) if (!Number.isSafeInteger(inv[key]) || inv[key] < 0) throw new Error('Số tiền báo cáo không hợp lệ.');
            if (inv.grandTotal !== inv.roomTotal+inv.serviceTotal) throw new Error('Tổng hóa đơn không khớp.');total += BigInt(inv.grandTotal);
        });
        revenueRows = list;revenueReady = true;revenuePeriod = {month,year};renderRevenue(list);
        document.getElementById('revenue-total').textContent = formatMoney(total);document.getElementById('revenue-export').disabled = false;
        document.getElementById('revenue-status').textContent = list.length+' hóa đơn đã thanh toán · Tháng '+month+'/'+year+' · giờ Việt Nam.';
    } catch (err) {
        if (version !== revenueVersion) return;
        document.getElementById('revenue-status').textContent = err.message || 'Chưa xác định được doanh thu tháng.';
        tableState('revenue-tbody',8,err.message || 'Không tải được doanh thu.');showRequestError(err,'Không tải được doanh thu.');
    }
}
function renderRevenue(list) {
    const body = document.getElementById('revenue-tbody');body.replaceChildren();
    if (!list.length) {tableState('revenue-tbody',8,'Không có doanh thu trong tháng này');return;}
    pageRows('revenue',list,renderRevenue).forEach(inv => {
        const invoice = textNode('a','HD'+inv.paymentId);invoice.href = 'checkout.html?paymentId='+inv.paymentId;
        appendTableRow(body,[invoice,formatDate(inv.paidAt),inv.customerName || '',inv.roomCode || '',formatMoney(inv.roomTotal),formatMoney(inv.serviceTotal),inv.paymentMethod || '',formatMoney(inv.grandTotal)]);
    });
}
async function loadYearly() {
    const version = ++yearlyVersion;
    document.getElementById('yearly-chart').replaceChildren();document.getElementById('yearly-total').textContent = '--';
    document.getElementById('yearly-status').textContent = 'Đang tải doanh thu năm...';tableState('yearly-tbody',2,'Đang tải...');
    try {
        const year = validReportYear(document.getElementById('chart-year').value),amounts = expectList(await apiGet('/statistics/yearly?year='+year));
        if (version !== yearlyVersion) return;
        if (amounts.length !== 12 || !amounts.every(v=>Number.isSafeInteger(v) && v >= 0)) throw new Error('Doanh thu năm phải có 12 tháng hợp lệ trong giới hạn hiển thị chính xác.');
        const body = document.getElementById('yearly-tbody');body.replaceChildren();
        amounts.forEach((amount,index) => appendTableRow(body,[index+1,formatMoney(amount)]));drawRevenueChart(amounts,year);
        document.getElementById('yearly-total').textContent = formatMoney(amounts.reduce((sum,value)=>sum+BigInt(value),0n));
        document.getElementById('yearly-status').textContent = 'Doanh thu đã thanh toán năm '+year+' · giờ Việt Nam.';
    } catch (err) {
        if (version !== yearlyVersion) return;
        document.getElementById('yearly-status').textContent = err.message;tableState('yearly-tbody',2,err.message);
    }
}
function exportRevenue() {
    const samePeriod = revenuePeriod && revenuePeriod.month === Number(document.getElementById('stat-month').value) && revenuePeriod.year === Number(document.getElementById('stat-year').value);
    return downloadCsv('doanh-thu-'+(revenuePeriod?.year || '')+'-'+(revenuePeriod?.month || '')+'.csv',[
        {label:'Hóa đơn',value:p=>'HD'+p.paymentId},{label:'Ngày thu',value:p=>p.paidAt},
        {label:'Khách hàng',value:p=>p.customerName,text:true},{label:'Phòng',value:p=>p.roomCode,text:true},
        {label:'Tiền phòng (VND)',value:p=>p.roomTotal},{label:'Dịch vụ (VND)',value:p=>p.serviceTotal},
        {label:'Phương thức',value:p=>p.paymentMethod},{label:'Tổng (VND)',value:p=>p.grandTotal}
    ],revenueRows,revenueReady && samePeriod);
}
