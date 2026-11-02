import importlib.util
import json
from pathlib import Path
import tempfile
import uuid
import threading
import unittest
import urllib.error
import urllib.request
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('service',Path(__file__).resolve().parents[1]/'app/server.py')
s=importlib.util.module_from_spec(spec)
spec.loader.exec_module(s)

class ServiceTest(unittest.TestCase):
    def setUp(self):
        self.temp=Path(__file__).resolve().parents[1]/'data'/('test-'+uuid.uuid4().hex)
        self.temp.mkdir(parents=True)
        s.DB=self.temp/'test.db'
        s.initialize()
        s.LIMITS.clear()
        self.server=s.ThreadingHTTPServer(('127.0.0.1',0),s.Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True)
        self.thread.start()
        self.uid,self.admin=1,False
        self.auth=True
        def backend(handler,path):
            if not self.auth: raise urllib.error.HTTPError(path,401,'Unauthorized',{},None)
            if path=='/auth/me':return {'user':{'userId':self.uid,'role':'admin' if self.admin else 'staff'}}
            if path=='/auth/csrf':return {'token':'test-csrf'}
            if path.startswith('/bookings/available-rooms'):return [{'roomId':1,'roomCode':'101','price':100,'secret':'hidden'}]
            raise AssertionError('Unexpected backend access: '+path)
        self.backend=patch.object(s.Handler,'backend',backend)
        self.backend.start()
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.backend.stop()
        for file in self.temp.iterdir():file.unlink()
        self.temp.rmdir()
    def call(self,path,body=None,csrf='test-csrf'):
        req=urllib.request.Request('http://127.0.0.1:'+str(self.server.server_port)+path,
          data=None if body is None else json.dumps(body).encode(),
          headers={'Content-Type':'application/json','X-CSRF-TOKEN':csrf})
        try:
            with urllib.request.urlopen(req) as r:return r.status,json.load(r)
        except urllib.error.HTTPError as e:return e.code,json.load(e)
    def test_auth_and_csrf(self):
        self.auth=False
        self.assertEqual(self.call('/support')[0],401)
        self.auth=True
        self.assertEqual(self.call('/support',{'subject':'a','message':'b'},csrf='bad')[0],403)
    def test_spring_xor_csrf_masks(self):
        raw=b'6b86b273-ff34-4fce-a94d-0c39ab893bed'
        def masked(pad):
            mask=bytes([pad])*len(raw)
            return s.base64.urlsafe_b64encode(mask+bytes(a^b for a,b in zip(mask,raw))).decode()
        self.assertNotEqual(masked(1),masked(2))
        self.assertEqual(s.csrf_value(masked(1)),s.csrf_value(masked(2)))
        self.assertNotEqual(s.csrf_value(masked(1)),s.csrf_value('bad'))
    def test_support_owner_admin_notification_reply(self):
        status,result=self.call('/support',{'subject':'Help','message':'Need help'})
        self.assertEqual(status,200)
        ticket=result['id']
        self.uid=2
        self.assertEqual(self.call('/support')[1]['tickets'],[])
        self.assertEqual(self.call('/support/reply',{'id':ticket,'reply':'ok'})[0],403)
        self.admin=True
        self.assertEqual(self.call('/support')[1]['unread'],1)
        self.assertEqual(self.call('/support/reply',{'id':ticket,'reply':'Resolved'})[0],200)
        self.assertEqual(self.call('/support')[1]['unread'],0)
        self.uid,self.admin=1,False
        self.assertEqual(self.call('/support')[1]['tickets'][0]['reply'],'Resolved')
    def test_documents_permissions_retrieval_and_removal(self):
        self.assertEqual(self.call('/documents')[0],403)
        self.admin=True
        self.call('/documents',{'title':'Pool','content':'Unique swimming pool information'})
        docs=self.call('/documents')[1]['documents']
        doc=next(d for d in docs if d['title']=='Pool')
        self.assertTrue(s.retrieve('swimming'))
        self.call('/documents',{'id':doc['id'],'title':'Pool','content':'Updated pool hours'})
        self.assertFalse(s.retrieve('swimming'))
        self.call('/documents',{'id':doc['id'],'delete':True})
        self.assertFalse(s.retrieve('pool'))
    def test_chat_dates_sources_and_minimum_room_data(self):
        with patch.object(s,'answer',return_value='Test answer') as model:
            status,result=self.call('/chat',{'message':'chính sách đặt cọc'})
            self.assertEqual(status,200);self.assertTrue(result['sources']);self.assertIsNone(result['rooms'])
            self.assertEqual(self.call('/chat',{'message':'rooms','checkin':'bad','checkout':'bad'})[0],400)
            start=s.dt.date.today()+s.dt.timedelta(days=30)
            status,result=self.call('/chat',{'message':'rooms','checkin':str(start),'checkout':str(start+s.dt.timedelta(days=1))})
            self.assertEqual(status,200);self.assertNotIn('secret',result['rooms'][0])
            self.assertEqual(model.call_count,2)
    def test_model_failure_does_not_fabricate_answer(self):
        with patch.object(s,'answer',side_effect=s.Problem(503,'Unavailable')):
            self.assertEqual(self.call('/chat',{'message':'test'})[0],503)
    def test_chat_rate_limit(self):
        with patch.object(s,'answer',return_value='ok'):
            for _ in range(5):self.assertEqual(self.call('/chat',{'message':'test'})[0],200)
            self.assertEqual(self.call('/chat',{'message':'test'})[0],429)

if __name__=='__main__':unittest.main()
