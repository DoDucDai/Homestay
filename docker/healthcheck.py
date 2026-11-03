"""Process readiness without authentication or repeated SQL/database wakeups."""
import json
import os
import urllib.request

def main():
    port=os.getenv('PORT') or os.getenv('SERVER_PORT','8080')
    for url in ('http://127.0.0.1:'+port+'/api/health','http://127.0.0.1:8091/health'):
        with urllib.request.urlopen(url,timeout=3) as response:
            result=json.load(response)
            if url.endswith('/health') and result.get('success') is not True:return 1
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception:raise SystemExit(1)
