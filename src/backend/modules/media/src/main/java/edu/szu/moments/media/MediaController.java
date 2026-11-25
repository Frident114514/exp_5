package edu.szu.moments.media;

import edu.szu.moments.media.MediaModels.MediaStream;
import edu.szu.moments.media.MediaModels.UploadedMedia;
import edu.szu.moments.posts.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class MediaController {
    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadedMedia> upload(@RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.ok(service.upload(file));
    }

    @GetMapping("/media/{mediaId}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable long mediaId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        MediaStream stream = service.readPublishedContent(mediaId, range);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(stream.mimeType()));
        headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.setContentLength(stream.content().length);
        if (stream.partial()) {
            headers.add(HttpHeaders.CONTENT_RANGE, stream.range().contentRangeHeader());
            return new ResponseEntity<>(stream.content(), headers, HttpStatus.PARTIAL_CONTENT);
        }
        return new ResponseEntity<>(stream.content(), headers, HttpStatus.OK);
    }
}
