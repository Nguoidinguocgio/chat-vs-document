package com.chatvsdoc.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.service.auth.SessionService;
import com.chatvsdoc.api.service.events.EventBus;
import com.chatvsdoc.api.service.events.EventService;
import com.chatvsdoc.api.support.ApiClient;
import com.chatvsdoc.api.support.ApiClient.SseEvent;
import com.chatvsdoc.api.support.ApiClient.SseStream;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import com.chatvsdoc.api.support.MakeFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.MockMvc;

/** T09: kênh SSE {@code /api/events}. */
@IntegrationTest
class EventsControllerIT {

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired EventBus bus;
    @Autowired EventService events;
    @Autowired SessionService sessions;

    private User a;
    private User b;
    private ApiClient apiA;
    private ApiClient apiB;

    @BeforeEach
    void setUp() throws Exception {
        fixtures.resetDocuments();
        a = fixtures.user();
        b = fixtures.user(UserRole.member, false);
        apiA = new ApiClient(port, fixtures.login(mvc, a).getValue());
        apiB = new ApiClient(port, fixtures.login(mvc, b).getValue());
    }

    private static JsonNode json(SseEvent e) throws Exception {
        return ApiClient.JSON.readTree(e.data());
    }

    @Test
    void ac011_2_otherUserSeesCreatedThenStatusChangedWithinFiveSeconds() throws Exception {
        try (SseStream streamB = apiB.events(null)) {
            assertThat(streamB.status).isEqualTo(200);
            long t0 = System.currentTimeMillis();
            apiA.upload("Hợp đồng A.pdf", MakeFixtures.okVi5p());
            SseEvent created = streamB.next("document.created", 5000);
            assertThat(created).isNotNull();
            assertThat(created.id()).isNotBlank();
            JsonNode doc = json(created);
            assertThat(doc.path("name").asText()).isEqualTo("Hợp đồng A.pdf");
            assertThat(doc.path("status").asText()).isEqualTo("processing");
            assertThat(doc.path("can_delete").asBoolean())
                    .as("B không phải người tải, không phải admin")
                    .isFalse();
            SseEvent changed = streamB.next("document.status_changed", 5000);
            assertThat(changed).isNotNull();
            assertThat(json(changed).path("status").asText()).isEqualTo("ready");
            assertThat(System.currentTimeMillis() - t0).isLessThan(5000);
            assertThat(Long.parseLong(changed.id())).isGreaterThan(Long.parseLong(created.id()));
        }
    }

    @Test
    void reconnectWithLastEventIdReplaysMissedEventsWithoutDuplicates() throws Exception {
        String lastId;
        try (SseStream s = apiA.events(null)) {
            bus.publish("document.deleted", Map.of("id", "doc-1"));
            lastId = s.next("document.deleted", 3000).id();
        }
        bus.publish("document.deleted", Map.of("id", "doc-2"));
        bus.publish("document.deleted", Map.of("id", "doc-3"));
        try (SseStream s = apiA.events(lastId)) {
            List<SseEvent> got = s.drain(1500);
            assertThat(got)
                    .extracting(e -> json(e).path("id").asText())
                    .containsExactly("doc-2", "doc-3");
        }
    }

    @Test
    void unknownOrTooOldLastEventIdGetsResync() throws Exception {
        try (SseStream s = apiA.events("khong-phai-so")) {
            assertThat(s.next("resync", 3000)).isNotNull();
        }
        try (SseStream s = apiA.events("999999999")) {
            assertThat(s.next("resync", 3000)).isNotNull();
        }
        // Đẩy vòng đệm (500) trôi qua sự kiện cũ.
        String oldId;
        try (SseStream s = apiA.events(null)) {
            bus.publish("document.deleted", Map.of("id", "cu"));
            oldId = s.next("document.deleted", 3000).id();
        }
        for (int i = 0; i < 510; i++) {
            bus.publish("document.deleted", Map.of("id", "x" + i));
        }
        try (SseStream s = apiA.events(oldId)) {
            assertThat(s.next("resync", 3000)).isNotNull();
        }
    }

    @Test
    void heartbeatAndPingKeepConnectionAlive() throws Exception {
        // Profile test: heartbeat và ping mỗi 1 giây (mặc định 20 s / 10 s).
        try (SseStream s = apiA.events(null)) {
            assertThat(s.next("heartbeat", 3000)).isNotNull();
            Fixtures.sleep(1200);
            assertThat(s.commentCount()).isPositive();
        }
    }

    @Test
    void clientDisconnectReleasesSubscription() throws Exception {
        // Chờ kết nối của các test trước đóng hết (máy chủ phát hiện ngắt ở lần ping kế tiếp).
        for (int i = 0; i < 100 && events.openConnections() > 0; i++) {
            Fixtures.sleep(50);
        }
        int before = events.openConnections();
        assertThat(before).isZero();
        SseStream s = apiA.events(null);
        s.next("heartbeat", 3000);
        assertThat(events.openConnections()).isEqualTo(before + 1);
        s.close();
        // Máy chủ phát hiện ngắt ở lần ghi kế tiếp (ping 1 s).
        for (int i = 0; i < 100 && events.openConnections() > before; i++) {
            Fixtures.sleep(50);
        }
        assertThat(events.openConnections()).isEqualTo(before);
        assertThat(bus.subscriberCount()).isEqualTo(before);
    }

    @Test
    void sessionRevokedGoesOnlyToThatUserAndClosesTheirStream() throws Exception {
        try (SseStream sa = apiA.events(null);
                SseStream sb = apiB.events(null)) {
            sa.next("heartbeat", 3000);
            sb.next("heartbeat", 3000);
            sessions.revokeUserSessions(b.getId());
            assertThat(sb.next("session.revoked", 3000)).isNotNull();
            assertThat(sb.awaitEnd(3000)).isTrue();
            assertThat(sa.drain(1500)).noneMatch(e -> e.name().equals("session.revoked"));
        }
    }

    @Test
    void fourthConnectionClosesTheOldest() throws Exception {
        SseStream first = apiA.events(null);
        SseStream second = apiA.events(null);
        SseStream third = apiA.events(null);
        first.next("heartbeat", 3000);
        try (SseStream fourth = apiA.events(null)) {
            assertThat(first.awaitEnd(3000)).isTrue();
            assertThat(fourth.next("heartbeat", 3000)).isNotNull();
            assertThat(second.next("heartbeat", 3000)).isNotNull();
        } finally {
            first.close();
            second.close();
            third.close();
        }
    }

    @Test
    void rejectedWithoutLoginOrWhilePasswordChangeRequired() throws Exception {
        try (SseStream s = new ApiClient(port, null).events(null)) {
            assertThat(s.status).isEqualTo(401);
        }
        User temp = fixtures.user(UserRole.member, true);
        try (SseStream s = new ApiClient(port, fixtures.login(mvc, temp).getValue()).events(null)) {
            assertThat(s.status).isEqualTo(403);
        }
    }
}
