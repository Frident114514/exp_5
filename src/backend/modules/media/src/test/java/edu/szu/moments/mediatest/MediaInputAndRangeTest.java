package edu.szu.moments.mediatest;

import java.nio.charset.StandardCharsets;

import edu.szu.moments.media.*;
import edu.szu.moments.media.MediaModels.ByteRange;
import edu.szu.moments.media.MediaModels.InspectedFile;
import edu.szu.moments.posts.PostModels.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;

class MediaInputAndRangeTest {
    private MediaProperties properties;
    private MediaInspector inspector;

    @BeforeEach
    void setUp() {
        properties = new MediaProperties();
        properties.setMaxImageBytes(2048);
        properties.setMaxVideoBytes(4096);
        inspector = new MediaInspector(properties);
    }

    static byte[] jpegBytes(int size) {
        byte[] data = new byte[Math.max(size, 16)];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        data[3] = (byte) 0xE0;
        for (int i = 4; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    static byte[] pngBytes(int size) {
        byte[] data = new byte[Math.max(size, 16)];
        byte[] sig = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(sig, 0, data, 0, sig.length);
        for (int i = 8; i < data.length; i++) {
            data[i] = (byte) ((i * 7) % 253);
        }
        return data;
    }

    static byte[] webpBytes(int size) {
        byte[] data = new byte[Math.max(size, 16)];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, data, 8, 4);
        for (int i = 12; i < data.length; i++) {
            data[i] = (byte) ((i * 11) % 255);
        }
        return data;
    }

    static byte[] mp4Bytes(int size) {
        byte[] data = new byte[Math.max(size, 32)];
        data[3] = 0x18;
        System.arraycopy("ftypisom".getBytes(StandardCharsets.US_ASCII), 0, data, 4, 8);
        for (int i = 12; i < data.length; i++) {
            data[i] = (byte) (i & 0xFF);
        }
        return data;
    }

    @Test
    void acceptsValidJpegPngWebpAndMp4AtExactSizeBoundary() {
        InspectedFile jpg = inspector.inspect(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes(2048)));
        assertThat(jpg.type()).isEqualTo(MediaType.IMAGE);
        assertThat(jpg.extension()).isEqualTo("jpg");

        InspectedFile png = inspector.inspect(new MockMultipartFile("file", "b.png", "image/png", pngBytes(2048)));
        assertThat(png.type()).isEqualTo(MediaType.IMAGE);
        assertThat(png.extension()).isEqualTo("png");

        InspectedFile webp = inspector.inspect(new MockMultipartFile("file", "c.webp", "image/webp", webpBytes(2048)));
        assertThat(webp.type()).isEqualTo(MediaType.IMAGE);
        assertThat(webp.extension()).isEqualTo("webp");

        InspectedFile mp4 = inspector.inspect(new MockMultipartFile("file", "d.mp4", "video/mp4", mp4Bytes(4096)));
        assertThat(mp4.type()).isEqualTo(MediaType.VIDEO);
        assertThat(mp4.extension()).isEqualTo("mp4");
    }

    @Test
    void rejectsImageAndVideoExceedingConfiguredLimitsWith413() {
        MockMultipartFile bigImg = new MockMultipartFile("file", "big.jpg", "image/jpeg", jpegBytes(2049));
        assertThatThrownBy(() -> inspector.inspect(bigImg))
            .isInstanceOf(MediaFailure.class)
            .extracting("status", "code")
            .containsExactly(413, "FILE_TOO_LARGE");

        MockMultipartFile bigVid = new MockMultipartFile("file", "big.mp4", "video/mp4", mp4Bytes(4097));
        assertThatThrownBy(() -> inspector.inspect(bigVid))
            .isInstanceOf(MediaFailure.class)
            .extracting("status", "code")
            .containsExactly(413, "FILE_TOO_LARGE");
    }

    @Test
    void rejectsNullOrEmptyFileWith400() {
        assertThatThrownBy(() -> inspector.inspect(null))
            .isInstanceOf(MediaFailure.class)
            .extracting("status", "code")
            .containsExactly(400, "VALIDATION_ERROR");

        MockMultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> inspector.inspect(empty))
            .isInstanceOf(MediaFailure.class)
            .extracting("status", "code")
            .containsExactly(400, "VALIDATION_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "text/plain", "video/quicktime", "application/octet-stream"})
    void rejectsUnsupportedDeclaredMimeWith415(String mime) {
        MockMultipartFile file = new MockMultipartFile("file", "test.bin", mime, jpegBytes(128));
        assertThatThrownBy(() -> inspector.inspect(file))
            .isInstanceOf(MediaFailure.class)
            .extracting("status", "code")
            .containsExactly(415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void rejectsSpoofedMagicBytesAndMismatchedExtensionsWith415() {
        MockMultipartFile fakeJpg = new MockMultipartFile(
            "file", "fake.jpg", "image/jpeg", "not a real jpeg".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> inspector.inspect(fakeJpg))
            .isInstanceOf(MediaFailure.class)
            .extracting("status", "code")
            .containsExactly(415, "UNSUPPORTED_MEDIA_TYPE");

        MockMultipartFile mimeMismatch = new MockMultipartFile(
            "file", "mismatch.png", "image/png", jpegBytes(128));
        assertThatThrownBy(() -> inspector.inspect(mimeMismatch))
            .isInstanceOf(MediaFailure.class)
            .extracting("status", "code")
            .containsExactly(415, "UNSUPPORTED_MEDIA_TYPE");

        MockMultipartFile extMismatch = new MockMultipartFile(
            "file", "evil.exe", "image/jpeg", jpegBytes(128));
        assertThatThrownBy(() -> inspector.inspect(extMismatch))
            .isInstanceOf(MediaFailure.class)
            .extracting("status", "code")
            .containsExactly(415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void sanitizesPathTraversalInOriginalFilenameAndGeneratesSafeStorageKey() {
        MockMultipartFile traversal = new MockMultipartFile(
            "file", "../../etc/secret/campus.jpeg", "image/jpeg", jpegBytes(128));
        InspectedFile inspected = inspector.inspect(traversal);
        assertThat(inspected.originalName()).isEqualTo("campus.jpeg");
        assertThat(inspected.extension()).isEqualTo("jpg");
    }

    @Test
    void parsesValidByteRangesCorrectly() {
        ByteRange first100 = RangeParser.parse("bytes=0-99", 1000);
        assertThat(first100.start()).isZero();
        assertThat(first100.end()).isEqualTo(99);
        assertThat(first100.length()).isEqualTo(100);
        assertThat(first100.contentRangeHeader()).isEqualTo("bytes 0-99/1000");

        ByteRange openEnded = RangeParser.parse("bytes=750-", 1000);
        assertThat(openEnded.start()).isEqualTo(750);
        assertThat(openEnded.end()).isEqualTo(999);
        assertThat(openEnded.length()).isEqualTo(250);

        ByteRange suffix = RangeParser.parse("bytes=-120", 1000);
        assertThat(suffix.start()).isEqualTo(880);
        assertThat(suffix.end()).isEqualTo(999);
        assertThat(suffix.length()).isEqualTo(120);

        ByteRange clamped = RangeParser.parse("bytes=900-5000", 1000);
        assertThat(clamped.start()).isEqualTo(900);
        assertThat(clamped.end()).isEqualTo(999);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "bytes=1000-1001",
        "bytes=500-200",
        "bytes=-0",
        "bytes=-",
        "bytes=0-10,20-30",
        "items=0-10",
        "bytes=abc-def"
    })
    void rejectsInvalidOrUnsatisfiableRangeWith416(String rangeHeader) {
        assertThatThrownBy(() -> RangeParser.parse(rangeHeader, 1000))
            .isInstanceOf(MediaFailure.class)
            .extracting("status", "code", "totalSize")
            .containsExactly(416, "RANGE_NOT_SATISFIABLE", 1000L);
    }
}
