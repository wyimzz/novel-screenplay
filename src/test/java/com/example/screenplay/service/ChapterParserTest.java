package com.example.screenplay.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterParserTest {

    private final ChapterParser parser = new ChapterParser();

    @Test
    void parsesAtLeastThreeChineseChapters() {
        String source = """
                第一章 雨夜
                林舟走进咖啡馆。
                第二章 名单
                苏禾拿出一份名单。
                第三章 钟楼
                两人来到钟楼。
                """;

        assertThat(parser.parse(source))
                .hasSize(3)
                .extracting("id")
                .containsExactly("chapter_01", "chapter_02", "chapter_03");
    }

    @Test
    void rejectsSourceWithFewerThanThreeChapters() {
        String source = """
                第一章 开始
                第一段正文。
                第二章 结束
                第二段正文。
                """;

        assertThatThrownBy(() -> parser.parse(source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少 3 个章节");
    }
}
