package edu.szu.moments.interaction;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import edu.szu.moments.posts.PostFailure;

@Component
public class InteractionPayloadParser {
    private static final Set<String> RATING_FIELDS = Set.of("score");
    private static final Set<String> COMMENT_FIELDS = Set.of("content");

    public int parseScore(JsonNode body) {
        requireObject(body, "评分请求必须是对象");
        rejectUnknownFields(body, RATING_FIELDS);
        JsonNode score = body.get("score");
        if (score == null || !score.isIntegralNumber() || !score.canConvertToInt()
                || score.intValue() < 1 || score.intValue() > 5) {
            throw PostFailure.invalid("score 必须为 1–5 的 JSON 整数");
        }
        return score.intValue();
    }

    public String parseComment(JsonNode body) {
        requireObject(body, "评论请求必须是对象");
        rejectUnknownFields(body, COMMENT_FIELDS);
        JsonNode content = body.get("content");
        if (content == null || !content.isTextual()) {
            throw PostFailure.invalid("content 必须为字符串");
        }
        String value = content.textValue().strip();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 500) {
            throw PostFailure.invalid("评论长度须为 1–500 字符");
        }
        return value;
    }

    private static void requireObject(JsonNode body, String message) {
        if (body == null || !body.isObject()) {
            throw PostFailure.invalid(message);
        }
    }

    private static void rejectUnknownFields(JsonNode body, Set<String> allowed) {
        body.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw PostFailure.invalid("不支持的字段：" + name);
            }
        });
    }
}
