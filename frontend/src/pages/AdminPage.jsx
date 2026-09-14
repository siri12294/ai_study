import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext.jsx';

export default function AdminPage() {
  const { logout } = useAuth();
  const [users, setUsers] = useState([]);
  const [analytics, setAnalytics] = useState(null);
  const [activity, setActivity] = useState([]);
  const [health, setHealth] = useState(null);
  const [selectedUser, setSelectedUser] = useState(null);

  useEffect(() => {
    api.adminUsers().then(setUsers);
    api.adminAnalytics().then(setAnalytics);
    api.adminActivity().then(setActivity);
    api.adminHealth().then(setHealth);
  }, []);

  async function openUser(userId) {
    setSelectedUser(await api.adminUserDetail(userId));
  }

  return (
    <div className="content" style={{ maxWidth: 1100, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '1.4em' }}>
        <div>
          <h1>Admin Dashboard</h1>
          <p className="muted">Platform-wide visibility into users, activity, and AI usage.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.6em' }}>
          <Link to="/spaces" className="btn btn-ghost">Back to app</Link>
          <button className="btn btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </div>

      {health && (
        <div className="grid grid-3" style={{ marginBottom: '1.4em' }}>
          <StatCard label="Database" value={health.databaseUp ? 'Healthy' : 'Down'} />
          <StatCard label="Background jobs queued" value={health.backgroundJobsQueued} />
          <StatCard label="AI mode" value={health.aiMockMode ? `Mock (${health.aiModel})` : `Live (${health.aiModel})`} />
        </div>
      )}

      {analytics && (
        <div className="grid grid-3" style={{ marginBottom: '1.4em' }}>
          <StatCard label="Users" value={analytics.totalUsers} />
          <StatCard label="Spaces / Projects" value={`${analytics.totalSpaces} / ${analytics.totalProjects}`} />
          <StatCard label="Quizzes completed" value={analytics.totalQuizzesCompleted} />
        </div>
      )}

      {analytics && analytics.aiUsageByFeature.length > 0 && (
        <div className="card" style={{ marginBottom: '1.4em' }}>
          <h3>AI usage by feature</h3>
          <table className="data-table">
            <thead><tr><th>Feature</th><th>Calls</th><th>Avg latency</th><th>Est. cost</th><th>Success rate</th></tr></thead>
            <tbody>
              {analytics.aiUsageByFeature.map((u) => (
                <tr key={u.feature}>
                  <td>{u.feature}</td>
                  <td>{u.callCount}</td>
                  <td>{Math.round(u.avgLatencyMs)} ms</td>
                  <td>${u.totalEstimatedCostUsd.toFixed(4)}</td>
                  <td>{Math.round(u.successRate * 100)}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="grid grid-2">
        <div className="card">
          <h3>Users</h3>
          <table className="data-table">
            <thead><tr><th>Email</th><th>Spaces</th><th>Projects</th><th></th></tr></thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.email}</td>
                  <td>{u.spaceCount}</td>
                  <td>{u.projectCount}</td>
                  <td><button className="btn btn-ghost" style={{ padding: '0.2em 0.6em', fontSize: '0.78rem' }} onClick={() => openUser(u.id)}>View</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="card">
          <h3>Recent platform activity</h3>
          <ul style={{ margin: 0, paddingLeft: '1.1em', fontSize: '0.85rem', maxHeight: 320, overflowY: 'auto' }}>
            {activity.map((a) => (
              <li key={a.id} style={{ marginBottom: '0.4em' }}>
                <span className="muted">{new Date(a.createdAt).toLocaleString()}</span> — {a.userEmail || 'system'} — {a.eventType.replaceAll('_', ' ').toLowerCase()}
                {a.projectName ? ` (${a.projectName})` : ''}
              </li>
            ))}
          </ul>
        </div>
      </div>

      {selectedUser && (
        <div className="card" style={{ marginTop: '1.4em' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <h3>{selectedUser.user.email}</h3>
            <button className="btn btn-ghost" onClick={() => setSelectedUser(null)}>Close</button>
          </div>
          <p className="muted">{selectedUser.projects.length} project(s)</p>
          <ul style={{ fontSize: '0.9rem' }}>
            {selectedUser.projects.map((p) => <li key={p.id}>{p.name}</li>)}
          </ul>
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value }) {
  return (
    <div className="card">
      <div className="muted" style={{ fontSize: '0.82rem', marginBottom: '0.4em' }}>{label}</div>
      <div style={{ fontFamily: 'var(--font-serif)', fontSize: '1.6rem' }}>{value}</div>
    </div>
  );
}
