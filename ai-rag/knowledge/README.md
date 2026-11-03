# Nguồn kiến thức Mochi

- `base.json`: hướng dẫn và FAQ đã đối chiếu với phần mềm.
- `published.json`: bộ tài liệu được nạp khi triển khai.
- `retrieval-cases.json`: dữ liệu kiểm tra truy xuất.
- `SnapshotCatalog.java`: công cụ đọc danh mục phòng/dịch vụ từ SQL Server.

Từ `backend`, sau khi build và chạy `scripts/security-classpath.ps1`:

```powershell
$ragClasspath = (Join-Path $PWD 'target/classes') + ';' + (Get-Content -Raw 'target/security-classpath.txt').Trim()
java '-Dfile.encoding=UTF-8' -cp $ragClasspath '../ai-rag/knowledge/SnapshotCatalog.java' | Set-Content -Encoding UTF8 '../ai-rag/data/catalog-rag-snapshot.json'
```

Từ root dự án, kiểm tra bản chụp trước khi nhập hoặc xuất bundle:

```powershell
python ai-rag/import-knowledge.py --catalog ai-rag/data/catalog-rag-snapshot.json
python ai-rag/check-knowledge.py --catalog ai-rag/data/catalog-rag-snapshot.json
python ai-rag/import-knowledge.py --catalog ai-rag/data/catalog-rag-snapshot.json --export-bundle ai-rag/knowledge/published.json
```

Importer sinh bản Markdown để rà soát. Chỉ thêm `--apply` khi muốn cập nhật
SQLite local; công cụ sao lưu trước khi nhập. Không thay bundle đã duyệt bằng
bản chụp cũ có mã phòng hoặc giá không còn đúng.

Danh mục trong tài liệu không chứng minh phòng còn trống. Lịch và giá thực tế
phải được kiểm tra qua backend. Chỉ nhập chính sách và thông tin đã xác nhận.
