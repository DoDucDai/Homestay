"""Evaluate retrieval on a temporary database; never changes the live knowledge store."""
import argparse
import importlib.util
import json
from pathlib import Path
import uuid

ROOT=Path(__file__).resolve().parent
def module(name,file):
    spec=importlib.util.spec_from_file_location(name,file)
    obj=importlib.util.module_from_spec(spec);spec.loader.exec_module(obj);return obj

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog',type=Path,required=True)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    importer=module('knowledge_importer',ROOT/'import-knowledge.py')
    service=module('knowledge_service',ROOT/'app/server.py')
    docs=importer.build_documents(json.loads(args.catalog.read_text(encoding='utf-8-sig')),'snapshot-test')
    cases=json.loads((ROOT/'knowledge/retrieval-cases.json').read_text(encoding='utf-8'))
    results=[]
    temp=ROOT/'data'/('rag-qa-'+uuid.uuid4().hex)
    temp.mkdir(parents=True)
    try:
        service.DB=Path(temp)/'test.sqlite3';service.initialize();importer.publish(service.DB,docs)
        with service.connect() as db:
            ids={r[0]:r[1] for r in db.execute('SELECT slug,document_id FROM rag_imports')}
        for case in cases:
            found=service.retrieve(case['query'])
            expected={ids[slug] for slug in case['slugs']}
            results.append({**case,'hit':any(r['id'] in expected for r in found),'retrieved':[{'id':r['id'],'title':r['title']} for r in found]})
    finally:
        for file in temp.iterdir():file.unlink()
        temp.rmdir()
    report={'documents':len(docs),'cases':len(results),'hits_top4':sum(r['hit'] for r in results),'results':results,'scope':'retrieval only; not an LLM accuracy score'}
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='results'},ensure_ascii=False))
    for row in results:
        if not row['hit']:print(json.dumps(row,ensure_ascii=False))

if __name__=='__main__':main()
