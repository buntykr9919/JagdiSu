package com.jdsu.quiz.analytics;

import com.jdsu.quiz.security.JwtService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class StudentAnalyticsController {
    private final StudentAnalyticsService analyticsService;

    public StudentAnalyticsController(StudentAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/sessions")
    public StudentAnalyticsService.SessionResponse recordSession(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @RequestBody StudentAnalyticsService.SessionRequest request
    ) {
        return analyticsService.recordSession(principal, request);
    }

    @GetMapping("/insights")
    public StudentAnalyticsService.InsightsResponse insights(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return analyticsService.insights(principal);
    }

    @GetMapping("/weak-topics")
    public List<StudentAnalyticsService.TopicMetric> weakTopics(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return analyticsService.weakTopics(principal);
    }

    @GetMapping("/recommended-tests")
    public List<String> recommendedTests(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return analyticsService.recommendedTests(principal);
    }

    @GetMapping("/revision-planner")
    public List<StudentAnalyticsService.RevisionItem> revisionPlanner(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return analyticsService.revisionPlanner(principal);
    }
}
