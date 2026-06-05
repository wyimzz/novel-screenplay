package com.example.screenplay.service;

import com.example.screenplay.model.Chapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ChapterParser {

    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "(?m)^\\s*((?:第[零〇一二两三四五六七八九十百千0-9]+章)|(?:Chapter\\s+\\d+))[^\\r\\n]*\\s*$",
            Pattern.CASE_INSENSITIVE);

    public List<Chapter> parse(String source) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n').trim();
        Matcher matcher = CHAPTER_HEADING.matcher(normalized);
        List<HeadingPosition> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new HeadingPosition(matcher.group(1).trim(), matcher.start(), matcher.end()));
        }

        if (headings.size() < 3) {
            throw new IllegalArgumentException("未识别到至少 3 个章节。请使用“第一章”或“Chapter 1”作为章节标题。");
        }

        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            HeadingPosition current = headings.get(i);
            int contentEnd = i + 1 < headings.size() ? headings.get(i + 1).start() : normalized.length();
            String content = normalized.substring(current.end(), contentEnd).trim();
            if (content.isBlank()) {
                throw new IllegalArgumentException("章节“" + current.title() + "”没有正文。");
            }
            chapters.add(new Chapter(
                    "chapter_%02d".formatted(i + 1),
                    i + 1,
                    current.title(),
                    content));
        }
        return chapters;
    }

    private record HeadingPosition(String title, int start, int end) {
    }
}
