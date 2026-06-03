package com.jdsu.quiz.jobs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class AsyncJobController {
    private final AsyncJobService asyncJobService;

    public AsyncJobController(AsyncJobService asyncJobService) {
        this.asyncJobService = asyncJobService;
    }

    @PostMapping
    public AsyncJobService.JobResponse enqueue(@RequestBody JobRequest request) {
        return asyncJobService.enqueue(request.jobType(), request.payload());
    }

    @GetMapping("/metrics")
    public Map<String, Integer> metrics() {
        return asyncJobService.counts();
    }

    public record JobRequest(String jobType, Map<String, Object> payload) {
    }
}
