import React, { useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';

export default function QuizPage() {
  const { projectId } = useParams();
  const [attemptId, setAttemptId] = useState(null);
  const [question, setQuestion] = useState(null);
  const [totalPlanned, setTotalPlanned] = useState(0);
  const [answeredCount, setAnsweredCount] = useState(0);
  const [selected, setSelected] = useState(null);
  const [openAnswer, setOpenAnswer] = useState('');
  const [result, setResult] = useState(null);
  const [finalScore, setFinalScore] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function start() {
    setError(null);
    setBusy(true);
    try {
      const res = await api.startQuiz(projectId, 5);
      setAttemptId(res.quizAttemptId);
      setQuestion(res.firstQuestion);
      setTotalPlanned(res.totalPlanned);
      setAnsweredCount(0);
      setResult(null);
      setFinalScore(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    const answer = question.type === 'MCQ' ? selected : openAnswer;
    if (!answer) return;
    setBusy(true);
    setError(null);
    try {
      const res = await api.submitAnswer(projectId, attemptId, question.id, answer);
      setResult(res);
      setAnsweredCount((c) => c + 1);
      if (res.quizComplete) {
        setFinalScore(res.finalScore);
        setQuestion(null);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  function next() {
    setQuestion(result.nextQuestion);
    setResult(null);
    setSelected(null);
    setOpenAnswer('');
  }

  if (!attemptId) {
    return (
      <div>
        <h1>Adaptive Quiz</h1>
        <p className="muted" style={{ maxWidth: 640 }}>
          Questions are chosen based on your current mastery per concept — not simply "wrong then easier,
          right then harder." Weaker or untested concepts come up more often, and difficulty is set to match
          each concept's mastery band.
        </p>
        {error && <p className="error-text">{error}</p>}
        <button className="btn btn-primary" onClick={start} disabled={busy}>{busy ? 'Starting…' : 'Start a 5-question quiz'}</button>
      </div>
    );
  }

  if (finalScore !== null) {
    return (
      <div className="card" style={{ maxWidth: 480 }}>
        <h2>Quiz complete</h2>
        <p style={{ fontFamily: 'var(--font-serif)', fontSize: '2rem', margin: '0.2em 0' }}>{Math.round(finalScore * 100)}%</p>
        <p className="muted">Mastery has been updated for the concepts covered, and a fresh recommendation is waiting on your dashboard.</p>
        <button className="btn btn-primary" onClick={start}>Take another quiz</button>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '0.6em' }}>
        <h2 style={{ margin: 0 }}>Question {answeredCount + 1} of {totalPlanned}</h2>
        <span className="muted" style={{ fontSize: '0.85rem' }}>
          {question && Array.from({ length: question.difficulty }).map((_, i) => <span key={i} className="difficulty-dot" />)}
          difficulty {question?.difficulty}/5 · {question?.conceptName}
        </span>
      </div>

      <div className="card" style={{ maxWidth: 640 }}>
        <p style={{ fontSize: '1.05rem' }}>{question.prompt}</p>

        {question.type === 'MCQ' ? (
          <div style={{ marginTop: '1em' }}>
            {question.options.map((opt) => {
              let cls = 'option-btn';
              if (result) {
                if (opt === result.correctAnswer) cls += ' correct';
                else if (opt === selected) cls += ' incorrect';
              } else if (opt === selected) {
                cls += ' selected';
              }
              return (
                <button key={opt} type="button" className={cls} disabled={!!result}
                        onClick={() => setSelected(opt)}>{opt}</button>
              );
            })}
          </div>
        ) : (
          <textarea rows={4} style={{ marginTop: '1em' }} value={openAnswer} disabled={!!result}
                    onChange={(e) => setOpenAnswer(e.target.value)} placeholder="Type your explanation…" />
        )}

        {error && <p className="error-text">{error}</p>}

        {!result ? (
          <button className="btn btn-primary" style={{ marginTop: '1.2em' }} disabled={busy || (!selected && !openAnswer)} onClick={submit}>
            {busy ? 'Submitting…' : 'Submit answer'}
          </button>
        ) : (
          <div style={{ marginTop: '1.2em' }}>
            <p style={{ fontWeight: 600, color: result.isCorrect ? 'var(--primary)' : 'var(--danger)' }}>
              {result.isCorrect ? 'Correct' : 'Not quite'}{result.score != null ? ` — score ${Math.round(result.score * 100)}%` : ''}
            </p>
            <p className="muted">{result.feedback}</p>
            <button className="btn btn-primary" onClick={next}>Continue</button>
          </div>
        )}
      </div>
    </div>
  );
}
