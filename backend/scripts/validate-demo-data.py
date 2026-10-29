"""Validate the complete CSV contract before any database connection or write."""
from pathlib import Path
from collections import Counter, defaultdict
from datetime import date, datetime
import csv, json, re, sys

def validate(root):
    root=Path(root); manifest=json.loads((root/'demo-manifest.json').read_text(encoding='utf-8-sig'))
    cutoff=date.fromisoformat(manifest['cutoff']); data={}
    columns={
        'customers':'customer_code,full_name,document_type,document_number,phone,email,address',
        'services':'service_code,service_name,unit_label,unit_price,status',
        'rooms':'room_code,room_name,room_type,price,status,description',
        'bookings':'booking_code,customer_code,room_code,checkin_date,checkout_date,status,price_per_night,created_at,note',
        'booking_services':'booking_code,service_code,quantity,unit_price_at_booking',
        'payments':'booking_code,room_total,service_total,grand_total,payment_method,paid_at'}
    def require(ok,message):
        if not ok: raise ValueError(message)
    def number(value,positive=False):
        require(bool(re.fullmatch(r'[0-9]+',value)), 'Invalid integer: '+value)
        result=int(value);require((result>0 if positive else result>=0) and result<=999999999999,'Amount out of range');return result
    for name,header in columns.items():
        with (root/('hung' if name in ('customers','services') else 'long')/(name+'.csv')).open(encoding='utf-8-sig',newline='') as f:
            reader=csv.DictReader(f,strict=True);require(reader.fieldnames==header.split(','),name+': columns');data[name]=list(reader)
        require(all(None not in r and all(v is not None for v in r.values()) for r in data[name]),name+': malformed CSV')
        require(len(data[name])==manifest['counts'][name],name+': manifest count')
    for name,key,prefix,count in [('customers','customer_code','KH',50),('services','service_code','DV',12),('bookings','booking_code','BK',60)]:
        require(len(data[name])==count and {r[key] for r in data[name]}=={f'{prefix}{i:03}' for i in range(1,count+1)},name+': keys/count')
    for name,key in [('customers','document_number'),('services','service_name'),('rooms','room_code'),('payments','booking_code')]:
        require(len({r[key] for r in data[name]})==len(data[name]),name+': duplicate '+key)
    for r in data['customers']:
        require(bool(re.fullmatch(r'[0-9]{12}' if r['document_type']=='CCCD' else r'[A-Z0-9]{6,20}',r['document_number'])) and r['document_type'] in ('CCCD','PASSPORT'),'Invalid document')
        require(bool(re.fullmatch(r'0[0-9]{9}',r['phone'])),'Invalid phone')
        require(not r['email'] or bool(re.fullmatch(r'[^@\s]+@example\.com',r['email'])),'Non-demo email')
        require(0<len(r['full_name'])<=100 and len(r['email'])<=100 and len(r['address'])<=200,'Customer length')
    require(any(not r['email'] for r in data['customers']) and any(not r['address'] for r in data['customers']),'Missing optional blanks')
    require(any(n>1 for n in Counter(r['full_name'] for r in data['customers']).values()),'Missing duplicate-name example')
    for r in data['services']:
        number(r['unit_price']);require(r['status'] in ('Hoạt động','Ngừng') and 0<len(r['service_name'])<=100 and bool(r['unit_label']),'Invalid service')
    require(sum(r['status']=='Ngừng' for r in data['services'])>=2,'Need two stopped services')
    require(len(data['rooms'])==20 and {r['room_type'] for r in data['rooms']}=={'Single','Double','VIP','Family'},'Room count/types')
    require(sum(r['status']=='Bảo trì' for r in data['rooms'])==2,'Need two maintenance rooms')
    for r in data['rooms']:
        number(r['price'],True);require(r['status'] in ('Trống','Bảo trì') and len(r['room_code'])<=15 and 0<len(r['room_name'])<=100 and len(r['description'])<=500,'Room fields')
    customers={r['customer_code']:r for r in data['customers']};services={r['service_code']:r for r in data['services']};rooms={r['room_code']:r for r in data['rooms']};bookings={r['booking_code']:r for r in data['bookings']}
    require(Counter(r['status'] for r in data['bookings'])=={'CheckedOut':40,'Cancelled':10,'Confirmed':6,'CheckedIn':4},'Booking distribution')
    nights={}
    for r in data['bookings']:
        require(r['customer_code'] in customers and r['room_code'] in rooms,'Booking foreign key')
        ci=date.fromisoformat(r['checkin_date']);co=date.fromisoformat(r['checkout_date']);require(co>ci,'Stay dates');nights[r['booking_code']]=(co-ci).days
        require(datetime.strptime(r['created_at'],'%Y-%m-%d %H:%M:%S').date()<=ci,'Creation date')
        number(r['price_per_night'],True)
        require(r['status']!='CheckedOut' or co<=cutoff,'Future checked-out')
        require(r['status']!='Confirmed' or ci>cutoff,'Confirmed must be future')
        require(r['status']!='CheckedIn' or ci<=cutoff<co,'CheckedIn cutoff')
        require(not(r['status'] in ('Confirmed','CheckedIn') and rooms[r['room_code']]['status']=='Bảo trì'),'Maintenance booking')
    require(1 in nights.values() and max(nights.values())>1,'One/multiple nights required')
    require(any(n>1 for n in Counter(r['customer_code'] for r in data['bookings']).values()),'Returning guest required')
    require(any(n>1 for n in Counter(r['room_code'] for r in data['bookings']).values()),'Reused room required')
    for i,a in enumerate(data['bookings']):
        for b in data['bookings'][i+1:]:
            require(not(a['room_code']==b['room_code'] and a['status']!='Cancelled' and b['status']!='Cancelled' and a['checkin_date']<b['checkout_date'] and b['checkin_date']<a['checkout_date']),'Overlapping '+a['booking_code']+'/'+b['booking_code'])
    require(len(data['booking_services'])>=30,'Service row count');pairs=set();totals=defaultdict(int);paid_with_services=set()
    for r in data['booking_services']:
        require(r['booking_code'] in bookings and r['service_code'] in services,'Service foreign key');b=bookings[r['booking_code']];s=services[r['service_code']]
        require(b['status'] in ('CheckedOut','CheckedIn') and s['status']=='Hoạt động','Invalid service lifecycle')
        pair=(r['booking_code'],r['service_code']);require(pair not in pairs,'Duplicate service pair');pairs.add(pair)
        qty=number(r['quantity'],True);require(qty<=10000,'Quantity range');totals[r['booking_code']]+=qty*number(r['unit_price_at_booking'])
        if b['status']=='CheckedOut':paid_with_services.add(r['booking_code'])
    require(len(paid_with_services)>=20 and len(paid_with_services)<40,'Service/no-service paid examples')
    require(len(data['payments'])==40 and {r['booking_code'] for r in data['payments']}=={r['booking_code'] for r in data['bookings'] if r['status']=='CheckedOut'},'Payment coverage')
    monthly=defaultdict(int)
    for r in data['payments']:
        b=bookings[r['booking_code']];room=nights[r['booking_code']]*number(b['price_per_night'],True);service=totals[r['booking_code']]
        require([number(r[k]) for k in ('room_total','service_total','grand_total')]==[room,service,room+service],'Invoice mismatch '+r['booking_code'])
        paid=datetime.strptime(r['paid_at'],'%Y-%m-%d %H:%M:%S');require(paid.date()==date.fromisoformat(b['checkout_date']) and paid.date()<=cutoff,'Paid date')
        require(r['payment_method'] in ('Tiền mặt','Chuyển khoản'),'Payment method');monthly[paid.strftime('%Y-%m')]+=room+service
    expected_months={f'{cutoff.year+(cutoff.month-1-i)//12}-{(cutoff.month-1-i)%12+1:02}' for i in range(12)}
    require(set(monthly)==expected_months and len(set(monthly.values()))==12,'Twelve distinct monthly totals required')
    return {'manifest':manifest,'data':data,'monthlyTotals':dict(sorted(monthly.items())),'totalRevenue':sum(monthly.values())}

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    result=validate(sys.argv[1]);text=json.dumps(result,ensure_ascii=False,indent=2)
    if len(sys.argv)>2:Path(sys.argv[2]).write_text(text,encoding='utf-8')
    print(json.dumps({'counts':result['manifest']['counts'],'monthlyTotals':result['monthlyTotals'],'totalRevenue':result['totalRevenue']},ensure_ascii=False,indent=2))
