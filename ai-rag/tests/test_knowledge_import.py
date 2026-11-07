import importlib.util
import json
from pathlib import Path
import sqlite3
import unittest
import uuid

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('knowledge_import',ROOT/'import-knowledge.py')
k=importlib.util.module_from_spec(spec);spec.loader.exec_module(k)
service_spec=importlib.util.spec_from_file_location('knowledge_retrieval',ROOT/'app/server.py')
service=importlib.util.module_from_spec(service_spec);service_spec.loader.exec_module(service)

class KnowledgeImportTest(unittest.TestCase):
    def setUp(self):
        self.temp=ROOT/'data'/('knowledge-test-'+uuid.uuid4().hex);self.temp.mkdir()
        self.db=self.temp/'test.sqlite3'
        with sqlite3.connect(self.db) as db:
            db.execute('CREATE TABLE documents(id INTEGER PRIMARY KEY,title TEXT,content TEXT,version INTEGER DEFAULT 1,updated_by INTEGER)')
        db.close()

    def tearDown(self):
        for file in self.temp.iterdir():file.unlink()
        self.temp.rmdir()

    def test_import_is_idempotent_and_updates_only_owned_content(self):
        docs=[{'slug':'a','title':'A','content':'initial'},{'slug':'b','title':'B','content':'original'}]
        self.assertEqual(k.publish(self.db,docs)['inserted'],2)
        self.assertEqual(k.publish(self.db,docs)['unchanged'],2)
        changed=[{**docs[0],'content':'new'},docs[1]]
        self.assertEqual(k.publish(self.db,changed)['updated'],1)
        with sqlite3.connect(self.db) as db:
            self.assertEqual(db.execute("SELECT version FROM documents WHERE title='A'").fetchone()[0],2)
        db.close()

    def test_admin_edits_removals_and_unrelated_documents_are_preserved(self):
        docs=[{'slug':'a','title':'A','content':'initial'},{'slug':'b','title':'B','content':'original'}]
        k.publish(self.db,docs)
        with sqlite3.connect(self.db) as db:
            db.execute("UPDATE documents SET content='admin revision' WHERE title='A'")
            db.execute("DELETE FROM documents WHERE title='B'")
            db.execute("INSERT INTO documents(title,content) VALUES ('Private custom document','untouched')")
        db.close()
        self.assertEqual(set(k.publish(self.db,docs)['preserved_admin_changes']),{'a','b'})
        with sqlite3.connect(self.db) as db:
            self.assertEqual(db.execute("SELECT content FROM documents WHERE title='A'").fetchone()[0],'admin revision')
            self.assertEqual(db.execute("SELECT COUNT(*) FROM documents WHERE title='B'").fetchone()[0],0)
            self.assertEqual(db.execute("SELECT content FROM documents WHERE title='Private custom document'").fetchone()[0],'untouched')
        db.close()

    def test_reviewed_guides_have_real_sources_and_valid_size(self):
        base=json.loads((ROOT/'knowledge/base.json').read_text(encoding='utf-8'))
        self.assertGreaterEqual(len(base),50)
        for row in base:
            self.assertTrue((ROOT.parent/row['source']).is_file(),row['source'])
        docs=k.build_documents()
        self.assertEqual(len({d['slug'] for d in docs}),len(docs))
        self.assertTrue(all(len(d['title'])<=150 and len(d['content'])<=15000 for d in docs))

    def test_catalog_is_timestamped_and_does_not_invent_hourly_or_capacity(self):
        catalog={'rooms':[{'room_id':1,'room_code':'P101','room_name':'A','room_type':'Single','price':300000,'hourly_price':None,'description':'Ban công'}],
                 'services':[{'service_id':21,'service_name':'Karaoke','unit_price':120000,'status':'Ngừng'}]}
        docs={d['slug']:d for d in k.build_documents(catalog,'2026-11-02T20:00:00+07:00')}
        room=docs['room:1']['content'];service=docs['service:21']['content']
        self.assertIn('300.000',room);self.assertIn('2026-11-02',room)
        self.assertIn('Chưa khai báo giá theo giờ',room);self.assertIn('Chưa có trường sức chứa',room)
        self.assertIn('Trạng thái: Ngừng',service);self.assertIn('không được coi là đang nhận yêu cầu mới',service)

    def test_complete_room_codes_are_not_conflated(self):
        catalog={'rooms':[
            {'room_id':1,'room_code':'P101','room_name':'Phòng Đơn Tầng 1','room_type':'Single','price':300000,'hourly_price':None,'description':'Ban công'},
            {'room_id':7,'room_code':'DEMO-P101','room_name':'Phòng Standard 101','room_type':'Single','price':400000,'hourly_price':None,'description':'Demo'}], 'services':[]}
        k.publish(self.db,k.build_documents(catalog,'2026-11-02T20:00:00+07:00'))
        with sqlite3.connect(self.db) as db:
            ids=dict(db.execute('SELECT slug,document_id FROM rag_imports'))
        db.close()
        previous=service.DB;service.DB=self.db
        try:
            normal={d['id'] for d in service.retrieve('Phòng P101 có tên gì và giá niêm yết mỗi đêm bao nhiêu?')}
            demo={d['id'] for d in service.retrieve('Giá phòng DEMO-P101 bao nhiêu?')}
            both={d['id'] for d in service.retrieve('So sánh P101 và DEMO-P101')}
            self.assertIn(ids['room:1'],normal);self.assertNotIn(ids['room:7'],normal)
            self.assertIn(ids['room:7'],demo);self.assertNotIn(ids['room:1'],demo)
            self.assertIn(ids['room:1'],both);self.assertIn(ids['room:7'],both)
        finally:service.DB=previous

if __name__=='__main__':unittest.main()
