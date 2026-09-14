import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';

export default function SpaceDetailPage() {
  const { spaceId } = useParams();
  const navigate = useNavigate();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [goal, setGoal] = useState('');
  const [error, setError] = useState(null);

  async function load() {
    setLoading(true);
    try {
      setProjects(await api.listProjects(spaceId));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [spaceId]);

  async function createProject(e) {
    e.preventDefault();
    setError(null);
    try {
      const project = await api.createProject(spaceId, { name, description, goal });
      navigate(`/projects/${project.id}`);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="content" style={{ maxWidth: 1000, margin: '0 auto' }}>
      <Link to="/spaces" className="muted" style={{ fontSize: '0.85rem' }}>&larr; All Spaces</Link>
      <h1 style={{ marginTop: '0.5em' }}>Projects</h1>
      <p className="muted">A Project is a focused learning journey with its own materials, Tutor, and mastery.</p>

      <button className="btn btn-primary" style={{ margin: '1.2em 0' }} onClick={() => setShowForm((s) => !s)}>
        {showForm ? 'Cancel' : 'New Project'}
      </button>

      {showForm && (
        <form onSubmit={createProject} className="card" style={{ marginBottom: '1.6em', maxWidth: 520 }}>
          <div className="field">
            <label>Name</label>
            <input required value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Understand Transformers" />
          </div>
          <div className="field">
            <label>Description</label>
            <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
          <div className="field">
            <label>Learning goal</label>
            <textarea rows={2} value={goal} onChange={(e) => setGoal(e.target.value)} placeholder="What do you want to be able to do when you're done?" />
          </div>
          {error && <p className="error-text">{error}</p>}
          <button className="btn btn-primary">Create Project</button>
        </form>
      )}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : projects.length === 0 ? (
        <div className="empty-state card">No Projects in this Space yet.</div>
      ) : (
        <div className="project-list">
          {projects.map((p) => (
            <Link key={p.id} to={`/projects/${p.id}`} className="tile card">
              <h3>{p.name}</h3>
              <p className="muted" style={{ fontSize: '0.88rem' }}>{p.goal || p.description || 'No goal set yet.'}</p>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
