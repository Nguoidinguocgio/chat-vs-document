package com.chatvsdoc.api.web;

import com.chatvsdoc.api.common.AppException;
import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.documents.DocumentService;
import com.chatvsdoc.api.service.documents.DocumentView;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletDiskFileUpload;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Danh sách, tải lên, thử lại tài liệu (T07, T09). */
@RestController
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/api/documents")
    DocumentService.DocumentList list(@AuthenticationPrincipal AuthUser me) {
        return documents.list(me);
    }

    /**
     * {@code multipart/form-data} một trường {@code file}. Đọc theo luồng (multipart của Spring đã
     * tắt) để dừng ngay khi vượt {@code MAX_FILE_MB} thay vì đọc hết body.
     */
    @PostMapping("/api/documents")
    ResponseEntity<DocumentView> upload(
            @AuthenticationPrincipal AuthUser me,
            @RequestHeader(value = "X-Batch-Size", required = false) Integer batchSize,
            HttpServletRequest request)
            throws IOException {
        if (!JakartaServletDiskFileUpload.isMultipartContent(request)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, Map.of("reason", "multipart_required"));
        }
        int batch = batchSize == null || batchSize < 1 || batchSize > 100 ? 1 : batchSize;
        try {
            JakartaServletDiskFileUpload parser = new JakartaServletDiskFileUpload();
            // Trình duyệt gửi tên file UTF-8 thô trong Content-Disposition.
            parser.setHeaderCharset(StandardCharsets.UTF_8);
            FileItemInputIterator it = parser.getItemIterator(request);
            while (it.hasNext()) {
                FileItemInput item = it.next();
                if (!item.isFormField() && "file".equals(item.getFieldName())) {
                    String name = fileName(item.getHeaders().getHeader("Content-Disposition"));
                    try (InputStream in = item.getInputStream()) {
                        DocumentView created = documents.upload(me, name, in, batch);
                        return ResponseEntity.status(HttpStatus.CREATED).body(created);
                    }
                }
            }
        } catch (FileUploadException e) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, Map.of("reason", "malformed_multipart"));
        }
        throw new AppException(ErrorCode.VALIDATION_ERROR, Map.of("reason", "file_missing"));
    }

    private static final Pattern FILENAME_EXT =
            Pattern.compile("filename\\*\\s*=\\s*(?i:utf-8)''([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILENAME =
            Pattern.compile(
                    "filename\\s*=\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|[^;]+)",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Tên file từ {@code Content-Disposition} ({@code filename*=UTF-8''…} ưu tiên, rồi {@code
     * filename="…"}). Không dùng {@code FileItemInput.getName()}: hàm này kiểm tra bằng {@code
     * Paths.get} nên trên Windows mọi tên có {@code ? : * " < > |} gây lỗi 500.
     */
    static String fileName(String disposition) {
        if (disposition == null) {
            return "";
        }
        Matcher ext = FILENAME_EXT.matcher(disposition);
        if (ext.find()) {
            return URLDecoder.decode(
                    ext.group(1).trim().replace("+", "%2B"), StandardCharsets.UTF_8);
        }
        Matcher m = FILENAME.matcher(disposition);
        if (!m.find()) {
            return "";
        }
        return m.group(2) != null ? m.group(2).replaceAll("\\\\(.)", "$1") : m.group(1).trim();
    }

    @PostMapping("/api/documents/{id}/retry")
    ResponseEntity<DocumentView> retry(
            @AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documents.retry(me, id));
    }
}
