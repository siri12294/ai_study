import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';

export default function AnalyticsPage() {
  const { projectId } = useParams();
  const [data, setData] = useState(null);

  useEffect(() => { api.projectAnalytics(projectId).then(setData); }, [projectId]);

  if (!data) return <p className="muted">Loading…</p>;

  return (
    <div>
      <h1>Project Analytics</h1>
      <div className="grid grid-3" style={{ margin: '1.4em 0' }}>
        <Stat label="Tutor messages" value={data.tutorMessages} />
        <Stat label="Quizzes completed" value={data.quizzesCompleted} />
        <Stat label="Avg. quiz score" value={`${Math.round((data.averageQuizScore || 0) * 100)}%`} />
      </div>

      <div className="card">
        <h3>Activity by type</h3>
        {Object.keys(data.activityByType).length === 0 ? (
          <p className="muted" style={{ fontSize: '0.9rem' }}>No activity recorded yet.</p>
        ) : (
          <table className="data-table">
            <thead><tr><th>Event</th><th>Count</th></tr></thead>
            <tbody>
              {Object.entries(data.activityByType).map(([k, v]) => (
                <tr key={k}><td>{k.replaceAll('_', ' ').toLowerCase()}</td><td>{v}</td></tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div className="card">
      <div className="muted" style={{ fontSize: '0.82rem', marginBottom: '0.4em' }}>{label}</div>
      <div style={{ fontFamily: 'var(--font-serif)', fontSize: '1.7rem' }}>{value}</div>
    </div>
  );
}
