import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';

function pct(n) { return Math.round((n || 0) * 100); }

export default function ProjectDashboardPage() {
  const { projectId } = useParams();
  const [dash, setDash] = useState(null);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    try { setDash(await api.projectDashboard(projectId)); } finally { setLoading(false); }
  }

  useEffect(() => { load(); }, [projectId]);

  if (loading) return <p className="muted">Loading dashboard…</p>;
  if (!dash) return null;

  const { project, overallProgress, topConcepts, recentActivity, recommendations, materialsReady, materialsTotal } = dash;

  return (
    <div>
      <h1>{project.name}</h1>
      {project.goal && <p className="muted" style={{ maxWidth: 640 }}>Goal: {project.goal}</p>}

      <div className="grid grid-3" style={{ margin: '1.6em 0' }}>
        <div className="card">
          <div className="muted" style={{ fontSize: '0.82rem', marginBottom: '0.4em' }}>Overall progress</div>
          <div style={{ fontFamily: 'var(--font-serif)', fontSize: '1.8rem' }}>{pct(overallProgress)}%</div>
          <div className="progress-track" style={{ marginTop: '0.6em' }}>
            <div className="progress-fill" style={{ width: `${pct(overallProgress)}%` }} />
          </div>
        </div>
        <div className="card">
          <div className="muted" style={{ fontSize: '0.82rem', marginBottom: '0.4em' }}>Materials ready</div>
          <div style={{ fontFamily: 'var(--font-serif)', fontSize: '1.8rem' }}>{materialsReady} / {materialsTotal}</div>
          <Link to={`/projects/${projectId}/materials`} className="muted" style={{ fontSize: '0.82rem' }}>Manage materials &rarr;</Link>
        </div>
        <div className="card">
          <div className="muted" style={{ fontSize: '0.82rem', marginBottom: '0.4em' }}>Next step</div>
          {recommendations.length > 0 ? (
            <p style={{ fontSize: '0.92rem', margin: 0 }}>{recommendations[0].text}</p>
          ) : (
            <p className="muted" style={{ fontSize: '0.9rem', margin: 0 }}>Upload material and take a quiz to get a recommendation.</p>
          )}
        </div>
      </div>

      <div className="grid grid-2">
        <div className="card">
          <h3>Concept mastery</h3>
          {topConcepts.length === 0 ? (
            <p className="muted" style={{ fontSize: '0.9rem' }}>No concepts detected yet — upload material to get started.</p>
          ) : (
            topConcepts.map((c) => (
              <div key={c.conceptId} className="mastery-row">
                <div className="mastery-name">{c.conceptName}</div>
                <div className="mastery-track progress-track"><div className="progress-fill" style={{ width: `${pct(c.score)}%` }} /></div>
                <div className="mastery-pct">{pct(c.score)}%</div>
              </div>
            ))
          )}
          <Link to={`/projects/${projectId}/growth`} className="muted" style={{ fontSize: '0.82rem' }}>View full growth &rarr;</Link>
        </div>

        <div className="card">
          <h3>Recent activity</h3>
          {recentActivity.length === 0 ? (
            <p className="muted" style={{ fontSize: '0.9rem' }}>Nothing yet — ask the Tutor a question or upload material.</p>
          ) : (
            <ul style={{ margin: 0, paddingLeft: '1.1em', fontSize: '0.88rem' }}>
              {recentActivity.slice(0, 8).map((a) => (
                <li key={a.id} style={{ marginBottom: '0.4em' }}>
                  <span className="muted">{new Date(a.createdAt).toLocaleString()}</span> — {formatEvent(a.eventType)}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div style={{ marginTop: '1.6em', display: 'flex', gap: '0.8em' }}>
        <Link to={`/projects/${projectId}/tutor`} className="btn btn-primary">Ask the Tutor</Link>
        <Link to={`/projects/${projectId}/quiz`} className="btn btn-ghost">Take a quiz</Link>
      </div>
    </div>
  );
}

function formatEvent(type) {
  return type.replaceAll('_', ' ').toLowerCase();
}
