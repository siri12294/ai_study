import React, { useEffect, useState } from 'react';
import { NavLink, Outlet, useParams, Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext.jsx';

export default function ProjectLayout() {
  const { projectId } = useParams();
  const { user, logout } = useAuth();
  const [projectName, setProjectName] = useState('');

  useEffect(() => {
    let active = true;
    api.projectDashboard(projectId).then((d) => { if (active) setProjectName(d.project.name); }).catch(() => {});
    return () => { active = false; };
  }, [projectId]);

  const navItem = (to, label, end = false) => (
    <NavLink to={to} end={end} className={({ isActive }) => (isActive ? 'active' : '')}>{label}</NavLink>
  );

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">AI Study Companion</div>
        <nav className="sidebar-nav">
          {navItem(`/projects/${projectId}`, 'Dashboard', true)}
          {navItem(`/projects/${projectId}/materials`, 'Materials')}
          {navItem(`/projects/${projectId}/tutor`, 'Tutor')}
          {navItem(`/projects/${projectId}/quiz`, 'Quiz')}
          {navItem(`/projects/${projectId}/growth`, 'Growth & Mastery')}
          {navItem(`/projects/${projectId}/analytics`, 'Analytics')}
        </nav>
        <div className="sidebar-footer">
          <Link to="/spaces" style={{ color: '#D8E6DC' }}>&larr; All Spaces</Link>
          {user?.role === 'ADMIN' && <div style={{ marginTop: '0.6em' }}><Link to="/admin" style={{ color: '#D8E6DC' }}>Admin dashboard</Link></div>}
          <div style={{ marginTop: '0.6em' }}><button onClick={logout} style={{ color: '#D8E6DC', padding: 0 }}>Sign out</button></div>
        </div>
      </aside>
      <div className="main-area">
        <div className="topbar">
          <strong>{projectName || 'Loading…'}</strong>
          <span className="muted" style={{ fontSize: '0.85rem' }}>{user?.displayName}</span>
        </div>
        <div className="content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
