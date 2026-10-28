import type { components } from '@/shared/api/schema'
import type { Scenario } from './scenario'

type CurrentUser = components['schemas']['CurrentUser']
type Document = components['schemas']['Document']

// Tài khoản giả: mật khẩu chung MOCK_PASSWORD; một số email kích hoạt nhánh lỗi của đăng nhập.
export const MOCK_PASSWORD = 'Matkhau-2026'

export const MOCK_USERS: Record<string, CurrentUser> = {
  'lan@congty.vn': { id: '11111111-1111-4111-8111-111111111111', email: 'lan@congty.vn', display_name: 'Lan', role: 'member', must_change_password: false },
  'admin@congty.vn': { id: '22222222-2222-4222-8222-222222222222', email: 'admin@congty.vn', display_name: 'Quản trị', role: 'admin', must_change_password: false },
  'moi@congty.vn': { id: '33333333-3333-4333-8333-333333333333', email: 'moi@congty.vn', display_name: 'Người mới', role: 'member', must_change_password: true },
}
export const LOCKED_EMAIL = 'khoa@congty.vn'
export const DISABLED_EMAIL = 'vohieu@congty.vn'

const people = {
  lan: { id: MOCK_USERS['lan@congty.vn'].id, display_name: 'Lan' },
  minh: { id: '44444444-4444-4444-8444-444444444444', display_name: 'Minh' },
  hoa: { id: '55555555-5555-4555-8555-555555555555', display_name: 'Hoa' },
}

const hoursAgo = (h: number) => new Date(Date.now() - h * 3_600_000).toISOString()

let seq = 0
export function docId(n: number): string {
  return `d0c00000-0000-4000-8000-${String(n).padStart(12, '0')}`
}

function doc(n: number, partial: Partial<Document> & Pick<Document, 'name' | 'status'>): Document {
  return {
    id: docId(n),
    page_count: null,
    error_code: null,
    error_details: null,
    unreadable_pages: [],
    uploaded_by: people.lan,
    uploaded_at: hoursAgo(n),
    can_delete: true,
    ...partial,
  }
}

/** Danh sách tài liệu theo kịch bản (dữ liệu lấy từ prototype SCR-03). */
export function seedDocuments(s: Scenario): Document[] {
  seq = 100
  const base: Document[] = [
    doc(1, { name: 'HopDong_A.pdf', status: 'ready', page_count: 48 }),
    doc(2, { name: 'DeXuat_B.pdf', status: 'ready', page_count: 22, uploaded_by: people.minh, can_delete: false }),
    doc(3, { name: 'BaoCao_Q3_2026_ban_cuoi_cung_da_duyet_v3.pdf', status: 'ready', page_count: 64, unreadable_pages: [7, 8] }),
    doc(4, { name: 'BienBan_hop_10.pdf', status: 'ready', page_count: 9, uploaded_by: people.hoa, can_delete: false }),
    doc(5, { name: 'Scan_01.pdf', status: 'failed', error_code: 'SCANNED_PDF', page_count: 12 }),
    doc(6, { name: 'PhuLuc_gia.pdf', status: 'processing', uploaded_by: people.minh, can_delete: false, uploaded_at: hoursAgo(0) }),
  ]
  switch (s) {
    case 'empty_workspace':
      return []
    case 'processing_only':
      return [doc(1, { name: 'HopDong_A.pdf', status: 'processing' }), doc(2, { name: 'DeXuat_B.pdf', status: 'processing' })]
    case 'all_failed':
      return [
        doc(1, { name: 'Scan_01.pdf', status: 'failed', error_code: 'SCANNED_PDF', page_count: 12 }),
        doc(2, { name: 'KhoaPass.pdf', status: 'failed', error_code: 'PASSWORD_PROTECTED' }),
        doc(3, { name: 'SoTay_NhanVien.pdf', status: 'failed', error_code: 'TOO_MANY_PAGES', page_count: 140, error_details: { page_count: 140 } }),
        doc(4, { name: 'Loi_he_thong.pdf', status: 'failed', error_code: 'SYSTEM_ERROR' }),
      ]
    case 'near_limit': {
      const many = [...base]
      for (let i = 7; i <= 19; i++) many.push(doc(i, { name: `TaiLieu_${String(i).padStart(2, '0')}.pdf`, status: 'ready', page_count: 10 + i }))
      return many
    }
    default:
      return base
  }
}

export function newDocument(name: string, owner: CurrentUser): Document {
  seq += 1
  return doc(seq, {
    name,
    status: 'processing',
    uploaded_by: { id: owner.id, display_name: owner.display_name },
    uploaded_at: new Date().toISOString(),
  })
}

/** Câu trả lời mẫu (prototype SCR-03). */
export const ANSWERS = {
  answered: {
    text:
      'Hai tài liệu quy định thời hạn thanh toán **không thống nhất**.\n\n' +
      '- Hợp đồng A: thanh toán đợt 1 trong vòng **30 ngày** kể từ ngày nhận hóa đơn hợp lệ [1].\n' +
      '- Đề xuất B: thời hạn **45 ngày** kể từ ngày nghiệm thu [2].\n\n' +
      'Đây là mâu thuẫn giữa hai tài liệu nên hệ thống không tự chọn bên nào. Bạn nên đối chiếu với bản hợp đồng đã ký.',
    citations: [
      { index: 1, document_id: docId(1), document_name: 'HopDong_A.pdf', page: 12, document_deleted: false },
      { index: 2, document_id: docId(2), document_name: 'DeXuat_B.pdf', page: 4, document_deleted: false },
    ],
  },
  partial: {
    text: 'Doanh thu quý 3 đạt **128,4 tỷ đồng**, tăng 12% so với cùng kỳ [1].',
    missing: 'Biên lợi nhuận theo từng mảng không có trong các tài liệu hiện có.',
    citations: [{ index: 1, document_id: docId(3), document_name: 'BaoCao_Q3_2026_ban_cuoi_cung_da_duyet_v3.pdf', page: 7, document_deleted: false }],
  },
}

export const CITATION_TEXT: Record<string, { snippet: string; page_count: number }> = {
  [`${docId(1)}:12`]: { snippet: '30 (ba mươi) ngày kể từ ngày nhận hóa đơn hợp lệ', page_count: 48 },
  [`${docId(2)}:4`]: { snippet: 'Thời hạn thanh toán là 45 ngày kể từ ngày nghiệm thu', page_count: 22 },
  [`${docId(3)}:7`]: { snippet: '128,4 tỷ đồng, tăng 12% so với cùng kỳ', page_count: 64 },
}
