import importlib.util
import json
import os
from contextlib import closing
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

    def test_render_email_environment_reaches_children_unchanged(self):
        email={
            'MAIL_PROVIDER':'brevo','MAIL_API_KEY':'test-only-key',
            'MAIL_FROM':'sender@example.org','MAIL_FROM_NAME':'DucDai',
            'EMAIL_VERIFICATION_ENABLED':'true','AUTH_TOKEN_SECRET':'test-only-secret',
            'APP_PUBLIC_URL':'https://frontend.example.org',
        }
        source={**email,'PORT':'10000','SPRING_PROFILES_ACTIVE':'prod'}
        result=start.environment(source)
        for key,value in email.items():self.assertEqual(result[key],value)
        self.assertEqual(result['SPRING_PROFILES_ACTIVE'],'prod')
        self.assertNotIn('SERVER_PORT',source)

    def test_invalid_or_colliding_port_is_rejected(self):
        for port in ('0','65536','abc','8091'):
            with self.subTest(port=port),self.assertRaises(ValueError):start.environment({'PORT':port})

    def test_bundle_bootstrap_keeps_admin_edits_and_removals(self):
        temp=ROOT/'ai-rag/data'/('docker-test-'+uuid.uuid4().hex);temp.mkdir(parents=True)
        target=temp/'assistant.sqlite3'
        try:
            with patch.dict(os.environ,{'AI_RAG_DB_PATH':str(target)}):
                start.seed_knowledge()
                bundle=json.loads((ROOT/'ai-rag/knowledge/published.json').read_text(encoding='utf-8'))
                with closing(sqlite3.connect(target)) as db:
                    self.assertEqual(db.execute('SELECT COUNT(*) FROM rag_imports WHERE document_id IN (SELECT id FROM documents)').fetchone()[0],len(bundle['documents']))
                    initial_count=db.execute('SELECT COUNT(*) FROM documents').fetchone()[0]
                    first,second=[r[0] for r in db.execute('SELECT document_id FROM rag_imports ORDER BY document_id LIMIT 2')]
                    db.execute('UPDATE documents SET content=? WHERE id=?',('Admin changed this',first))
                    db.execute('DELETE FROM documents WHERE id=?',(second,));db.commit()
                start.seed_knowledge()
                with closing(sqlite3.connect(target)) as db:
                    self.assertEqual(db.execute('SELECT content FROM documents WHERE id=?',(first,)).fetchone()[0],'Admin changed this')
                    self.assertEqual(db.execute('SELECT COUNT(*) FROM documents WHERE id=?',(second,)).fetchone()[0],0)
                    self.assertEqual(db.execute('SELECT COUNT(*) FROM documents').fetchone()[0],initial_count-1)
        finally:
            for file in temp.iterdir():file.unlink()
            temp.rmdir()

if __name__=='__main__':unittest.main()
