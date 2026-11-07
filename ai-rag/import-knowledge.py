# NOTE MOCHI: Tạo tài liệu RAG từ hướng dẫn/danh mục đã duyệt và nhập SQLite có bảo toàn thay đổi của admin.
"""Publish reviewed local knowledge, preserving documents edited/removed by admins."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import sqlite3
from contextlib import closing

ROOT=Path(__file__).resolve().parent

# NOTE MOCHI: Băm tên/nội dung để biết bản nhập trước có bị admin thay đổi hay không.
def fingerprint(title,content):
    return hashlib.sha256((title+'\n'+content).encode('utf-8')).hexdigest()

# NOTE MOCHI: Ghép hướng dẫn và bản chụp danh mục thành tài liệu theo slug; ghi rõ thời điểm chụp và không dùng snapshot để khẳng định còn phòng.
def build_documents(catalog=None, captured=None):
    docs=[]
    for entry in json.loads((ROOT/'knowledge/base.json').read_text(encoding='utf-8')):
        content='\n'.join('Hỏi: '+q for q in entry['questions'])+'\nTrả lời: '+entry['answer']+'\nNguồn xác minh: '+entry['source']
        docs.append({'slug':'guide:'+entry['slug'],'title':entry['title'],'content':content})
    if catalog:
        stamp=captured or dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).isoformat(timespec='seconds')
        footer='\nBản chụp danh mục lúc '+stamp+'. Giá/trạng thái có thể thay đổi; kiểm tra lại hệ thống. Không xác nhận còn phòng bằng tài liệu này.'
        snapshot_label='Bản chụp dữ liệu lúc '+stamp+'. Không phải dữ liệu khả dụng thời gian thực.\n'
        def money(value):return format(value,',.0f').replace(',','.')
        for row in catalog['rooms']:
            code=row['room_code']
            content=(snapshot_label+f"Hỏi: Phòng {code} là phòng gì, giá bao nhiêu, có tiện nghi nào?\n"
                     f"Mã phòng: {code}. Tên phòng: {row['room_name']}. Loại: {row['room_type']}.\n"
                     +(f"Cơ sở: {row['property_name']}. Địa chỉ: {row['property_address']}.\n" if row.get('property_name') and row.get('property_address') else '')
                     +f"Giá niêm yết theo đêm tại thời điểm chụp: {money(row['price'])} đồng/đêm.\n"
                     +(f"Giá theo giờ: {money(row['hourly_price'])} đồng/giờ.\n" if row.get('hourly_price') else 'Chưa khai báo giá theo giờ; không suy từ giá đêm.\n')
                     +f"Mô tả lưu trong hệ thống: {row.get('description') or 'Chưa có mô tả'}.\n"
                     +'Chưa có trường sức chứa chuẩn. Chỉ nêu sức chứa và tiện nghi khi mô tả của đúng phòng ghi rõ. Không suy số khách tối đa hoặc chính sách từ tên/loại phòng.'+footer)
            docs.append({'slug':'room:'+str(row['room_id']),'title':f"Phòng {code} — {row['room_name']} ({row['room_type']}) | {stamp[:10]}",'content':content})
        for row in catalog['services']:
            content=(snapshot_label+f"Hỏi: Dịch vụ {row['service_name']} giá bao nhiêu và có hoạt động không?\n"
                     f"Mã dịch vụ: {row['service_id']}. Tên chính xác: {row['service_name']}.\n"
                     f"Đơn giá tại thời điểm chụp: {money(row['unit_price'])} đồng/đơn vị ghi nhận. Trạng thái: {row['status']}.\n"
                     +'Chưa có đơn vị suất/kg/ngày/chuyến được xác nhận; không tự suy đơn vị. Phải chọn đúng mã vì có dịch vụ trùng tên với giá khác. '
                     +('Dịch vụ Ngừng không được coi là đang nhận yêu cầu mới.' if row['status']=='Ngừng' else 'Kiểm tra lại trạng thái trước khi ghi nhận sử dụng.')+footer)
            docs.append({'slug':'service:'+str(row['service_id']),'title':f"Dịch vụ {row['service_name']} — mã {row['service_id']} | {stamp[:10]}",'content':content})
        types=sorted({r['room_type'] for r in catalog['rooms']})
        for kind in types:
            rows=[r for r in catalog['rooms'] if r['room_type']==kind]
            listing='; '.join(r['room_code']+': '+money(r['price'])+' đồng/đêm' for r in rows)
            docs.append({'slug':'room-type:'+kind,'title':f"Danh sách phòng {kind} và giá niêm yết | {stamp[:10]}",'content':snapshot_label+f"Hỏi: Có những phòng {kind} nào, mã nào và giá niêm yết bao nhiêu?\n"+listing+'. Chưa xác minh sức chứa. '+footer})
        overview='; '.join(kind+': '+', '.join(r['room_code'] for r in catalog['rooms'] if r['room_type']==kind) for kind in types)
        docs.append({'slug':'catalog:rooms','title':'Tổng hợp danh mục phòng đang giới thiệu | '+stamp[:10],'content':snapshot_label+'Hỏi: Danh mục có những loại phòng nào và bao nhiêu mã được giới thiệu?\n'+str(len(catalog['rooms']))+' mã trong bản chụp, đã loại phòng có trạng thái Bảo trì: '+overview+footer})
        listing='\n'.join(status+': '+ '; '.join(str(r['service_id'])+' — '+r['service_name'] for r in catalog['services'] if r['status']==status) for status in ['Hoạt động','Ngừng'])
        docs.append({'slug':'catalog:services','title':'Toàn bộ danh mục dịch vụ hoạt động và ngừng | '+stamp[:10],'content':snapshot_label+'Hỏi: Homestay có những dịch vụ gì? Dịch vụ nào đang hoạt động, dịch vụ nào ngừng?\n'+listing+'\nTên gần giống có thể là mã khác nhau; không gộp đơn giá. Kiểm tra trạng thái hiện tại trên trang Dịch vụ.'})
    slugs=set()
    for doc in docs:
        if doc['slug'] in slugs or not 0<len(doc['title'])<=150 or not 0<len(doc['content'])<=15000:
            raise ValueError('Invalid or duplicate document: '+doc['slug'])
        slugs.add(doc['slug'])
    return docs

# NOTE MOCHI: Thêm/cập nhật theo slug và hash; nếu admin đã sửa hoặc gỡ tài liệu thì giữ thay đổi đó thay vì ghi đè.
def publish(db_path,docs):
    summary={'inserted':0,'updated':0,'unchanged':0,'preserved_admin_changes':[]}
    with closing(sqlite3.connect(db_path,timeout=10)) as db, db:
        db.execute('CREATE TABLE IF NOT EXISTS rag_imports(slug TEXT PRIMARY KEY, document_id INTEGER NOT NULL, imported_hash TEXT NOT NULL)')
        for doc in docs:
            incoming=fingerprint(doc['title'],doc['content'])
            previous=db.execute('SELECT document_id,imported_hash FROM rag_imports WHERE slug=?',(doc['slug'],)).fetchone()
            if previous:
                current=db.execute('SELECT title,content FROM documents WHERE id=?',(previous[0],)).fetchone()
                if not current or fingerprint(*current)!=previous[1]:
                    summary['preserved_admin_changes'].append(doc['slug']);continue
                if incoming==previous[1]:summary['unchanged']+=1;continue
                db.execute('UPDATE documents SET title=?,content=?,version=version+1 WHERE id=?',(doc['title'],doc['content'],previous[0]))
                db.execute('UPDATE rag_imports SET imported_hash=? WHERE slug=?',(incoming,doc['slug']))
                summary['updated']+=1
            else:
                if db.execute('SELECT 1 FROM documents WHERE title=?',(doc['title'],)).fetchone():
                    summary['preserved_admin_changes'].append(doc['slug']);continue
                cursor=db.execute('INSERT INTO documents(title,content) VALUES (?,?)',(doc['title'],doc['content']))
                db.execute('INSERT INTO rag_imports VALUES (?,?,?)',(doc['slug'],cursor.lastrowid,incoming))
                summary['inserted']+=1
    return summary

# NOTE MOCHI: Đọc lựa chọn catalog/export/apply; tạo bản tài liệu xem trước, chỉ nhập kho thật khi có --apply và sao lưu trước.
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog',type=Path)
    parser.add_argument('--export-bundle',type=Path,help='Write the reviewed document bundle used by Docker startup.')
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    catalog=json.loads(args.catalog.read_text(encoding='utf-8-sig')) if args.catalog else None
    captured=dt.datetime.fromtimestamp(args.catalog.stat().st_mtime,dt.timezone(dt.timedelta(hours=7))).isoformat(timespec='seconds') if args.catalog else None
    docs=build_documents(catalog,captured)
    if args.export_bundle:
        args.export_bundle.parent.mkdir(parents=True,exist_ok=True)
        args.export_bundle.write_text(json.dumps({'schema':1,'catalogCapturedAt':captured,'documents':docs},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    export=ROOT/'knowledge/BO_TAI_LIEU_RAG.md'
    export.write_text('# Bộ tài liệu RAG đã đối chiếu hệ thống\n\n'+''.join('## '+d['title']+'\n\n'+d['content']+'\n\n' for d in docs),encoding='utf-8')
    print(json.dumps({'documents':len(docs),'preview':str(export),'applied':args.apply},ensure_ascii=False))
    if not args.apply:return
    target=ROOT/'data/assistant.sqlite3'
    if not target.exists():raise SystemExit('Start the AI service once to initialize its database first.')
    backup=ROOT/'data/backups'/('before-rag-import-'+dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'.sqlite3')
    backup.parent.mkdir(parents=True,exist_ok=True)
    with closing(sqlite3.connect(target)) as source,closing(sqlite3.connect(backup)) as destination:source.backup(destination)
    print(json.dumps({'backup':str(backup),**publish(target,docs)},ensure_ascii=False))

if __name__=='__main__':main()
