package com.chatvsdoc.api.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.domain.entity.AnswerType;
import com.chatvsdoc.api.domain.entity.Chunk;
import com.chatvsdoc.api.domain.entity.Citation;
import com.chatvsdoc.api.domain.entity.Conversation;
import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.DocumentPage;
import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.domain.entity.Event;
import com.chatvsdoc.api.domain.entity.Message;
import com.chatvsdoc.api.domain.entity.MessageStatus;
import com.chatvsdoc.api.domain.entity.Session;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.repository.ChunkRepository;
import com.chatvsdoc.api.domain.repository.ChunkVectorRepository;
import com.chatvsdoc.api.domain.repository.CitationRepository;
import com.chatvsdoc.api.domain.repository.ConversationRepository;
import com.chatvsdoc.api.domain.repository.DocumentPageRepository;
import com.chatvsdoc.api.domain.repository.DocumentRepository;
import com.chatvsdoc.api.domain.repository.EventRepository;
import com.chatvsdoc.api.domain.repository.MessageRepository;
import com.chatvsdoc.api.domain.repository.SessionRepository;
import com.chatvsdoc.api.domain.repository.UserRepository;
import com.chatvsdoc.api.port.EmbeddingPort;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Lưu rồi đọc lại từng entity để chắc ánh xạ JPA (enum PostgreSQL, citext, int[], jsonb, vector).
 */
@IntegrationTest
class RepositoryMappingTest {

    @Autowired Fixtures fixtures;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired DocumentRepository documents;
    @Autowired DocumentPageRepository pages;
    @Autowired ChunkRepository chunks;
    @Autowired ChunkVectorRepository vectors;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired CitationRepository citations;
    @Autowired EventRepository events;
    @Autowired EmbeddingPort embedding;

    @Test
    void emailIsCaseInsensitive() {
        User u = fixtures.user();
        assertThat(users.findByEmail(u.getEmail().toUpperCase())).isPresent();
    }

    @Test
    void sessionRoundTrip() {
        User u = fixtures.user();
        String hash = "a".repeat(63) + "b";
        sessions.save(new Session(hash, u.getId()));
        assertThat(sessions.findByTokenHash(hash))
                .get()
                .extracting(Session::getUserId)
                .isEqualTo(u.getId());
    }

    @Test
    void documentPagesChunksAndVectors() {
        User u = fixtures.user();
        Document d =
                new Document(
                        "Hợp đồng.pdf",
                        1234,
                        "f".repeat(64),
                        "docs/" + UUID.randomUUID(),
                        u.getId());
        d.setUnreadablePages(new Integer[] {3, 7});
        d.setStatus(DocumentStatus.ready);
        d.setPageCount(10);
        documents.saveAndFlush(d);

        Document loaded = documents.findById(d.getId()).orElseThrow();
        assertThat(loaded.getUnreadablePages()).containsExactly(3, 7);
        assertThat(loaded.getStatus()).isEqualTo(DocumentStatus.ready);

        pages.save(new DocumentPage(d.getId(), 1, "Điều khoản thanh toán"));
        assertThat(pages.findByDocumentIdOrderByPage(d.getId())).hasSize(1);

        Chunk payment =
                chunks.saveAndFlush(new Chunk(d.getId(), 1, 0, 21, "Điều khoản thanh toán"));
        Chunk other = chunks.saveAndFlush(new Chunk(d.getId(), 2, 0, 18, "Bảo hành thiết bị"));
        List<float[]> v = embedding.embed(List.of(payment.getText(), other.getText()));
        vectors.updateEmbedding(payment.getId(), v.get(0));
        vectors.updateEmbedding(other.getId(), v.get(1));

        assertThat(vectors.findEmbedding(payment.getId()))
                .get()
                .satisfies(e -> assertThat(e).hasSize(1024));
        List<ChunkVectorRepository.ScoredChunk> hits =
                vectors.nearest(embedding.embed(List.of("thanh toán")).get(0), 50);
        assertThat(hits).isNotEmpty();
        int iPayment = indexOf(hits, payment.getId());
        int iOther = indexOf(hits, other.getId());
        assertThat(iPayment).isNotNegative().isLessThan(iOther < 0 ? Integer.MAX_VALUE : iOther);
    }

    @Test
    void conversationMessageCitationAndEvent() {
        User u = fixtures.user();
        Document d =
                documents.saveAndFlush(
                        new Document(
                                "a.pdf", 1, "0".repeat(64), "k/" + UUID.randomUUID(), u.getId()));
        Conversation c = conversations.saveAndFlush(new Conversation(u.getId(), "Hội thoại"));
        Message m = new Message(c.getId(), "Câu hỏi?", "vi");
        m.setAnswerType(AnswerType.not_found);
        m.setStatus(MessageStatus.completed);
        m.setCompletedAt(Instant.now());
        messages.saveAndFlush(m);
        citations.saveAndFlush(new Citation(m.getId(), 1, d.getId(), 4, null));

        Message loaded = messages.findById(m.getId()).orElseThrow();
        assertThat(loaded.getAnswerType()).isEqualTo(AnswerType.not_found);
        assertThat(loaded.getStatus()).isEqualTo(MessageStatus.completed);
        assertThat(citations.findByMessageIdOrderByIdx(m.getId()))
                .singleElement()
                .extracting(Citation::getPage)
                .isEqualTo(4);

        Event e =
                events.saveAndFlush(
                        new Event("question_asked", u.getId(), null, Map.of("doc_count", 2)));
        assertThat(events.findById(e.getId()).orElseThrow().getProps())
                .containsEntry("doc_count", 2);
    }

    private static int indexOf(List<ChunkVectorRepository.ScoredChunk> hits, UUID id) {
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).chunkId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
