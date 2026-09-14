import React, { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';

export default function TutorPage() {
  const { projectId } = useParams();
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const scrollRef = useRef(null);

  useEffect(() => {
    api.tutorHistory(projectId).then(setMessages).catch(() => {});
  }, [projectId]);

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [messages]);

  async function send(e) {
    e.preventDefault();
    const text = input.trim();
    if (!text || sending) return;
    setError(null);
    setInput('');
    setMessages((prev) => [...prev, { id: `temp-${Date.now()}`, role: 'USER', content: text, citations: [], createdAt: new Date().toISOString() }]);
    setSending(true);
    try {
      const reply = await api.tutorAsk(projectId, text);
      setMessages((prev) => [...prev, reply]);
    } catch (err) {
      setError(err.message);
    } finally {
      setSending(false);
    }
  }

  return (
    <div>
      <h1>AI Tutor</h1>
      <p className="muted" style={{ maxWidth: 640, marginBottom: '1.2em' }}>
        Answers are grounded in this Project's uploaded materials. If there isn't enough evidence to answer
        confidently, the Tutor will tell you instead of guessing.
      </p>

      <div className="card">
        <div className="chat-window" ref={scrollRef}>
          {messages.length === 0 && <p className="muted">Ask a question about your materials to get started.</p>}
          {messages.map((m) => (
            <div key={m.id} className={`msg ${m.role === 'USER' ? 'msg-user' : 'msg-assistant'} ${m.insufficientEvidence ? 'msg-insufficient' : ''}`}>
              <div>{m.content}</div>
              {m.citations && m.citations.length > 0 && (
                <div className="citation">
                  {m.citations.map((c, i) => (
                    <div key={i}><strong>Source:</strong> {c.materialName}{c.page ? ` — Page ${c.page}` : ''}</div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
        <form onSubmit={send} className="chat-input-row">
          <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="Ask about your materials…" disabled={sending} />
          <button className="btn btn-primary" disabled={sending}>{sending ? 'Thinking…' : 'Send'}</button>
        </form>
        {error && <p className="error-text" style={{ marginTop: '0.6em' }}>{error}</p>}
      </div>
    </div>
  );
}
