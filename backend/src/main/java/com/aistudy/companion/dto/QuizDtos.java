package com.aistudy.companion.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class QuizDtos {
    public record StartQuizRequest(Integer questionCount) {}

    public record QuestionResponse(String id, String type, String prompt, List<String> options,
                                    String conceptName, int difficulty, int orderIndex) {}

    public record StartQuizResponse(String quizAttemptId, QuestionResponse firstQuestion, int totalPlanned) {}

    public record SubmitAnswerRequest(@NotBlank String answer) {}

    public record AnswerResultResponse(boolean isCorrect, Double score, String feedback, String correctAnswer,
                                        QuestionResponse nextQuestion, boolean quizComplete, Double finalScore) {}
}
