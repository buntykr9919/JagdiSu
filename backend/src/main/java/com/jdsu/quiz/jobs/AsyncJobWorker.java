package com.jdsu.quiz.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AsyncJobWorker {
    private final AsyncJobService asyncJobService;

    public AsyncJobWorker(AsyncJobService asyncJobService) {
        this.asyncJobService = asyncJobService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.poll-delay-ms:5000}")
    public void processJobs() {
        for (AsyncJobService.JobRecord job : asyncJobService.claim(5)) {
            try {
                // Real workers can branch by jobType and call OCR, notification, analytics, or backup services.
                asyncJobService.complete(job.id());
            } catch (Exception exception) {
                asyncJobService.fail(job.id(), exception.getMessage());
            }
        }
    }
}
