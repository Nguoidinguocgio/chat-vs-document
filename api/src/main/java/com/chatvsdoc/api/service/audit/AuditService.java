package com.chatvsdoc.api.service.audit;

import com.chatvsdoc.api.domain.entity.AuditLog;
import com.chatvsdoc.api.domain.entity.AuditResult;
import com.chatvsdoc.api.domain.repository.AuditLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ghi nhật ký kiểm toán. {@code target} là ID/khóa, không phải nội dung nghiệp vụ. */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    // Giao dịch riêng: lần thất bại vẫn được ghi kể cả khi giao dịch nghiệp vụ bị rollback.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorUserId, String action, String target, AuditResult result) {
        repo.save(new AuditLog(actorUserId, action, target, result));
    }
}
