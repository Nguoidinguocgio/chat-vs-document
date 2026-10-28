package com.chatvsdoc.api.support;

import com.chatvsdoc.api.port.EmbeddingPort;
import com.chatvsdoc.api.port.fake.FakeEmbedding;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedding giả dùng trong test tích hợp: mặc định như {@link FakeEmbedding}; có thể bật lỗi tạm
 * thời liên tục, độ trễ mỗi lô, hoặc trả sai số chiều từ lô thứ N (tiêm lỗi khi ghi chunk).
 */
public class SwitchableEmbedding implements EmbeddingPort {

    private final FakeEmbedding fake = new FakeEmbedding(1024);
    private volatile boolean failing;
    private volatile long delayMs;
    private volatile int wrongDimFromBatch = -1;
    private final AtomicInteger calls = new AtomicInteger();

    public void reset() {
        failing = false;
        delayMs = 0;
        wrongDimFromBatch = -1;
        calls.set(0);
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public void setWrongDimFromBatch(int batch) {
        this.wrongDimFromBatch = batch;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public int dimension() {
        return 1024;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        int n = calls.getAndIncrement();
        if (failing) {
            throw new EmbeddingException("simulated outage", true, null);
        }
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (wrongDimFromBatch >= 0 && n >= wrongDimFromBatch) {
            return texts.stream().map(t -> new float[] {1f, 0f, 0f}).toList();
        }
        return fake.embed(texts);
    }
}
