import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';

function pct(n) { return Math.round((n || 0) * 100); }

function Sparkline({ points }) {
  if (!points || points.length < 2) return <span className="muted" style={{ fontSize: '0.78rem' }}>Not enough history yet</span>;
  const w = 160, h = 34;
  const values = points.map((p) => p.score);
  const min = Math.min(...values), max = Math.max(...values);
  const range = max - min || 1;
  const step = w / (points.length - 1);
  const path = values.map((v, i) => `${i === 0 ? 'M' : 'L'} ${i * step} ${h - ((v - min) / range) * h}`).join(' ');
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <path d={path} fill="none" stroke="var(--accent)" strokeWidth="2" />
    </svg>
  );
}

export default function GrowthPage() {
  const { projectId } = useParams();
  const [mastery, setMastery] = useState([]);
  const [growth, setGrowth] = useState([]);
  const [recommendations, setRecommendations] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([api.mastery(projectId), api.growth(projectId), api.recommendations(projectId)])
      .then(([m, g, r]) => { setMastery(m); setGrowth(g); setRecommendations(r); })
      .finally(() => setLoading(false));
  }, [projectId]);

  async function dismiss(id) {
    await api.dismissRecommendation(projectId, id);
    setRecommendations((prev) => prev.filter((r) => r.id !== id));
  }

  if (loading) return <p className="muted">Loading…</p>;

  return (
    <div>
      <h1>Growth &amp; Mastery</h1>

      <div className="card" style={{ marginBottom: '1.4em' }}>
        <h3>Recommendations</h3>
        {recommendations.length === 0 ? (
          <p className="muted" style={{ fontSize: '0.9rem' }}>No active recommendations. Take a quiz to generate one.</p>
        ) : (
          recommendations.map((r) => (
            <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', gap: '1em', padding: '0.6em 0', borderBottom: '1px solid var(--hairline)' }}>
              <span style={{ fontSize: '0.92rem' }}>{r.text}</span>
              <button className="btn btn-ghost" style={{ padding: '0.3em 0.7em', fontSize: '0.78rem' }} onClick={() => dismiss(r.id)}>Dismiss</button>
            </div>
          ))
        )}
      </div>

      <div className="card">
        <h3>Concept mastery &amp; trend</h3>
        {mastery.length === 0 ? (
          <p className="muted" style={{ fontSize: '0.9rem' }}>No concepts yet. Upload material and take a quiz.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr><th>Concept</th><th>Mastery</th><th>Trend</th><th>History</th><th>Evidence</th></tr>
            </thead>
            <tbody>
              {mastery.map((m) => {
                const g = growth.find((x) => x.conceptId === m.conceptId);
                return (
                  <tr key={m.conceptId}>
                    <td>{m.conceptName}</td>
                    <td style={{ width: 160 }}>
                      <div className="progress-track"><div className="progress-fill" style={{ width: `${pct(m.score)}%` }} /></div>
                      <span className="muted" style={{ fontSize: '0.78rem' }}>{pct(m.score)}%</span>
                    </td>
                    <td className={`trend-${m.trend.toLowerCase()}`} style={{ fontWeight: 600, fontSize: '0.85rem' }}>
                      {m.trend.replace('_', ' ').toLowerCase()}
                    </td>
                    <td><Sparkline points={g?.history} /></td>
                    <td className="muted">{m.evidenceCount}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
