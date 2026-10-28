package com.chatvsdoc.api.service.documents;

import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.repository.UserRepository;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Chuyển entity {@link Document} thành {@link DocumentView} (kèm tên người tải lên). */
@Component
public class DocumentViews {

    public static final String TOO_MANY_PAGES = "TOO_MANY_PAGES";

    private final UserRepository users;

    public DocumentViews(UserRepository users) {
        this.users = users;
    }

    public DocumentView of(Document d) {
        return list(List.of(d)).get(0);
    }

    public List<DocumentView> list(List<Document> docs) {
        Set<UUID> ids = new HashSet<>();
        docs.forEach(d -> ids.add(d.getUploadedBy()));
        Map<UUID, String> names =
                users.findAllById(ids).stream()
                        .collect(Collectors.toMap(User::getId, User::getDisplayName));
        Function<Document, DocumentView> map =
                d ->
                        new DocumentView(
                                d.getId(),
                                d.getName(),
                                d.getPageCount(),
                                d.getStatus(),
                                d.getErrorCode(),
                                TOO_MANY_PAGES.equals(d.getErrorCode()) && d.getPageCount() != null
                                        ? Map.of("page_count", d.getPageCount())
                                        : null,
                                Arrays.asList(d.getUnreadablePages()),
                                new DocumentView.Uploader(
                                        d.getUploadedBy(),
                                        names.getOrDefault(d.getUploadedBy(), "")),
                                d.getUploadedAt(),
                                false);
        return docs.stream().map(map).toList();
    }
}
