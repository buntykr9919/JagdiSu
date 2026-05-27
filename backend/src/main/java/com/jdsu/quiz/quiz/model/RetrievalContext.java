package com.jdsu.quiz.quiz.model;

import java.util.List;

public record RetrievalContext(
        List<SourceQuestion> examples,
        String corpusSummary,
        String generationInstruction
) {
}

