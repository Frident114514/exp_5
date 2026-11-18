package edu.szu.moments.poststest;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.moments.posts.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class PostInputTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PostPayloadParser parser = new PostPayloadParser();

    @ParameterizedTest
    @ValueSource(strings = {
        "[]", "null", "{\"text\":null}", "{\"text\":1}", "{\"tags\":null}",
        "{\"tags\":[\" \"]}", "{\"tags\":[1]}", "{\"tags\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}",
        "{\"mediaIds\":[0]}", "{\"mediaIds\":[1,1]}", "{\"mediaIds\":[1.5]}",
        "{\"mediaIds\":[9223372036854775808]}", "{\"mediaIds\":null}", "{\"authorId\":2}"
    })
    void rejectsMalformedInput(String body) throws Exception {
        var node = json.readTree(body);
        assertThatThrownBy(() -> parser.parse(node, false)).isInstanceOf(PostFailure.class);
    }

    @Test
    void normalizesTagsAndDistinguishesOmittedFields() throws Exception {
        var change = parser.parse(json.readTree("{\"text\":\" 新内容 \",\"tags\":[\"校园\",\" 校园 \",\"School\",\"school\"]}"), true);
        assertThat(change.text()).isEqualTo("新内容");
        assertThat(change.mediaIds()).isNull();
        assertThat(change.tags()).containsExactly("校园", "School", "school");
    }

    @Test
    void usesUnicodeCodePointsForTagLimit() throws Exception {
        String tag = "🌸".repeat(20);
        var body = json.createObjectNode();
        body.putArray("tags").add(tag);
        assertThat(parser.parse(body, false).tags()).containsExactly(tag);
        body.putArray("tags").add(tag + "🌸");
        assertThatThrownBy(() -> parser.parse(body, false)).isInstanceOf(PostFailure.class);
    }

    @Test
    void checksTextLimitAndEmptyPatch() throws Exception {
        var body = json.createObjectNode().put("text", "文".repeat(10001));
        assertThatThrownBy(() -> parser.parse(body, false)).isInstanceOf(PostFailure.class);
        assertThatThrownBy(() -> parser.parse(json.createObjectNode(), true)).isInstanceOf(PostFailure.class);
    }

    @Test
    void mapsBeijingDaysToHalfOpenUtcRange() {
        var filter = PostFilter.parse("2026-11-18", "2026-11-18", " 校园 ", null, null, 1, 10);
        assertThat(filter.from()).isEqualTo(Instant.parse("2026-11-17T16:00:00Z"));
        assertThat(filter.until()).isEqualTo(Instant.parse("2026-11-18T16:00:00Z"));
        assertThat(filter.tag()).isEqualTo("校园");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-1-01", "", "2026-11-18T00:00:00", "0999-01-01"})
    void rejectsInvalidDates(String date) {
        assertThatThrownBy(() -> PostFilter.parse(date, null, null, null, null, 1, 10)).isInstanceOf(PostFailure.class);
    }

    @Test
    void rejectsReversedDatesAndBadPaging() {
        assertThatThrownBy(() -> PostFilter.parse("2026-11-19", "2026-11-18", null, null, null, 1, 10)).isInstanceOf(PostFailure.class);
        assertThatThrownBy(() -> PostFilter.parse(null, null, null, null, null, 0, 10)).isInstanceOf(PostFailure.class);
        assertThatThrownBy(() -> PostFilter.parse(null, null, null, null, null, 1, 51)).isInstanceOf(PostFailure.class);
        assertThatThrownBy(() -> PostFilter.parse(null, null, null, null, null, Integer.MAX_VALUE, 50)).isInstanceOf(PostFailure.class);
    }
}

