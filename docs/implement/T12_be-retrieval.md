# T12 — Backend: truy hồi đa tài liệu

| | |
|---|---|
| **Giai đoạn / Loại** | 2 — BB-02 Hỏi đáp / Backend |
| **Ước tính** | 3 ngày |
| **Phụ thuộc** | T08 (dữ liệu đã lập chỉ mục) |
| **Mở khóa** | T13 |
| **Liên quan** | US-021 (AC-021.1/2), US-024 (AC-024.2), US-023 (AC-023.1 — nhánh "không tìm thấy" sớm); TH-04, TH-05, TH-06 |

## 1. Mục tiêu
Từ câu hỏi, lấy ra **một tập đoạn văn có thứ tự** từ **mọi** tài liệu sẵn sàng (không chỉ tài liệu giống nhất), kèm đủ thông tin để trích dẫn (tên tài liệu, trang, vị trí). Khi không có đoạn nào đủ liên quan, báo "rỗng" để trả lời "không tìm thấy" mà không tốn lượt gọi LLM.

## 2. Phạm vi
**Trong:** dịch vụ `Retriever`, truy vấn vector, chọn đoạn đa dạng theo tài liệu, ngưỡng điểm, công cụ dòng lệnh để chỉnh tham số.
**Ngoài:** tìm kiếm từ khóa/hybrid, rerank bằng mô hình, nhớ ngữ cảnh nhiều lượt (US-025).

## 3. Đặc tả chi tiết

### 3.1 Giao diện
```python
@dataclass
class ContextBlock:
    n: int                # 1..k, theo điểm giảm dần — đúng số dùng làm [n] trong câu trả lời
    document_id: UUID
    document_name: str
    page: int
    chunk_id: UUID
    char_start: int
    char_end: int
    text: str
    score: float          # độ tương đồng cosine 0..1

class Retriever:
    async def search(self, question: str) -> list[ContextBlock]: ...
```

### 3.2 Thuật toán
1. Chuẩn hóa câu hỏi (trim, gộp khoảng trắng); vector hóa bằng `EmbeddingPort.embed_query`.
2. Truy vấn pgvector, **chỉ** tài liệu `status='ready' AND deleted_at IS NULL`:
   ```sql
   SELECT c.id, c.document_id, d.name, c.page, c.char_start, c.char_end, c.text,
          1 - (c.embedding <=> :q) AS score
   FROM chunks c JOIN documents d ON d.id = c.document_id
   WHERE d.status = 'ready' AND d.deleted_at IS NULL
   ORDER BY c.embedding <=> :q LIMIT 30;
   ```
3. **Ngưỡng:** nếu điểm cao nhất `< RETRIEVAL_MIN_SCORE` (mặc định 0.30, **chỉnh bằng dữ liệu DEP-002**, T20) → trả danh sách **rỗng**.
4. **Chọn đa dạng:** từ 30 ứng viên (đã sắp theo điểm), lấy lần lượt cho đến khi đủ `RETRIEVAL_TOP_K` (mặc định 8), với **tối đa 4 đoạn mỗi tài liệu** — để câu hỏi cần ghép nhiều tài liệu không bị một tài liệu chiếm hết chỗ (US-021). Bỏ các ứng viên điểm `< 0.6 × điểm cao nhất` để không đưa đoạn nhiễu.
5. **Khử trùng lặp:** hai đoạn cùng tài liệu, cùng trang, chồng lấn vị trí > 50% → giữ đoạn điểm cao hơn.
6. Gán `n = 1…k` theo điểm giảm dần và trả về. Nếu tổng độ dài văn bản các đoạn vượt 24.000 ký tự (~6.000 token), cắt bớt từ đoạn điểm thấp nhất.

### 3.3 Truy hồi chéo ngôn ngữ
Việc hỏi tiếng Việt trên tài liệu tiếng Anh (và ngược lại) dựa hoàn toàn vào **mô hình embedding đa ngôn ngữ** (yêu cầu ở T08). Không dịch câu hỏi. Có một bài kiểm tra riêng (mục 5) để bảo đảm điều này.

### 3.4 Hiệu năng
Mục tiêu ≤ 300 ms với workspace đầy (20 tài liệu × 100 trang ≈ 6.000–10.000 chunk) — chỉ mục HNSW đã tạo ở T03. Ghi `retrieval_ms` vào log có cấu trúc (không kèm nội dung).

### 3.5 Công cụ dòng lệnh để chỉnh tham số
`python -m app.cli search "câu hỏi" [--k 8] [--min-score 0.3]` in ra danh sách (n, điểm, tài liệu, trang, 120 ký tự đầu của đoạn) — dùng khi chỉnh `TOP_K`/`MIN_SCORE` cùng T20. Không in toàn bộ văn bản vào log hệ thống.

## 4. Tệp sẽ tạo
`services/retrieval.py`, `cli.py` (lệnh `search`), `tests/test_retrieval_*.py`.

## 5. Tiêu chí hoàn thành
- [ ] Chỉ trả đoạn của tài liệu `ready` chưa xóa (test: thêm tài liệu `processing`/`failed`/xóa mềm có nội dung khớp → không xuất hiện).
- [ ] AC-021.2 (phần truy hồi): với hai tài liệu A và B cùng chứa thông tin cho một câu hỏi, kết quả có đoạn của **cả hai** (kể cả khi A có nhiều đoạn điểm cao hơn B); không tài liệu nào chiếm quá 4 đoạn.
- [ ] Câu hỏi hoàn toàn ngoài phạm vi ("Thủ đô của Pháp là gì?") → danh sách rỗng với bộ tài liệu mẫu (cần kiểm với embedding thật ở T20; với `FakeEmbedding` dùng dữ liệu được dựng để dưới ngưỡng).
- [ ] Workspace không có tài liệu `ready` → danh sách rỗng, không lỗi.
- [ ] `n` liên tục từ 1, đúng thứ tự điểm giảm dần; mỗi block có `page`, `char_start`, `char_end` khớp bản ghi chunk.
- [ ] Khử trùng lặp hoạt động (test đoạn chồng lấn).
- [ ] Tổng độ dài ngữ cảnh không vượt giới hạn 24.000 ký tự.
- [ ] **Chéo ngôn ngữ (cần embedding thật):** với một tài liệu tiếng Anh về "payment terms 30 days", câu hỏi tiếng Việt "thời hạn thanh toán" trả về đoạn đúng trong top-3; kết quả được ghi vào báo cáo T20.
- [ ] Đo trên workspace đầy (dữ liệu sinh): `retrieval_ms` p90 ≤ 300 ms (báo cáo số liệu trong PR).
- [ ] `cli search` chạy được và hiển thị đúng thông tin.

## 6. Kiểm thử
pytest + Postgres thật, vector dựng bằng `FakeEmbedding` có kiểm soát (đặt khoảng cách biết trước); test tham số `k`, ngưỡng, cap theo tài liệu, khử trùng lặp.

## 7. Ghi chú
`RETRIEVAL_MIN_SCORE` phụ thuộc mô hình embedding — **bắt buộc** hiệu chỉnh lại khi đổi mô hình. Đặt trong cấu hình, không hard-code. Tránh đặt quá cao: sẽ từ chối nhầm câu hỏi có đáp án (làm giảm TH-05).
