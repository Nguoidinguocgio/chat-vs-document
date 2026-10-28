package com.chatvsdoc.api.service.answer;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.domain.entity.AnswerType;
import com.chatvsdoc.api.service.retrieval.ContextBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** T13 mục 5: chuỗi được cắt mảnh ở MỌI vị trí có thể (1, 2 và nhiều điểm cắt). */
class StreamParserTest {

    static final List<ContextBlock> BLOCKS =
            IntStream.rangeClosed(1, 3)
                    .mapToObj(
                            n ->
                                    new ContextBlock(
                                            n,
                                            UUID.randomUUID(),
                                            "d" + n + ".pdf",
                                            n,
                                            UUID.randomUUID(),
                                            0,
                                            1,
                                            "x",
                                            0.9))
                    .toList();

    record Run(String text, List<Integer> citations, StreamParser.Outcome outcome, int deltas) {}

    static Run run(List<String> chunks) {
        List<AnswerEvent> events = new ArrayList<>();
        StreamParser p = new StreamParser(BLOCKS, events::add);
        chunks.forEach(p::feed);
        StreamParser.Outcome o = p.finish();
        StringBuilder text = new StringBuilder();
        List<Integer> cites = new ArrayList<>();
        int deltas = 0;
        for (AnswerEvent e : events) {
            if (e instanceof AnswerEvent.Delta d) {
                text.append(d.text());
                deltas++;
            } else if (e instanceof AnswerEvent.Citation c) {
                cites.add(c.index());
            }
        }
        return new Run(text.toString(), cites, o, deltas);
    }

    /** Mọi cách cắt chuỗi thành 1, 2 hoặc 3 mảnh và cắt từng ký tự — kết quả phải giống nhau. */
    static List<List<String>> splits(String s) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of(s));
        out.add(s.chars().mapToObj(c -> String.valueOf((char) c)).toList());
        for (int i = 1; i < s.length(); i++) {
            out.add(List.of(s.substring(0, i), s.substring(i)));
            for (int j = i + 1; j < s.length(); j++) {
                out.add(List.of(s.substring(0, i), s.substring(i, j), s.substring(j)));
            }
        }
        return out;
    }

    static void forAllSplits(String s, java.util.function.Consumer<Run> check) {
        for (List<String> chunks : splits(s)) {
            check.accept(run(chunks));
        }
    }

    @Test
    void ac022_validCitationsKeptAndEmittedOncePerIndex() {
        forAllSplits(
                "A [1][1][2] B.",
                r -> {
                    assertThat(r.text()).isEqualTo("A [1][1][2] B.");
                    assertThat(r.citations()).containsExactly(1, 2);
                    assertThat(r.outcome().answerType()).isEqualTo(AnswerType.answered);
                    assertThat(r.outcome().uncited()).isFalse();
                });
    }

    @Test
    void invalidCitationsAreRemovedAndDanglingBracketKept() {
        forAllSplits(
                "x[0]y[99]z[a] [3]",
                r -> {
                    assertThat(r.text()).isEqualTo("xyz [3]");
                    assertThat(r.citations()).containsExactly(3);
                });
        forAllSplits("cuối [2", r -> assertThat(r.text()).isEqualTo("cuối [2"));
        assertThat(run(List.of("[không phải trích dẫn dài] ok")).text())
                .isEqualTo("[không phải trích dẫn dài] ok");
    }

    @Test
    void ac023_notFoundEvenWhenSplitOrIndentedEmitsNoDelta() {
        forAllSplits(
                "  <<NOT_FOUND>>",
                r -> {
                    assertThat(r.outcome().answerType()).isEqualTo(AnswerType.not_found);
                    assertThat(r.deltas()).isZero();
                });
        assertThat(run(List.of("<<NOT_FOUND>> rác phía sau")).deltas()).isZero();
    }

    @Test
    void ac023_3_missingLineIsHiddenAndBecomesPartial() {
        forAllSplits(
                "Doanh thu 128 [1].\n<<MISSING>> Biên lợi nhuận",
                r -> {
                    assertThat(r.text()).isEqualTo("Doanh thu 128 [1].\n");
                    assertThat(r.outcome().answerType()).isEqualTo(AnswerType.partial);
                    assertThat(r.outcome().missing()).isEqualTo("Biên lợi nhuận");
                });
    }

    @Test
    void textWithoutCitationsIsAnsweredButFlaggedUncited() {
        StreamParser.Outcome o = run(List.of("Không có trích dẫn.")).outcome();
        assertThat(o.answerType()).isEqualTo(AnswerType.answered);
        assertThat(o.uncited()).isTrue();
        assertThat(run(List.of()).outcome().answerType()).isEqualTo(AnswerType.not_found);
        assertThat(run(List.of("   ")).outcome().answerType()).isEqualTo(AnswerType.not_found);
    }

    @Test
    void angleBracketsThatAreNotMarkersPassThrough() {
        forAllSplits("a < b và <i>", r -> assertThat(r.text()).isEqualTo("a < b và <i>"));
    }
}
