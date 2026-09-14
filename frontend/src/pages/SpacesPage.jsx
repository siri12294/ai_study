import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext.jsx';

export default function SpacesPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [spaces, setSpaces] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState(null);

  async function load() {
    setLoading(true);
    try {
      setSpaces(await api.listSpaces());
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function createSpace(e) {
    e.preventDefault();
    setError(null);
    try {
      await api.createSpace({ name, description });
      setName(''); setDescription(''); setShowForm(false);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="content" style={{ maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.6em' }}>
        <div>
          <h1>Your Spaces</h1>
          <p className="muted">{user?.displayName}, pick a broad learning area or start a new one.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.6em' }}>
          {user?.role === 'ADMIN' && <button className="btn btn-ghost" onClick={() => navigate('/admin')}>Admin dashboard</button>}
          <button className="btn btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </div>

      <button className="btn btn-primary" style={{ marginBottom: '1.4em' }} onClick={() => setShowForm((s) => !s)}>
        {showForm ? 'Cancel' : 'New Space'}
      </button>

      {showForm && (
        <form onSubmit={createSpace} className="card" style={{ marginBottom: '1.6em', maxWidth: 480 }}>
          <div className="field">
            <label>Name</label>
            <input required value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Machine Learning" />
          </div>
          <div className="field">
            <label>Description</label>
            <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What is this Space about?" />
          </div>
          {error && <p className="error-text">{error}</p>}
          <button className="btn btn-primary">Create Space</button>
        </form>
      )}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : spaces.length === 0 ? (
        <div className="empty-state card">No Spaces yet. Create one to start learning something new.</div>
      ) : (
        <div className="space-list">
          {spaces.map((s) => (
            <Link key={s.id} to={`/spaces/${s.id}`} className="tile card">
              <h3>{s.name}</h3>
              <p className="muted" style={{ fontSize: '0.88rem', minHeight: '2.2em' }}>{s.description || 'No description yet.'}</p>
              <span className="muted" style={{ fontSize: '0.8rem' }}>{s.projectCount} project{s.projectCount === 1 ? '' : 's'}</span>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
