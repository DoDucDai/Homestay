"""Run the Java web app and loopback-only Python RAG under one Render service."""
import importlib.util
import json
import os
from pathlib import Path
import shlex
import signal
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[1]

def load_module(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    obj=importlib.util.module_from_spec(spec);spec.loader.exec_module(obj);return obj

def environment(source):
    result=dict(source)
    port=result.get('PORT') or result.get('SERVER_PORT') or '8080'
    if not port.isdigit() or not 1<=int(port)<=65535 or int(port)==8091:
        raise ValueError('PORT/SERVER_PORT must be a valid port other than the internal RAG port 8091.')
    result['SERVER_PORT']=port
    result['SERVER_ADDRESS']='0.0.0.0'
    result['HOMESTAY_BACKEND_URL']='http://127.0.0.1:'+port
    result.setdefault('AI_RAG_DB_PATH','/var/lib/homestay/assistant.sqlite3')
    return result

def seed_knowledge():
    # Import checksums preserve admin edits/removals, including on later deploys.
    target=Path(os.environ['AI_RAG_DB_PATH'])
    bundle=json.loads((ROOT/'ai-rag/knowledge/published.json').read_text(encoding='utf-8'))
    docs=bundle['documents']
    if bundle.get('schema')!=1 or not isinstance(docs,list):raise ValueError('Invalid knowledge bundle.')
    seen=set()
    for doc in docs:
        if not all(isinstance(doc.get(k),str) for k in ('slug','title','content')) or doc['slug'] in seen:
            raise ValueError('Invalid knowledge document.')
        if not 0<len(doc['title'])<=150 or not 0<len(doc['content'])<=15000:raise ValueError('Knowledge document too large.')
        seen.add(doc['slug'])
    service=load_module('container_rag',ROOT/'ai-rag/app/server.py')
    service.initialize()
    importer=load_module('container_import',ROOT/'ai-rag/import-knowledge.py')
    result=importer.publish(target,docs)
    print('Knowledge bundle: added',result['inserted'],'updated',result['updated'],'preserved admin changes',len(result['preserved_admin_changes']),flush=True)

def main():
    os.environ.update(environment(os.environ))
    missing=[name for name in ('DB_HOST','DB_NAME','DB_USER','DB_PASSWORD') if not os.getenv(name)]
    if missing:raise ValueError('Missing database environment variables: '+', '.join(missing))
    seed_knowledge()
    children=[]
    stopping=False
    def stop(signum,frame):
        nonlocal stopping
        stopping=True
    signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGINT,stop)
    code=0
    try:
        children.append(subprocess.Popen([sys.executable,str(ROOT/'ai-rag/app/server.py')],cwd=ROOT))
        # Small defaults for a demo instance; override HOMESTAY_JAVA_OPTS if needed.
        options=shlex.split(os.getenv('HOMESTAY_JAVA_OPTS','-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError'))
        children.append(subprocess.Popen(['java',*options,'-jar',str(ROOT/'homestay.jar')],cwd=ROOT))
        while not stopping:
            if any(child.poll() is not None for child in children):
                print('A required service stopped; shutting down the container.',flush=True)
                code=1;break
            time.sleep(.5)
    finally:
        for child in children:
            if child.poll() is None:child.terminate()
        for child in children:
            try:child.wait(timeout=10)
            except subprocess.TimeoutExpired:child.kill();child.wait()
    return code

if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,OSError) as error:
        # Do not dump the environment, credentials or connection strings.
        print('Container startup failed:',str(error),file=sys.stderr)
        raise SystemExit(1)
