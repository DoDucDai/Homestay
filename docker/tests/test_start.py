import importlib.util
import os
from pathlib import Path
import sqlite3
import unittest
from unittest.mock import patch
import uuid

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('container_start',ROOT/'docker/start.py')
start=importlib.util.module_from_spec(spec);spec.loader.exec_module(start)

class ContainerStartTest(unittest.TestCase):
    def test_render_port_is_shared_with_loopback_rag(self):
        result=start.environment({'PORT':'10000','SERVER_PORT':'8080','DB_PASSWORD':'not-printed'})
        self.assertEqual(result['SERVER_PORT'],'10000')
        self.assertEqual(result['SERVER_ADDRESS'],'0.0.0.0')
        self.assertEqual(result['HOMESTAY_BACKEND_URL'],'http://127.0.0.1:10000')
        self.assertEqual(result['DB_PASSWORD'],'not-printed')

    def test_default_port_and_storage_override(self):
        self.assertEqual(start.environment({})['SERVER_PORT'],'8080')
        self.assertEqual(start.environment({'AI_RAG_DB_PATH':'/tmp/demo.sqlite3'})['AI_RAG_DB_PATH'],'/tmp/demo.sqlite3')

    def test_invalid_or_colliding_port_is_rejected(self):
        for port in ('0','65536','abc','8091'):
            with self.subTest(port=port),self.assertRaises(ValueError):start.environment({'PORT':port})

    def test_bundle_bootstrap_keeps_admin_edits_and_removals(self):
        temp=ROOT/'ai-rag/data'/('docker-test-'+uuid.uuid4().hex);temp.mkdir()
        target=temp/'assistant.sqlite3'
        try:
            with patch.dict(os.environ,{'AI_RAG_DB_PATH':str(target)}):
                start.seed_knowledge()
                db=sqlite3.connect(target)
                self.assertEqual(db.execute('SELECT COUNT(*) FROM documents').fetchone()[0],102)
                first,second=[r[0] for r in db.execute('SELECT document_id FROM rag_imports ORDER BY document_id LIMIT 2')]
                db.execute('UPDATE documents SET content=? WHERE id=?',('Admin changed this',first))
                db.execute('DELETE FROM documents WHERE id=?',(second,));db.commit();db.close()
                start.seed_knowledge()
                db=sqlite3.connect(target)
                self.assertEqual(db.execute('SELECT content FROM documents WHERE id=?',(first,)).fetchone()[0],'Admin changed this')
                self.assertEqual(db.execute('SELECT COUNT(*) FROM documents WHERE id=?',(second,)).fetchone()[0],0)
                db.close()
        finally:
            for file in temp.iterdir():file.unlink()
            temp.rmdir()

if __name__=='__main__':unittest.main()
