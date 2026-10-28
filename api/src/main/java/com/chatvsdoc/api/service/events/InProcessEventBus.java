package com.chatvsdoc.api.service.events;

import com.chatvsdoc.api.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Bus trong tiến trình: ID tăng dần toàn cục, vòng đệm N sự kiện / M phút để phát lại. */
@Component
public class InProcessEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventBus.class);

    private record Listener(UUID userId, Consumer<BusEvent> consumer) {}

    private final Deque<BusEvent> ring = new ArrayDeque<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Clock clock;
    private final int bufferSize;
    private final Duration bufferAge;
    private long seq;

    public InProcessEventBus(Clock clock, AppProperties props) {
        this.clock = clock;
        this.bufferSize = props.sse().bufferSize();
        this.bufferAge = Duration.ofMinutes(props.sse().bufferMinutes());
    }

    @Override
    public void publish(String type, Object data) {
        dispatch(append(type, data, null));
    }

    @Override
    public void publishTo(UUID userId, String type, Object data) {
        dispatch(append(type, data, userId));
    }

    private synchronized BusEvent append(String type, Object data, UUID target) {
        BusEvent e = new BusEvent(++seq, clock.instant(), type, data, target);
        ring.addLast(e);
        trim(e.ts());
        return e;
    }

    private void trim(Instant now) {
        while (!ring.isEmpty()
                && (ring.size() > bufferSize
                        || ring.peekFirst().ts().isBefore(now.minus(bufferAge)))) {
            ring.removeFirst();
        }
    }

    // Gọi listener ngoài khóa để một kết nối chậm không chặn người phát.
    private void dispatch(BusEvent e) {
        for (Listener l : listeners) {
            if (e.visibleTo(l.userId())) {
                try {
                    l.consumer().accept(e);
                } catch (RuntimeException ex) {
                    log.warn("event_listener_failed type={}", e.type(), ex);
                }
            }
        }
    }

    @Override
    public synchronized Subscription subscribe(
            UUID userId, Long lastEventId, Consumer<BusEvent> consumer) {
        Listener l = new Listener(userId, consumer);
        listeners.add(l);
        Runnable cancel = () -> listeners.remove(l);
        if (lastEventId == null || lastEventId == seq) {
            return new Subscription(List.of(), cancel);
        }
        trim(clock.instant());
        BusEvent oldest = ring.peekFirst();
        // ID lạ (lớn hơn hiện tại — máy chủ đã khởi động lại) hoặc đã trôi khỏi vòng đệm → resync.
        if (lastEventId > seq || oldest == null || oldest.id() > lastEventId + 1) {
            return new Subscription(null, cancel);
        }
        List<BusEvent> replay = new ArrayList<>();
        for (BusEvent e : ring) {
            if (e.id() > lastEventId && e.visibleTo(userId)) {
                replay.add(e);
            }
        }
        return new Subscription(replay, cancel);
    }

    @Override
    public int subscriberCount() {
        return listeners.size();
    }
}
