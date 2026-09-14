import React, { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';

const badgeClass = { QUEUED: 'badge-processing', PROCESSING: 'badge-processing', READY: 'badge-ready', FAILED: 'badge-failed' };

export default function MaterialsPage() {
  const { projectId } = useParams();
  const [materials, setMaterials] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);
  const fileRef = useRef(null);
  const pollRef = useRef(null);

  async function load() {
    const list = await api.listMaterials(projectId);
    setMaterials(list);
    return list;
  }

  useEffect(() => {
    load();
    return () => clearInterval(pollRef.current);
  }, [projectId]);

  useEffect(() => {
    const hasInFlight = materials.some((m) => m.status === 'QUEUED' || m.status === 'PROCESSING');
    if (hasInFlight && !pollRef.current) {
      pollRef.current = setInterval(load, 3000);
    } else if (!hasInFlight && pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, [materials]);

  async function onUpload(e) {
    e.preventDefault();
    const file = fileRef.current.files[0];
    if (!file) return;
    setError(null);
    setUploading(true);
    try {
      await api.uploadMaterial(projectId, file);
      fileRef.current.value = '';
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
    }
  }

  return (
    <div>
      <h1>Materials</h1>
      <p className="muted" style={{ maxWidth: 640 }}>
        Upload PDF study material. Processing (text extraction, chunking, and concept detection) happens
        in the background — you can navigate away and come back.
      </p>

      <form onSubmit={onUpload} className="card" style={{ maxWidth: 520, margin: '1.4em 0', display: 'flex', gap: '0.8em', alignItems: 'center' }}>
        <input type="file" accept="application/pdf" ref={fileRef} />
        <button className="btn btn-primary" disabled={uploading}>{uploading ? 'Uploading…' : 'Upload'}</button>
      </form>
      {error && <p className="error-text">{error}</p>}

      {materials.length === 0 ? (
        <div className="empty-state card">No materials uploaded yet.</div>
      ) : (
        <table className="data-table">
          <thead>
            <tr><th>File</th><th>Status</th><th>Pages</th><th>Uploaded</th></tr>
          </thead>
          <tbody>
            {materials.map((m) => (
              <tr key={m.id}>
                <td>{m.fileName}</td>
                <td>
                  <span className={`badge ${badgeClass[m.status]}`}>{m.status}</span>
                  {m.status === 'FAILED' && m.failureReason && (
                    <div className="muted" style={{ fontSize: '0.78rem', marginTop: '0.3em', maxWidth: 320 }}>{m.failureReason}</div>
                  )}
                </td>
                <td>{m.pageCount ?? '—'}</td>
                <td className="muted">{new Date(m.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
