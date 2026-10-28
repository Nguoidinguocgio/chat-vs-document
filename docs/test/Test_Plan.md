# Kế hoạch kiểm thử (Test Plan) — Chat với tài liệu (MVP lõi)

> **Mã tài liệu:** TP-CVD-001 · **Phiên bản:** 0.1 (Draft) · **Ngày:** 2026-10-28
> **Chuẩn:** IEEE 829 (đối chiếu từng mục ở [Phụ lục A](#phụ-lục-a--đối-chiếu-checklist-ieee-829))
> **Căn cứ:** [Implement Plan](../implement/implement_plan.md) · [Danh sách task T01–T23](../implement/README.md) · [User stories BB-01/BB-02](../user_stories/US_Backbone_Upload_va_HoiDap.md) · [PRD](../requirements/PRD.md) · [SDD UI/UX](../design/SDD_UIUX_Blueprint.md)
> **Quy ước:** `[Đề xuất]` = giả định của người soạn, cần xác nhận; `[TBD]` = chưa có thông tin.

---

## 1. Định danh và giới thiệu

### 1.1 Định danh

| Thuộc tính | Giá trị |
|---|---|
| Mã kế hoạch | **TP-CVD-001** |
| Phiên bản | 0.1 (Draft) — mọi thay đổi ghi ở mục 1.3 và commit cùng thay đổi phạm vi |
| Vị trí | `docs/test/Test_Plan.md` (version-controlled trong repo) |
| Cấp độ | **Kế hoạch kiểm thử chủ đạo (Master Test Plan)** cho MVP lõi; chi tiết thực thi nằm ở task T20, T21, T22 |

### 1.2 Giới thiệu

**Sản phẩm:** ứng dụng web cho phép một người dùng đã đăng nhập tải PDF lên workspace chung, hỏi bằng tiếng Việt/Anh và nhận câu trả lời **có trích dẫn tên tài liệu + số trang** kiểm chứng được; khi tài liệu không có căn cứ thì từ chối thay vì bịa.

**Mục đích kế hoạch này:** xác định *kiểm thử cái gì, bằng cách nào, đạt khi nào, ai làm, khi nào* để có bằng chứng cho các cổng phát hành G-01…G-14 của PRD. Phạm vi sản phẩm chỉ gồm hai chức năng xương sống — **BB-01 Tải lên PDF** và **BB-02 Hỏi đáp có trích nguồn** — cùng phần tối thiểu để chạy được (đăng nhập, phiên).

**Điểm khó của kiểm thử sản phẩm này:** một phần kết quả do LLM sinh ra, không xác định. Vì vậy kế hoạch tách làm hai mảng:

| Mảng | Kiểm cái gì | Dùng LLM thật? |
|---|---|---|
| **Kiểm thử phần mềm** (unit, tích hợp, E2E, tải, bảo mật) | Logic, giao thức, giao diện, hiệu năng — lặp lại được | Không — dùng `FakeLLM` / `FakeEmbedding` |
| **Đánh giá chất lượng AI** (DEP-002) | Đúng trang, từ chối đúng, không bịa, chống injection | Có — đo bằng bộ câu hỏi chuẩn, tính tỷ lệ |

### 1.3 Lịch sử thay đổi

| Phiên bản | Ngày | Nội dung |
|---|---|---|
| 0.1 | 2026-10-28 | Bản nháp đầu, soạn khi T01–T16 đã triển khai |

---

## 2. Phạm vi

### 2.1 Hạng mục kiểm thử (Test Items)

Trạng thái triển khai tại thời điểm soạn: **T01–T16 đã hoàn thành**, T17–T19 đang chờ, T20–T23 chưa bắt đầu.

| Mã | Hạng mục | Thành phần | Task nguồn | Trạng thái |
|---|---|---|---|---|
| TI-01 | Khung repo, CI, luật ranh giới module R-1…R-6 | `.github/`, lint | T01 | Đã có |
| TI-02 | Thư viện UI và design tokens | `web/src/shared/ui` | T02 | Đã có |
| TI-03 | Nền tảng backend, lược đồ CSDL, migration | `api/` (Flyway, JPA) | T03 | Đã có |
| TI-04 | Đăng nhập, phiên 8 giờ, khóa tạm khi sai 5 lần | `api/service/auth` | T04 | Đã có |
| TI-05 | Khung ứng dụng FE, router + guard, API client | `web/src/app`, `shared/api` | T05 | Đã có |
| TI-06 | Hợp đồng OpenAPI + API giả MSW | `contracts/openapi.yaml`, `web/src/mocks` | T06 | Đã có |
| TI-07 | API tải lên `POST /documents` | `api/web`, `service/documents` | T07 | Đã có |
| TI-08 | Pipeline xử lý PDF (PDFBox, 7 loại lỗi + cảnh báo) | `api/service/pdf` | T08 | Đã có |
| TI-09 | `GET /documents`, SSE `/events`, analytics EV-010…013 | `api/service/events`, `analytics` | T09 | Đã có |
| TI-10 | FE tải lên: ALG-02, kéo-thả, hàng đợi | `web/src/features/documents/upload` | T10 | Đã có |
| TI-11 | FE bảng Tài liệu + đồng bộ thời gian thực (ALG-03) | `web/src/features/documents`, `shared/realtime` | T11 | Đã có |
| TI-12 | Truy hồi đa tài liệu (top-k, pgvector) | `api/service/retrieval` | T12 | Đã có |
| TI-13 | Sinh câu trả lời có trích dẫn, chống injection | `api/service/answer` | T13 | Đã có |
| TI-14 | API chat SSE `POST /conversations/:id/messages` | `api/service/chat` | T14 | Đã có |
| TI-15 | FE Composer + bố cục chat (ST-04) | `web/src/features/chat` | T15 | Đã có |
| TI-16 | FE render streaming + chip trích dẫn (ALG-01) | `web/src/features/chat`, `shared/lib/{sse,markdown}` | T16 | Đã có |
| TI-17 | Trích dẫn, tải file (Range), Dừng/Tạo lại | `api` | T17 | Chờ |
| TI-18 | FE Xem nguồn (pdf.js, nổi bật đoạn) | `web/src/features/source-viewer` | T18 | Chờ |
| TI-19 | FE Dừng/thử lại, lỗi, offline | `web` | T19 | Chờ |
| TI-20 | Bản build staging (tích hợp toàn bộ) | `ops/` | T23 | Chờ |

### 2.2 Tính năng cần kiểm thử

Gộp theo tiêu chí chấp nhận P0 của PRD (giữ nguyên ID để truy vết):

| Nhóm | Tính năng | AC / chỉ tiêu | Ưu tiên |
|---|---|---|---|
| **Xác thực** | Đăng nhập đúng/sai, khóa 15 phút sau 5 lần sai, mật khẩu tạm, hết phiên, chuyển hướng khi chưa đăng nhập | AC-001.1 – 001.6, AC-003.1 | P0 |
| **BB-01 Tải lên** | Kiểm tra file phía client (đuôi + `%PDF-` + ≤ 10 MB + chỗ trống) | AC-010.4/5, AC-012.1/2, ALG-02 | P0 |
| | Tải nhiều file, tiến trình riêng, lỗi một file không dừng file khác | AC-010.3 | P0 |
| | Giới hạn 20 tài liệu, giữ chỗ nguyên tử khi hai người cùng tải | AC-010.4, PRD §5.2 | P0 |
| | Xử lý nền → Sẵn sàng ≤ 60 s p90 (100 trang) | AC-010.1, TH-03 | P0 |
| | 7 loại lỗi + cảnh báo trang không đọc được | AC-012.1 – 012.8 | P0 |
| | Danh sách tài liệu, bộ đếm x/20, đồng bộ ≤ 5 s giữa hai phiên | AC-011.1 – 011.3 | P0 |
| | Mất mạng khi tải: Gián đoạn → thử lại 1/2/4 s → nút Thử lại | PRD §5.2 | P0 |
| **BB-02 Hỏi đáp** | Streaming, token đầu ≤ 3 s, hoàn chỉnh ≤ 15 s (p90) | AC-020.1, TH-01/02 | P0 |
| | Khóa ô nhập khi chưa có tài liệu Sẵn sàng; đếm 2.000 ký tự; Enter/Shift+Enter; IME | AC-020.3/4, ST-04 | P0 |
| | Tổng hợp đa tài liệu, nêu mâu thuẫn | AC-021.1 – 021.3 | P0 |
| | Trích dẫn `[n]` + danh sách nguồn; đúng trang | AC-022.1 – 022.3, TH-04 | P0 |
| | Từ chối khi không có căn cứ; không dùng kiến thức ngoài; trả lời một phần | AC-023.1 – 023.3, TH-06 | P0 |
| | Trả lời theo ngôn ngữ câu hỏi, hỏi chéo ngôn ngữ | AC-024.1/2 | P0 |
| | Dừng ≤ 1 s, Tạo lại | US-026 | P1 |
| | Xem đoạn gốc ≤ 1,5 s, tài liệu đã xóa | AC-030.1 – 030.3 | P1 |
| | Gián đoạn / quá hạn 30 s / AI không khả dụng | PRD §5.2, ST-02 | P0 |
| **Xuyên suốt** | Render an toàn nội dung LLM/PDF (XSS, injection) | NFR-S05, R-4 | P0 |
| | Phân quyền, cô lập hội thoại giữa người dùng | NFR-S04, G-07 | P0 |
| | Analytics EV-010…EV-030, không chứa nội dung nghiệp vụ | G-10 | P0 |
| | Khả năng tiếp cận: bàn phím, WCAG 2.1 AA, NVDA | NFR-U03 | P0 |
| | Hiệu năng và dung lượng: 20 × 100 trang, ≥ 10 người đồng thời; LCP ≤ 2 s | TH-01/02/03/08, NFR-C01 | P0 |

### 2.3 Tính năng không kiểm thử (Features Not to be Tested)

| Hạng mục | Lý do |
|---|---|
| Quản trị người dùng đầy đủ (US-002 – 005); xóa / tóm tắt / phát hiện trùng tài liệu (US-013 – 015) | Ngoài phạm vi implement plan §9. **Lưu ý:** US-002/003, US-050, US-061 là P0 trước launch — cần test plan bổ sung sau GĐ 4 |
| Hội thoại tiếp nối, lịch sử (US-025, US-040/041); đánh giá 👍/👎 (US-051); báo cáo tuần (US-050) | Như trên; prototype có hiển thị nhưng không thuộc lát cắt xương sống |
| OCR / PDF bản scan | Quyết định D-008: không hỗ trợ — chỉ kiểm thử việc **từ chối đúng** |
| Độ khả dụng và độ chính xác nội bộ của dịch vụ bên thứ ba (Anthropic API, mô hình `bge-m3`) | Ngoài kiểm soát; chỉ kiểm thử cách hệ thống **xử lý khi chúng lỗi/chậm** (T10 trong BB-02) |
| Trình duyệt ngoài Chrome/Edge (Firefox, Safari); thiết bị di động / màn hình < 1280 px | NFR-U01 chỉ cam kết Chrome, Edge ≥ 1280 px; màn hình nhỏ chỉ kiểm tra "hiển thị được" |
| Kiểm thử xâm nhập chuyên sâu bởi bên thứ ba | Lên kế hoạch riêng nếu An toàn thông tin yêu cầu (T22 §2) |
| Hạ tầng production (mạng, HA, DR) | Ngoài phạm vi MVP; chỉ kiểm sao lưu / rollback ở T23 |
| Tính đúng đắn tuyệt đối của mọi câu trả lời | Không thể kiểm thử toàn bộ; chất lượng AI đo bằng **tỷ lệ** trên DEP-002 |

---

## 3. Cách tiếp cận và phương pháp

### 3.1 Chiến lược kiểm thử (Testing Approach)

| Mức | Mục tiêu | Công cụ | Người viết | Chạy ở đâu |
|---|---|---|---|---|
| **Unit — BE** | Logic nghiệp vụ: kiểm tra file, phân quyền, xử lý tài liệu, ghép trích dẫn; độ phủ dòng ≥ 70% (G-09) | JUnit 5, Mockito | BE (kèm task) | CI mỗi commit |
| **Unit/Component — FE** | Store, composable, thuật toán ALG-01/02/03, component | Vitest, Vue Test Utils | FE (kèm task) | CI mỗi commit |
| **Tích hợp BE** | Repository + pgvector + Flyway + pipeline PDF với PostgreSQL thật | Testcontainers (≥ 1.21.4) | BE | CI mỗi PR |
| **Hợp đồng API** | `openapi.yaml` hợp lệ; BE và MSW cùng tuân thủ; định dạng lỗi duy nhất | Redocly lint, kiểm thử hợp đồng | FE + BE | CI mỗi PR |
| **Lint kiến trúc** | Luật R-1…R-6: `fetch` chỉ ở `shared/api`; nội dung LLM chỉ qua `shared/lib/markdown`; không `v-html`; không token ở `localStorage` | ESLint (luật tùy biến), ArchUnit | FE + BE | CI mỗi commit |
| **E2E** | Mọi luồng P0 trên Chrome + Edge; 22 kịch bản E-01…E-22 | Playwright, backend thật + `FakeLLM`/`FakeEmbedding` | QA | CI mỗi PR; Edge có thể chạy đêm nhưng phải xanh trước launch |
| **Khả năng tiếp cận** | 0 lỗi serious/critical; bàn phím; NVDA | axe-core, Playwright, NVDA thủ công | QA | CI + thủ công 1 lần/GĐ |
| **Đánh giá chất lượng AI** | TH-04…TH-07, AC-024.2, injection | `eval/run.py` trên DEP-002 + chuyên gia chấm | QA + Đội PTKD | Sau mỗi thay đổi prompt/truy hồi; hằng tuần sau launch |
| **Thử tải & hiệu năng** | L-1…L-5 (TH-01/02/03/08, NFR-C01) | k6, Lighthouse CI | QA | Staging, GĐ 4 |
| **Bảo mật** | OWASP Top 10, phân quyền chéo, XSS, injection, upload độc hại | OWASP ZAP, `npm audit`, dependency-check, gitleaks, thủ công S-01…S-13 | QA + ATTT | Staging, GĐ 4 |
| **Chấp nhận (UAT)** | ≥ 4/5 người hoàn thành "tải lên – hỏi – kiểm chứng" không hỗ trợ (G-12) | Kịch bản UAT, quan sát | PO + Đội PTKD | Staging, cuối GĐ 4 |

**Nguyên tắc xuyên suốt:**

1. **Xác định trước, ngẫu nhiên sau:** mọi test tự động dùng `FakeLLM`/`FakeEmbedding`, có kịch bản cố định (`ngat`, `cham`, `loi dich vu`, `injection`…). LLM thật chỉ dùng ở đánh giá AI và S-06.
2. **So khớp nguyên văn thông điệp** theo PRD §4.3 / SDD 3.5.3 — không kiểm "chứa từ khóa".
3. **Kiểm thử theo lát cắt dọc:** mỗi giai đoạn kết thúc bằng một luồng E2E chạy được, không dồn tích hợp về cuối.
4. **Không hạ ngưỡng để đạt:** nếu chưa đạt, ghi điểm nghẽn và đề xuất (giảm `TOP_K`, đổi mô hình, tối ưu truy vấn).
5. **Hồi quy AI bắt buộc:** mỗi thay đổi prompt (T13) / tham số truy hồi (T12) kèm báo cáo DEP-002 trong PR; không chỉ tiêu nào giảm quá 2 điểm % nếu không có lý do được duyệt.
6. **Không rò dữ liệu nghiệp vụ:** câu hỏi, câu trả lời, văn bản PDF không xuất hiện trong log / analytics / công cụ báo lỗi (kiểm bằng S-10).

### 3.2 Tự động hóa và kiểm thử thủ công

| Loại | Tỷ trọng [Đề xuất] | Nội dung |
|---|---|---|
| **Tự động trong CI** | ~90% số ca | Unit, tích hợp, hợp đồng, lint, E2E E-01…E-22, axe-core, quét phụ thuộc/bí mật, ZAP baseline |
| **Tự động theo yêu cầu / lịch** | — | `eval/run.py` (DEP-002), k6, ZAP active scan, Lighthouse CI, Edge E2E hằng đêm |
| **Thủ công có hướng dẫn** | ~10% | NVDA (biên bản `e2e/a11y-manual.md`); S-01…S-13 phần chưa tự động; **chấm TH-05 "đầy đủ" và TH-07 "bịa đặt" do chuyên gia**; UAT; kiểm tra chất lượng trích xuất PDF thật của đội |
| **Thăm dò (exploratory)** | 2 phiên × 90 phút / giai đoạn | QA tự do trên bản staging với PDF thật; ghi phát hiện vào bug tracker |

**Lý do giữ chấm thủ công:** "đầy đủ" và "bịa đặt" cần phán đoán nội dung; tự động hóa hoàn toàn nằm ngoài phạm vi T20. Kiểm tra độ nhất quán: hai chuyên gia chấm độc lập mẫu 15 câu, đồng thuận ≥ 80%.

### 3.3 Dữ liệu kiểm thử

| Bộ dữ liệu | Nội dung | Chủ sở hữu | Dùng cho |
|---|---|---|---|
| **Fixtures PDF sinh bằng mã** (`e2e/fixtures/`, `MakeFixtures.java`) | `ok_vi`, `ok_en`, `too_many_101p`, `encrypted`, `scanned`, `mixed_scan`, `corrupted`, `fake_docx`, `big_11mb`, `injection` | QA + BE | E2E, tích hợp |
| **Corpus DEP-002** (`eval/corpus/`) | ≥ 10 PDF thật/đại diện, Việt + Anh; ≥ 1 tài liệu ≥ 80 trang; ≥ 2 tài liệu mâu thuẫn có chủ đích; ≥ 1 có trang scan một phần; đã ẩn danh | Đội PTKD | Đánh giá AI |
| **Bộ câu hỏi DEP-002** (`eval/dep002.yaml`) | ≥ 50 câu: 10 `single`, 10 `multi`, 10 `out_of_scope`, 10 `cross_lang`, 10 `injection` | Đội PTKD (ký, G-03) | Đánh giá AI, tải |
| **Payload XSS/injection** (`xss-payloads.json`) | ≥ 10 payload | QA + ATTT | S-05, S-06 |
| **PDF sinh bằng mã cho thử tải** | 20 tệp × 100 trang | QA | L-1, L-2 |
| **Tài khoản mẫu** | `lan` (Member), `minh` (Member), `tam` (mật khẩu tạm) | QA | E2E |

---

## 4. Tiêu chí đạt/không đạt, tạm dừng và tiếp tục

### 4.1 Tiêu chí đạt/không đạt (Item Pass/Fail Criteria)

**Mức ca kiểm thử:** một ca *Đạt* khi kết quả thực tế khớp kết quả mong đợi (kể cả nguyên văn thông điệp); *Không đạt* nếu sai lệch; *Bị chặn* nếu không chạy được vì lỗi khác.

**Mức bản phát hành (đối chiếu cổng launch của PRD §10):**

| Cổng | Tiêu chí đạt (đo được) | Bằng chứng | Chủ sở hữu |
|---|---|---|---|
| **G-01** | 100% AC của story P0 đạt | Báo cáo kiểm thử | QA Lead |
| **G-02** | 0 lỗi Blocker/Critical đang mở; lỗi Major ≤ 3 và đều có cách xử lý tạm | Bug tracker | QA Lead |
| **G-03** | DEP-002 sẵn sàng: ≥ 50 câu, phân bố tối thiểu như mục 3.3, được Đội PTKD ký | Bộ dữ liệu có chữ ký | Đội PTKD |
| **G-04** | **TH-04 ≥ 90%** (trích dẫn đúng trang) · **TH-05 ≥ 80%** (đầy đủ, đa tài liệu) · **TH-06 ≥ 90%** (từ chối đúng) · **TH-07 ≤ 5%** (bịa đặt) · MET-002 ≥ 85% | `report.md` + `grading.csv` | Product Owner |
| **G-05** | **TH-01** TTFT p90 ≤ 3 s · **TH-02** hoàn chỉnh p90 ≤ 15 s · **TH-03** xử lý 100 trang p90 ≤ 60 s · **TH-08** LCP p75 ≤ 2 s; lỗi ≤ 2%; 20 × 100 trang, ≥ 10 người đồng thời | Báo cáo tải | Tech Lead |
| **G-06** | OWASP: **0 High/Critical**; mọi Medium có chủ sở hữu và hạn xử lý | Báo cáo ZAP + thủ công | ATTT |
| **G-07** | Member không gọi được API Admin/Báo cáo; chưa đăng nhập không đọc được dữ liệu; không đọc hội thoại người khác (S-01, S-02, S-03) | Biên bản phân quyền | ATTT |
| **G-08** | ATTT và Pháp chế ký (AC-060.2, AC-060.4) | Biên bản ký | ATTT, Pháp chế |
| **G-09** | Độ phủ dòng BE (logic nghiệp vụ) **≥ 70%**; E2E P0 xanh trên **Chrome và Edge**, 5 lần liên tiếp không flaky | Báo cáo CI | Tech Lead |
| **G-10** | Mọi sự kiện EV-0xx ghi đúng trên staging | Kết quả kiểm sự kiện | Product Owner |
| **G-11** | Có baseline MET-001/MET-002 từ tra cứu thủ công trên DEP-002 | Bảng baseline | Trưởng nhóm PTKD |
| **G-12** | ≥ 5 người PER-01; **≥ 4/5** hoàn thành "tải lên – hỏi – kiểm chứng" không hỗ trợ | Biên bản UAT | Product Owner |
| **G-13, G-14** | Giám sát, cảnh báo chi phí, sao lưu, rollback đã diễn tập; hướng dẫn 1 trang | Runbook, tài liệu | Tech Lead, PO |

**Tiêu chí bổ sung (không nằm trong G nhưng đã chốt ở task):**

- Khả năng tiếp cận: **axe-core 0 lỗi serious/critical** ở mọi trạng thái chính; thao tác chính dùng được chỉ bằng bàn phím; zoom 200% không cuộn ngang.
- Injection (NFR-S05): **0 ca vượt qua** trong ≥ 10 ca (hiển thị) và nhóm `injection` của DEP-002 (không vi phạm `forbidden`, không lộ prompt hệ thống).
- AC-024.2: chênh lệch tỷ lệ đạt giữa `cross_lang` và `single` cùng ngôn ngữ ≤ 10 điểm %.
- AC-022.2: 100% câu trả lời có nội dung có ≥ 1 trích dẫn.
- Route: 100% endpoint (trừ `/auth/login`, `/healthz`) trả `401` khi không đăng nhập và `403` khi thiếu `X-Requested-With` cho POST/PUT/PATCH/DELETE.
- Cạnh tranh chỗ trống: workspace **không bao giờ vượt 20** tài liệu.

**Phân loại lỗi:** Blocker (chặn luồng P0, không có cách vòng) · Critical (sai dữ liệu / lộ dữ liệu / lỗ hổng High) · Major (chức năng sai nhưng có cách xử lý tạm) · Minor · Trivial.

### 4.2 Tiêu chí tạm dừng (Suspension Criteria)

Tạm dừng **toàn bộ chu kỳ kiểm thử** (hoặc nhánh bị ảnh hưởng) khi:

| # | Điều kiện | Phạm vi tạm dừng |
|---|---|---|
| SC-1 | Bản build không khởi động / không đăng nhập được, hoặc smoke test (đăng nhập → tải 1 PDF → hỏi 1 câu) thất bại | Toàn bộ |
| SC-2 | Có lỗi **Blocker** ở luồng P0 chưa có cách vòng | Nhánh liên quan |
| SC-3 | Phát hiện **lộ dữ liệu giữa người dùng** hoặc **XSS thực thi được** | Toàn bộ test chức năng; chuyển sang xử lý bảo mật |
| SC-4 | Nghi ngờ nội dung nghiệp vụ lọt vào log / analytics / công cụ báo lỗi (S-10) | Dừng nhận dữ liệu PDF thật vào môi trường đó |
| SC-5 | Môi trường staging bất ổn: CSDL ngắt lặp lại, tỷ lệ lỗi hạ tầng > 10% trong 30 phút | Test tải, E2E |
| SC-6 | Nhà cung cấp LLM/embedding ngừng hoạt động hoặc hết hạn mức | Đánh giá AI DEP-002, S-06, L-1 với LLM thật (E2E dùng Fake vẫn chạy) |
| SC-7 | > 30% ca E2E đã chạy thất bại cùng một nguyên nhân gốc | Dừng E2E đến khi sửa |
| SC-8 | Thay đổi phạm vi / hợp đồng API lớn làm kịch bản cũ không còn đúng | Nhánh liên quan, đến khi cập nhật test |

### 4.3 Yêu cầu tiếp tục (Resumption Requirements)

| Sau tạm dừng | Điều kiện để tiếp tục |
|---|---|
| SC-1, SC-2 | Bản vá đã triển khai; smoke test xanh; lỗi gốc có test hồi quy tự động; QA xác nhận đóng |
| SC-3, SC-4 | Lỗi đã sửa; **ATTT xác nhận**; chạy lại S-01, S-05, S-10 xanh; dữ liệu rò (nếu có) đã được xóa khỏi log/kho |
| SC-5 | Môi trường được dựng lại / reset dữ liệu về trạng thái chuẩn; smoke xanh; ghi nguyên nhân |
| SC-6 | Nhà cung cấp phục hồi, hoặc chuyển sang khóa/ngân sách khác đã duyệt; chạy lại tập con DEP-002 để xác nhận hành vi không đổi |
| SC-7, SC-8 | Nguyên nhân gốc đã sửa / test đã cập nhật và được duyệt; chạy lại toàn bộ nhóm bị ảnh hưởng (không chỉ ca lỗi) |

Mọi lần tạm dừng/tiếp tục ghi vào nhật ký kiểm thử kèm thời điểm, lý do, người quyết định.

---

## 5. Môi trường và nguồn lực

### 5.1 Môi trường kiểm thử (Environmental Needs)

| Môi trường | Mục đích | Cấu hình chính | LLM / Embedding |
|---|---|---|---|
| **Dev (PC của đội)** | Unit, tích hợp, E2E cục bộ | Java 21, Node 22, Docker, Python 3.12, PostgreSQL 16 + pgvector (Testcontainers), k6, ZAP | `FakeLLM` / `FakeEmbedding` |
| **CI (GitHub Actions)** | Cổng chất lượng mỗi PR: lint + test + build + E2E | Job `web`, `api` (đang là required check), job `e2e` thêm ở T21 | Fake |
| **E2E xác định** | Playwright trên Chrome + Edge | `ops/docker-compose.e2e.yml`: PostgreSQL + API (profile `e2e`) + web build; `LLM_PROVIDER=fake`, `EMBEDDING_PROVIDER=fake`, `STREAM_STALL_SECONDS=3`, `LLM_FIRST_TOKEN_TIMEOUT_S=2` | Fake |
| **Staging** | Đánh giá AI, thử tải, bảo mật, UAT | **Giống production** (cấu hình, kích thước CSDL); proxy nội bộ (kiểm OQ-UX-02: có đệm SSE không) | `bge-m3` qua Ollama + `claude-sonnet-5-5` (thật) |

**Phụ thuộc và điều kiện:**

- **Embedding `bge-m3` (1024 chiều):** đo sơ bộ trên PC dev chạy CPU cho thấy xử lý chậm gấp khoảng 4 lần ngưỡng TH-03. **Staging phải có tài nguyên đủ (GPU hoặc cấu hình tương đương) trước khi chạy L-2**; nếu không, kết quả TH-03 không có ý nghĩa.
- **Không gửi `temperature` / `top_p` / `top_k`** tới Messages API (trả 400 từ 2026-10-21) — cần ca kiểm thử xác nhận cấu hình LLM thật.
- Testcontainers cần `testcontainers.version ≥ 1.21.4` (Docker 29).
- Trình duyệt: Chrome và Edge (2 phiên bản mới nhất), viewport 1440×900 (thêm 1100×800 và mô phỏng zoom 200%).
- NVDA + Chrome trên một máy Windows cho kiểm thử trình đọc màn hình.
- Bug tracker (GitHub Issues) với nhãn mức độ Blocker / Critical / Major / Minor.
- Kết nối ra Anthropic API từ staging; khóa API riêng cho kiểm thử, có hạn mức chi phí ngày.
- Thỏa thuận LLM không lưu, không huấn luyện (DEP-001 / G-08) **phải có trước khi đưa PDF thật chứa dữ liệu nhạy cảm vào staging**; trước đó chỉ dùng corpus đã ẩn danh.

### 5.2 Trách nhiệm (Responsibilities)

| Vai trò | Trách nhiệm kiểm thử |
|---|---|
| **QA Lead** (bán thời gian) | Chủ sở hữu kế hoạch này; viết & duy trì E2E, a11y, bộ k6, kịch bản S-xx; chạy `eval/run.py`; quản lý bug tracker; lập báo cáo tổng kết; đề xuất Go/No-Go |
| **Frontend Dev** | Unit/component test FE kèm mỗi task; fixture MSW; sửa lỗi FE; lint kiến trúc R-1…R-6 |
| **Backend Dev** | Unit + tích hợp BE kèm mỗi task; `MakeFixtures.java`; `FakeLLM`/`FakeEmbedding`; sửa lỗi BE; số liệu EV-0xx |
| **Tech Lead** | Duyệt kế hoạch; cấu hình CI/staging; sở hữu G-05, G-09, G-13; xử lý OQ-UX-01/02 |
| **Đội PTKD** | Soạn & **ký** corpus + DEP-002 (G-03); **chấm TH-05/TH-07**; thực hiện baseline thủ công (G-11); cung cấp người UAT |
| **Product Owner** | Sở hữu G-04, G-10, G-12, G-14; quyết định hoãn AC P1; duyệt ngoại lệ ngưỡng |
| **An toàn thông tin** | Xem xét báo cáo bảo mật, kiểm thử S-01…S-13 phần phân quyền, ký G-06/G-08 |
| **Pháp chế** | Ký G-08 (thỏa thuận LLM) |
| **CNTT / Vận hành** | Dựng staging, proxy, sao lưu/rollback; trả lời OQ-UX-02 |

### 5.3 Nhân sự và đào tạo (Staffing & Training Needs)

Nguồn lực theo implement plan: **1 FE + 1 BE toàn thời gian, 1 QA bán thời gian** [Đề xuất]. Tổng công sức QA ở danh sách task: **13 người-ngày** (T20: 5, T21: 4, T22: 4), chưa kể thời gian chuyên gia PTKD.

| Nhu cầu | Đối tượng | Nội dung | Khi nào |
|---|---|---|---|
| Playwright + axe-core | QA | Viết E2E có hai ngữ cảnh trình duyệt, mô phỏng offline, bắt request mạng | Trước T21 |
| NVDA | QA | Kiểm thử `aria-live`, `role="log"`, thứ tự focus | Trước T21 |
| k6 | QA | Kịch bản SSE dài, ảo hóa người dùng | Trước T22 |
| OWASP ZAP / XSS / IDOR | QA + ATTT | Quét có phiên đăng nhập, bộ payload | Trước T22 |
| Quy tắc chấm "đầy đủ" / "bịa đặt" | Chuyên gia PTKD | Đọc `eval/README.md`, chấm thử 15 câu để hiệu chuẩn | Trước khi chấm chính thức |
| Domain: tài liệu dự án, hợp đồng, đề xuất | QA | Hiểu nội dung corpus để phát hiện trích dẫn sai | GĐ 1 |
| Rủi ro nhân sự | — | Chỉ 1 QA bán thời gian → khi GĐ 4 dồn việc có thể thiếu người; cần Dev hỗ trợ (xem mục 8) | — |

---

## 6. Thực thi và bàn giao

### 6.1 Công việc kiểm thử (Testing Tasks)

| # | Công việc | Phụ trách | Phụ thuộc | Giai đoạn |
|---|---|---|---|---|
| TT-01 | Duyệt & chốt kế hoạch này; thiết lập bug tracker, nhãn mức độ | QA Lead, Tech Lead | — | Ngay |
| TT-02 | Bắt đầu soạn corpus + DEP-002 (phụ thuộc con người, không phụ thuộc code) | Đội PTKD | — | GĐ 1 → |
| TT-03 | Smoke test + rà soát unit/tích hợp của T01–T16 (đã triển khai): chạy toàn bộ trong CI, ghi độ phủ BE, rà ca thiếu theo AC P0 | QA, Dev | T01–T16 | GĐ 2 (hiện tại) |
| TT-04 | Dựng khung Playwright, môi trường E2E xác định, fixtures; viết E-01…E-12 (BB-01 + lõi BB-02) | QA | T10, T11, T14 | GĐ 1–2 |
| TT-05 | Viết `eval/run.py`, `score.py`, báo cáo; kiểm tra công cụ bằng kết quả giả có đáp án biết trước | QA | T14 | GĐ 2–3 |
| TT-06 | Chạy DEP-002 lần 1 (baseline AI) → phản hồi cho T12/T13 | QA, PTKD | TT-02, TT-05 | GĐ 2–3 |
| TT-07 | E-13…E-22 (Xem nguồn, Dừng, lỗi, offline, phân quyền) + axe-core mọi trạng thái | QA | T17, T18, T19 | GĐ 3 |
| TT-08 | Baseline thủ công MET-001/MET-002 (G-11) | Đội PTKD | TT-02 | GĐ 3–4 |
| TT-09 | Thử tải L-1…L-5 trên staging, ghi điểm nghẽn | QA, BE | T19, staging | GĐ 4 |
| TT-10 | Bảo mật: ZAP, liệt kê route, S-01…S-13, quét phụ thuộc/bí mật | QA, ATTT | T19 | GĐ 4 |
| TT-11 | NVDA thủ công, kiểm tra bàn phím / zoom / reduced-motion | QA | T18, T19 | GĐ 4 |
| TT-12 | Chạy DEP-002 chính thức; chuyên gia chấm TH-05/07; đồng thuận 15 câu | QA, PTKD | TT-06, bản đóng băng prompt | GĐ 4 |
| TT-13 | Sửa lỗi & kiểm thử hồi quy; chạy E2E 5 lần liên tiếp để xác nhận ổn định | Dev, QA | TT-07…TT-12 | GĐ 4 |
| TT-14 | UAT ≥ 5 người PER-01 (G-12) | PO, PTKD | Staging ổn định | GĐ 4 |
| TT-15 | Báo cáo tổng kết, đối chiếu G-01…G-14, đề xuất Go/No-Go | QA Lead | tất cả | Cuối GĐ 4 |

### 6.2 Danh mục ca kiểm thử chính

Mỗi ca nằm ở một tài liệu nguồn duy nhất; kế hoạch này chỉ ánh xạ, không chép lại. **Các ca đã được tách thành từng test case, mỗi file một ca, tại [test_cases/](test_cases/README.md)** (72 test case, mã `TC-<nhóm>-nn`); kết quả, ảnh chụp màn hình và video của từng ca lưu tại `./test_report/{ID}/`.

| Nhóm | Mã ca | Số ca | Nguồn chi tiết | Mục tiêu |
|---|---|---|---|---|
| Chấp nhận BB-01 | T1–T9 | 9 | [US_Backbone §BB-01.7](../user_stories/US_Backbone_Upload_va_HoiDap.md) | Tải lên, lỗi, giới hạn, thời gian thực, mất mạng |
| Chấp nhận BB-02 | T1–T12 | 12 | [US_Backbone §BB-02.7](../user_stories/US_Backbone_Upload_va_HoiDap.md) | Trích dẫn, mâu thuẫn, từ chối, một phần, injection, Dừng, lỗi, hiệu năng |
| E2E tự động | E-01 – E-22 | 22 | [T21 §3.2](../implement/T21_qa-e2e-accessibility.md) | Mọi luồng P0 trên Chrome + Edge |
| Thử tải | L-1 – L-5 | 5 | [T22 §3.1](../implement/T22_qa-load-security.md) | TH-01/02/03/08, độ bền |
| Bảo mật | S-01 – S-13 | 13 | [T22 §3.2](../implement/T22_qa-load-security.md) | IDOR, phiên, XSS, injection, upload độc hại, CSRF |
| Chất lượng AI | Q-001… (≥ 50) | ≥ 50 | `eval/dep002.yaml` ([T20](../implement/T20_qa-eval-dataset-harness.md)) | TH-04…TH-07, AC-024.2 |
| Tiếp cận | A11y-xx | theo trạng thái | [T21 §3.3](../implement/T21_qa-e2e-accessibility.md) | WCAG 2.1 AA, bàn phím, NVDA |

**Ví dụ ma trận truy vết yêu cầu → kiểm thử:**

| Yêu cầu | Ca tự động | Ca thủ công / đo |
|---|---|---|
| AC-010.1, TH-03 | E-04 | L-2 |
| AC-010.3 | E-05 | — |
| AC-010.4/5 | E-08, E-11 | — |
| AC-011.2 | E-09 | T8 (BB-01) |
| AC-012.1 – 012.5 | E-06 | — |
| AC-012.6, 012.8 | E-05, E-07 | T7 (BB-01) |
| AC-020.1, TH-01/02 | E-12 | L-1 |
| AC-020.4 | E-04 | — |
| AC-021.x, AC-022.x | E-12 | DEP-002 (`multi`, `single`) |
| AC-023.x, TH-06 | E-14 | DEP-002 (`out_of_scope`) |
| AC-024.x | E-15 | DEP-002 (`cross_lang`) |
| AC-030.x | E-13, E-20 | — |
| US-026 | E-18 | — |
| NFR-S05 | E-16 | S-05, S-06, DEP-002 (`injection`) |
| NFR-S04, G-07 | E-22 | S-01, S-02, S-03 |
| NFR-U03 | axe-core mọi trạng thái | NVDA |

### 6.3 Sản phẩm bàn giao (Test Deliverables)

| Sản phẩm | Nội dung | Khi nào |
|---|---|---|
| Kế hoạch kiểm thử (tài liệu này) | TP-CVD-001, cập nhật khi đổi phạm vi | Ngay; cập nhật mỗi GĐ |
| Bộ test tự động | `api/src/test`, `web/src/**/*.test.ts`, `e2e/tests/*.spec.ts` | Kèm mỗi task |
| Fixtures + dữ liệu | `e2e/fixtures/`, `eval/corpus/`, `eval/dep002.yaml` (có chữ ký PTKD) | GĐ 1 → 4 |
| Công cụ đánh giá | `eval/run.py`, `score.py`, `report_template.md`, `baseline_template.*` | GĐ 2–3 |
| Báo cáo CI | Độ phủ, kết quả E2E, trace/video khi lỗi | Mỗi PR |
| Báo cáo đánh giá AI | `eval/reports/<ngày>/report.md`, `results.json`, `grading.csv` | Mỗi thay đổi prompt/truy hồi; chính thức GĐ 4 |
| Báo cáo thử tải | `reports/load-YYYYMMDD.md` (kèm tài nguyên, điểm nghẽn, chi phí token) | GĐ 4 |
| Báo cáo bảo mật | `reports/security-YYYYMMDD.md` + bảng S-01…S-13 (ATTT ký) | GĐ 4 |
| Biên bản tiếp cận | `e2e/a11y-manual.md` + báo cáo axe-core | GĐ 3–4 |
| Biên bản UAT | Kịch bản, quan sát, kết quả từng người | GĐ 4 |
| Nhật ký lỗi | Bug tracker: mức độ, bằng chứng, chủ sở hữu, trạng thái | Liên tục |
| **Báo cáo tổng kết kiểm thử (Test Summary Report)** | Đối chiếu G-01…G-14, thống kê lỗi, rủi ro tồn, đề xuất Go/No-Go | Cuối GĐ 4 |

### 6.4 Lịch trình (Schedule)

Căn theo lộ trình implement plan (tổng ≈ 7–9,5 tuần; ngân sách/thời hạn thực tế còn mở — OQ-011). Dùng mốc tương đối [Đề xuất]; cập nhật ngày thật khi chốt.

| GĐ | Hoạt động kiểm thử | Mốc ra (exit) |
|---|---|---|
| **0** Nền tảng (1 tuần) | Thiết lập CI, lint kiến trúc, hợp đồng OpenAPI, MSW; unit đầu tiên | CI xanh; hợp đồng được duyệt |
| **1** BB-01 (2 tuần) | Unit/tích hợp T07–T11; khung Playwright; E-04…E-11; **PTKD bắt đầu soạn DEP-002** | BB-01 T1–T9 đạt; E2E BB-01 xanh |
| **2** BB-02 lõi (2–3 tuần) | Unit/tích hợp T12–T16; E-12, E-14…E-17; `eval/run.py`; **DEP-002 lần 1** (baseline) | BB-02 T1–T8 đạt; có số đo AI đầu tiên |
| **3** Kiểm chứng & bền (1–1,5 tuần) | E-13, E-18…E-22; axe-core mọi trạng thái; chỉnh prompt/top-k theo DEP-002 | BB-02 T9–T11 đạt; E2E đủ 22 ca |
| **4** Chất lượng & phát hành (1–2 tuần) | Tải (L-1…L-5), bảo mật (ZAP, S-01…S-13), NVDA, DEP-002 chính thức, UAT, hồi quy | **G-01…G-14 đạt → Go/No-Go** |

**Tiến độ hiện tại (2026-10-28):** T01–T16 đã commit; việc QA kế tiếp là TT-03 (rà soát kiểm thử đã có) và TT-04/TT-05 để không dồn công việc về GĐ 4.

**Điểm rủi ro lịch:** DEP-002 và corpus phụ thuộc con người, **cần bắt đầu ngay** (GĐ 1) vì là điều kiện của G-03/G-04 và chặn TT-06, TT-12.

---

## 7. Rủi ro và phương án dự phòng

| ID | Rủi ro | Mức | Giảm thiểu / dự phòng |
|---|---|---|---|
| TR-01 | **DEP-002 và corpus chậm** (phụ thuộc Đội PTKD) → không đo được TH-04…TH-07, chặn G-03/G-04 | Cao | Bắt đầu từ GĐ 1; QA hỗ trợ định dạng YAML; dùng corpus đại diện đã ẩn danh trong lúc chờ tài liệu thật |
| TR-02 | **Embedding CPU chậm ~4× so với TH-03**; kết quả tải sai lệch | Cao | Staging có GPU/tài nguyên tương đương; ghi rõ cấu hình khi báo cáo; không kết luận TH-03 từ PC dev |
| TR-03 | **Kết quả LLM không xác định** → đo AI dao động giữa các lần chạy | Cao | Chạy lặp ≥ 3 lần mỗi lần đánh giá, báo cáo trung bình và khoảng; cố định phiên bản prompt/`TOP_K`/`MIN_SCORE`/mô hình trong báo cáo; hồi quy theo ngưỡng 2 điểm % |
| TR-04 | **Thỏa thuận LLM / ATTT chậm** → không được đưa PDF thật vào staging | Cao | Dùng corpus ẩn danh; chuẩn bị phương án mô hình triển khai nội bộ (implement plan §7) |
| TR-05 | **Chỉ 1 QA bán thời gian**, GĐ 4 dồn nhiều việc (T20–T22 ≈ 13 người-ngày) | Cao | Dev hỗ trợ viết fixtures, sửa lỗi nhanh; bắt đầu E2E và `eval` từ GĐ 1–2; ưu tiên P0 trước, P1 hoãn có văn bản của PO |
| TR-06 | **Proxy đệm SSE** làm streaming giật; E2E trên Fake không phát hiện | TB | Kiểm sớm trên staging (OQ-UX-02); thêm ca TTFT qua proxy thật; heartbeat dự phòng |
| TR-07 | **Hết hạn mức / giới hạn tốc độ API LLM** khi đánh giá và thử tải | TB | Hạn mức ngân sách riêng cho QA; thử tải dùng LLM giả có độ trễ thực tế (ghi rõ lựa chọn); chạy DEP-002 theo `--only category` để tiết kiệm |
| TR-08 | **Chi phí token vượt ngân sách** khi chạy lặp DEP-002 | TB | Ghi token từ GĐ 2; giới hạn lần chạy đầy đủ (mỗi lần đóng băng prompt); cảnh báo theo ngày |
| TR-09 | **Test E2E flaky** (SSE, hai ngữ cảnh, thời gian) | TB | Môi trường xác định (`STREAM_STALL_SECONDS=3`…), chờ theo sự kiện không theo `sleep`; tiêu chí "5 lần liên tiếp xanh"; thử lại 1 lần trên CI nhưng theo dõi tỷ lệ |
| TR-10 | **PDF thật khó trích xuất** (bảng, nhiều cột, scan một phần) → trích dẫn sai/thiếu | TB | Thử sớm với PDF thật; kiểm tra trang không đọc được hiển thị rõ; ghi nhận vào báo cáo thay vì hạ ngưỡng |
| TR-11 | **OQ-UX-01:** backend không trả vị trí đoạn trích → AC-030.1 "nổi bật" hạ xuống tìm chuỗi phía client | TB | Chốt ở GĐ 0/1; điều chỉnh E-13 và tiêu chí "≥ 9/10 đoạn nổi bật đúng" theo quyết định |
| TR-12 | **Chấm thủ công không nhất quán** giữa chuyên gia | TB | Hiệu chuẩn 15 câu, đồng thuận ≥ 80%, quy tắc chấm viết trong `eval/README.md` |
| TR-13 | **Phạm vi P0 ngoài lát cắt** (US-002/003, US-050, US-061) chưa có kế hoạch kiểm thử | TB | Soạn bổ sung test plan sau GĐ 4; nêu rõ trong báo cáo tổng kết là điều kiện launch còn mở |
| TR-14 | **Môi trường Windows**: ổ C: gần đầy, Docker cài ở D: | Thấp | Đặt cache/artifact Playwright, Testcontainers trên D:; dọn dẹp định kỳ; CI chạy trên Linux để giảm khác biệt |

---

## 8. Phê duyệt (Approvals)

Kế hoạch này chỉ được coi là có hiệu lực khi các bên dưới ký. Chữ ký ở **bản cuối của kế hoạch**, không thay thế chữ ký các cổng G-xx ở mục 4.1.

| Vai trò | Họ tên | Chữ ký | Ngày | Ghi chú |
|---|---|---|---|---|
| QA Lead (tác giả) | [TBD] | | | |
| Tech Lead | [TBD] | | | Môi trường, CI, G-05/G-09 |
| Product Owner | [TBD] | | | Phạm vi, G-04/G-12 |
| Đại diện Đội PTKD | [TBD] | | | DEP-002, chấm TH-05/07 |
| An toàn thông tin | [TBD] | | | Phạm vi bảo mật, G-06/G-07/G-08 |

---

## Phụ lục A — Đối chiếu checklist IEEE 829

Đối chiếu với `docs/Test_Plan_IEEE_829_Checklist.xlsx` (18 mục).

| # | Nhóm | Mục checklist | Vị trí trong tài liệu | Trạng thái |
|---|---|---|---|---|
| 1 | 1. Metadata | Test Plan Identifier | §1.1 | Đạt |
| 2 | | Introduction | §1.2 | Đạt |
| 3 | 2. Phạm vi | Test Items | §2.1 | Đạt |
| 4 | | Features to be Tested | §2.2 | Đạt |
| 5 | | Features Not to be Tested | §2.3 | Đạt |
| 6 | 3. Phương pháp | Testing Approach | §3.1, §3.3 | Đạt |
| 7 | | Automation vs Manual | §3.2 | Đạt |
| 8 | 4. Đạt/Không đạt | Item Pass/Fail Criteria | §4.1 | Đạt |
| 9 | | Suspension Criteria | §4.2 | Đạt |
| 10 | | Resumption Requirements | §4.3 | Đạt |
| 11 | 5. Môi trường | Environmental Needs | §5.1 | Đạt |
| 12 | | Responsibilities | §5.2 | Đạt |
| 13 | | Staffing & Training Needs | §5.3 | Đạt |
| 14 | 6. Thực thi | Testing Tasks | §6.1 | Đạt |
| 15 | | Test Deliverables | §6.3 | Đạt |
| 16 | | Schedule | §6.4 | Đạt — ngày tuyệt đối chờ chốt OQ-011 |
| 17 | 7. Rủi ro | Risks & Contingencies | §7 | Đạt |
| 18 | | Approvals | §8 | Khung đã có — **chờ tên và chữ ký** |

## Phụ lục B — Điểm cần xác nhận trước khi chốt bản 1.0

1. Tên người giữ từng vai trò (§8) và liệu QA bán thời gian có đủ cho GĐ 4 (TR-05).
2. Mốc ngày tuyệt đối (§6.4) — phụ thuộc OQ-011 (ngân sách/thời hạn).
3. Cấu hình staging có GPU hay không (TR-02) — quyết định liệu TH-03 đo được.
4. Quyết định OQ-UX-01 (vị trí đoạn trích) và OQ-UX-02 (proxy SSE) — ảnh hưởng E-13 và ca TTFT.
5. Xác nhận OQ-016 (AI không dùng kiến thức ngoài tài liệu) — nền của AC-023.2.
6. Ngưỡng tỷ trọng tự động/thủ công (§3.2) và số phiên exploratory — là đề xuất.
7. Cần soạn **test plan bổ sung** cho các story P0 ngoài lát cắt (US-002/003, US-050, US-061).
