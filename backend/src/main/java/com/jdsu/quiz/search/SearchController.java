package com.jdsu.quiz.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/notes")
    public SearchService.SearchResponse notes(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "20") int limit) {
        return searchService.search("notes", q, limit);
    }

    @GetMapping("/questions")
    public SearchService.SearchResponse questions(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "20") int limit) {
        return searchService.search("questions", q, limit);
    }

    @GetMapping("/courses")
    public SearchService.SearchResponse courses(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "20") int limit) {
        return searchService.search("courses", q, limit);
    }

    @GetMapping("/community")
    public SearchService.SearchResponse community(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "20") int limit) {
        return searchService.search("community", q, limit);
    }

    @GetMapping("/current-affairs")
    public SearchService.SearchResponse currentAffairs(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "20") int limit) {
        return searchService.search("current-affairs", q, limit);
    }
}
