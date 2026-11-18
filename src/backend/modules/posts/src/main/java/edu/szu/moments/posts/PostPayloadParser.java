package edu.szu.moments.posts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class PostPayloadParser {
    private static final Set<String> FIELDS = Set.of("text", "mediaIds", "tags");

    public PostChange parse(JsonNode body, boolean patch) {
        if (body == null || !body.isObject() || (patch && body.isEmpty())) {
            throw PostFailure.invalid("请提交有效的动态对象");
        }
        body.fieldNames().forEachRemaining(name -> {
            if (!FIELDS.contains(name)) {
                throw PostFailure.invalid("不支持的字段：" + name);
            }
        });
        String text = body.has("text") ? text(body.get("text")) : patch ? null : "";
        List<Long> mediaIds = body.has("mediaIds") ? ids(body.get("mediaIds")) : patch ? null : List.of();
        List<String> tags = body.has("tags") ? tags(body.get("tags")) : patch ? null : List.of();
        return new PostChange(text, mediaIds, tags);
    }

    private String text(JsonNode value) {
        if (!value.isTextual()) {
            throw PostFailure.invalid("text 必须为字符串");
        }
        String text = value.textValue().strip();
        if (text.codePointCount(0, text.length()) > 10000) {
            throw PostFailure.invalid("文字不能超过 10000 字符");
        }
        return text;
    }

    private List<Long> ids(JsonNode value) {
        if (!value.isArray() || value.size() > 9) {
            throw PostFailure.invalid("媒体列表最多包含 9 项");
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isIntegralNumber() || !item.canConvertToLong() || item.longValue() <= 0) {
                throw PostFailure.invalid("媒体 ID 必须为正整数");
            }
            ids.add(item.longValue());
        }
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            throw PostFailure.invalid("媒体 ID 不能重复");
        }
        return List.copyOf(ids);
    }

    private List<String> tags(JsonNode value) {
        if (!value.isArray() || value.size() > 5) {
            throw PostFailure.invalid("标签最多 5 项");
        }
        Set<String> tags = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw PostFailure.invalid("标签必须为字符串");
            }
            String tag = item.textValue().strip();
            int length = tag.codePointCount(0, tag.length());
            if (length < 1 || length > 20) {
                throw PostFailure.invalid("标签长度须为 1–20 字符");
            }
            tags.add(tag);
        }
        return List.copyOf(tags);
    }
}

