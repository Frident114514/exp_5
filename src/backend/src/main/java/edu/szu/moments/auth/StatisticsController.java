package edu.szu.moments.auth;

import edu.szu.moments.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
class StatisticsController {
    private final StatisticsService service;
    StatisticsController(StatisticsService service) { this.service=service; }

    @GetMapping("/statistics")
    ApiResponse<StatisticsService.Statistics> statistics(@RequestParam(required=false) String from,
                                                        @RequestParam(required=false) String to) {
        return ApiResponse.ok(service.get(from,to));
    }
}
