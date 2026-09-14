const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

function getToken() {
  return localStorage.getItem('asc_token');
}

async function request(path, { method = 'GET', body, isForm = false } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (!isForm && body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: isForm ? body : body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 204) return null;

  let data = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }

  if (!res.ok) {
    const message = (data && data.message) || `Request failed (${res.status})`;
    const err = new Error(message);
    err.status = res.status;
    throw err;
  }
  return data;
}

export const api = {
  // auth
  register: (payload) => request('/api/auth/register', { method: 'POST', body: payload }),
  login: (payload) => request('/api/auth/login', { method: 'POST', body: payload }),

  // spaces
  listSpaces: () => request('/api/spaces'),
  createSpace: (payload) => request('/api/spaces', { method: 'POST', body: payload }),

  // projects
  listProjects: (spaceId) => request(`/api/spaces/${spaceId}/projects`),
  createProject: (spaceId, payload) => request(`/api/spaces/${spaceId}/projects`, { method: 'POST', body: payload }),
  listAllProjects: () => request('/api/projects'),
  projectDashboard: (projectId) => request(`/api/projects/${projectId}/dashboard`),

  // materials
  listMaterials: (projectId) => request(`/api/projects/${projectId}/materials`),
  uploadMaterial: (projectId, file) => {
    const form = new FormData();
    form.append('file', file);
    return request(`/api/projects/${projectId}/materials`, { method: 'POST', body: form, isForm: true });
  },

  // tutor
  tutorHistory: (projectId) => request(`/api/projects/${projectId}/tutor/history`),
  tutorAsk: (projectId, message) => request(`/api/projects/${projectId}/tutor/ask`, { method: 'POST', body: { message } }),

  // quiz
  startQuiz: (projectId, questionCount) => request(`/api/projects/${projectId}/quiz/start`, { method: 'POST', body: { questionCount } }),
  submitAnswer: (projectId, attemptId, questionId, answer) =>
    request(`/api/projects/${projectId}/quiz/${attemptId}/questions/${questionId}/answer`, { method: 'POST', body: { answer } }),

  // mastery / growth / recommendations
  mastery: (projectId) => request(`/api/projects/${projectId}/mastery`),
  growth: (projectId) => request(`/api/projects/${projectId}/growth`),
  recommendations: (projectId) => request(`/api/projects/${projectId}/recommendations`),
  dismissRecommendation: (projectId, id) => request(`/api/projects/${projectId}/recommendations/${id}/dismiss`, { method: 'POST' }),

  // analytics
  projectAnalytics: (projectId) => request(`/api/projects/${projectId}/analytics`),

  // admin
  adminUsers: () => request('/api/admin/users'),
  adminUserDetail: (userId) => request(`/api/admin/users/${userId}`),
  adminAnalytics: () => request('/api/admin/analytics'),
  adminActivity: () => request('/api/admin/activity'),
  adminHealth: () => request('/api/admin/health'),
};

export function setToken(token) {
  if (token) localStorage.setItem('asc_token', token);
  else localStorage.removeItem('asc_token');
}

export function getStoredToken() {
  return getToken();
}
