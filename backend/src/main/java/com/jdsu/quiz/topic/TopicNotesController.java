package com.jdsu.quiz.topic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class TopicNotesController {
    private final TopicNotesService topicNotesService;

    public TopicNotesController(TopicNotesService topicNotesService) {
        this.topicNotesService = topicNotesService;
    }

    @PostMapping("/notes")
    public TopicNotesResponse generateNotes(@Valid @RequestBody TopicNotesRequest request) {
        return topicNotesService.generate(request);
    }

    public record TopicNotesRequest(@NotBlank String topic, @NotBlank String language) {
    }

    public record TopicNotesResponse(String topic, String language, String notes) {
    }
}
