package com.aistudy.companion.repository;

import com.aistudy.companion.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, String> {
    List<QuizQuestion> findByQuizAttemptIdOrderByOrderIndexAsc(String quizAttemptId);
    List<QuizQuestion> findByQuizAttempt_Project_IdAndConcept_Id(String projectId, String conceptId);
}
