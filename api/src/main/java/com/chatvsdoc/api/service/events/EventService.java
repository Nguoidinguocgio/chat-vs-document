package com.chatvsdoc.api.service.events;

import com.chatvsdoc.api.config.AppProperties;
import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.events.EventBus.BusEvent;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Kết nối SSE {@code /api/events} (T09 mục 3.2): phát lại theo {@code Last-Event-ID} hoặc {@code
 * resync}, {@code heartbeat} định kỳ + dòng {@code : ping} chống proxy đóng kết nối, tối đa N kết
 * nối mỗi người (đóng kết nối cũ nhất), {@code session.revoked} đóng kết nối.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventBus bus;
    private final AppProperties.Sse cfg;
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private final Map<UUID, Deque<Connection>> byUser = new ConcurrentHashMap<>();
    private final Set<Connection> all = ConcurrentHashMap.newKeySet();

    public EventService(EventBus bus, AppProperties props) {
        this.bus = bus;
        this.cfg = props.sse();
        timer.scheduleAtFixedRate(
                this::ping, cfg.pingSeconds(), cfg.pingSeconds(), TimeUnit.SECONDS);
        timer.scheduleAtFixedRate(
                this::heartbeat, cfg.heartbeatSeconds(), cfg.heartbeatSeconds(), TimeUnit.SECONDS);
    }

    /** Một kết nối SSE; mọi lần gửi tuần tự hóa qua khóa của chính nó. */
    private final class Connection {
        final AuthUser user;
        final SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        long lastSentId;
        Runnable cancel = () -> {};
        boolean closed;

        Connection(AuthUser user) {
            this.user = user;
        }

        synchronized void send(BusEvent e) {
            if (closed || e.id() <= lastSentId) {
                return;
            }
            lastSentId = e.id();
            write(
                    SseEmitter.event()
                            .id(Long.toString(e.id()))
                            .name(e.type())
                            .data(e.dataFor(user), MediaType.APPLICATION_JSON));
            if ("session.revoked".equals(e.type())) {
                close();
            }
        }

        synchronized void write(SseEmitter.SseEventBuilder event) {
            if (closed) {
                return;
            }
            try {
                emitter.send(event);
            } catch (IOException | RuntimeException ex) {
                // Client đã ngắt hoặc emitter đã đóng: dọn kết nối, không để lỗi lan ra bộ định
                // thời.
                close();
            }
        }

        synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            release();
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // emitter đã hỏng sẵn
            }
        }

        void release() {
            cancel.run();
            all.remove(this);
            Deque<Connection> mine = byUser.get(user.id());
            if (mine != null) {
                synchronized (mine) {
                    mine.remove(this);
                }
            }
        }
    }

    public SseEmitter open(AuthUser user, Long lastEventId) {
        Connection c = new Connection(user);
        c.emitter.onCompletion(c::release);
        c.emitter.onTimeout(c::close);
        c.emitter.onError(ex -> c.release());
        Connection evicted = null;
        Deque<Connection> mine = byUser.computeIfAbsent(user.id(), k -> new ArrayDeque<>());
        synchronized (mine) {
            mine.addLast(c);
            if (mine.size() > cfg.maxConnectionsPerUser()) {
                evicted = mine.peekFirst();
            }
        }
        if (evicted != null) {
            evicted.close();
        }
        all.add(c);
        // Gửi ngay một dòng chú thích để header phản hồi được đẩy đi (client không phải chờ
        // heartbeat).
        c.write(SseEmitter.event().comment("connected"));
        // Giữ khóa kết nối trong lúc phát lại: sự kiện mới đến chờ rồi được lọc trùng theo ID.
        synchronized (c) {
            EventBus.Subscription sub = bus.subscribe(user.id(), lastEventId, c::send);
            c.cancel = sub.cancel();
            List<BusEvent> replay = sub.replay();
            if (replay == null) {
                c.write(
                        SseEmitter.event()
                                .name("resync")
                                .data(Map.of(), MediaType.APPLICATION_JSON));
            } else {
                replay.forEach(c::send);
            }
        }
        return c.emitter;
    }

    // Ngoại lệ thoát khỏi tác vụ định kỳ sẽ hủy mọi lần chạy sau → luôn bắt lại.
    private void ping() {
        safely(() -> all.forEach(c -> c.write(SseEmitter.event().comment("ping"))));
    }

    private void heartbeat() {
        safely(
                () ->
                        all.forEach(
                                c ->
                                        c.write(
                                                SseEmitter.event()
                                                        .name("heartbeat")
                                                        .data(
                                                                Map.of(),
                                                                MediaType.APPLICATION_JSON))));
    }

    private static void safely(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.warn("sse_timer_task_failed", e);
        }
    }

    /** Số kết nối đang mở (kiểm tra rò rỉ trong test). */
    public int openConnections() {
        return all.size();
    }

    @PreDestroy
    void shutdown() {
        timer.shutdownNow();
        all.forEach(Connection::close);
    }
}
