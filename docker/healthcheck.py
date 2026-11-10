# NOTE MOCHI: Kiểm tra readiness của Java/Python qua HTTP nội bộ, không tạo truy vấn SQL định kỳ chỉ để đánh thức database.
"""Process readiness without authentication or repeated SQL/database wakeups."""
import json
import os
import urllib.request

# NOTE MOCHI: Đọc cổng từ môi trường, yêu cầu cả /api/health và RAG /health trả success=true; lỗi trả mã thoát khác 0.
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
