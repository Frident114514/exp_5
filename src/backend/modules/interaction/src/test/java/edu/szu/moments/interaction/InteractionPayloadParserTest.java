package edu.szu.moments.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import edu.szu.moments.posts.PostFailure;

class InteractionPayloadParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final InteractionPayloadParser parser = new InteractionPayloadParser();

    @Test
    void acceptsOnlyIntegerScoresFromOneThroughFive() throws Exception {
        assertThat(parser.parseScore(mapper.readTree("{\"score\":1}"))).isEqualTo(1);
        assertThat(parser.parseScore(mapper.readTree("{\"score\":5}"))).isEqualTo(5);
        for (String json : new String[] {
                "{\"score\":0}", "{\"score\":6}", "{\"score\":4.5}",
                "{\"score\":\"4\"}", "{\"score\":2147483648}", "{}",
                "{\"score\":4,\"userId\":22}", "null", "[]"}) {
            assertThatThrownBy(() -> parser.parseScore(mapper.readTree(json)))
                .as(json).isInstanceOf(PostFailure.class);
        }
    }

    @Test
    void trimsAndCountsCommentUnicodeCodePoints() throws Exception {
        assertThat(parser.parseComment(mapper.readTree("{\"content\":\"  好😀  \"}")))
            .isEqualTo("好😀");
        String emoji = parser.parseComment(mapper.readTree("{\"content\":\"😀\"}"));
        assertThat(emoji.codePointCount(0, emoji.length())).isEqualTo(1);
        String fiveHundredEmoji = parser.parseComment(mapper.readTree(mapper.writeValueAsString(
            Map.of("content", "😀".repeat(500)))));
        assertThat(fiveHundredEmoji.codePointCount(0, fiveHundredEmoji.length())).isEqualTo(500);
    }

    @Test
    void rejectsBlankNonTextAndOutOfRangeComments() throws Exception {
        for (String json : new String[] {
                "{\"content\":\"  \\t\\n \"}",
                mapper.writeValueAsString(Map.of("content", "😀".repeat(501))),
                "{\"content\":1}", "{}", "{\"content\":\"x\",\"userId\":22}", "null"}) {
            assertThatThrownBy(() -> parser.parseComment(mapper.readTree(json)))
                .as(json).isInstanceOf(PostFailure.class);
        }
    }
}
