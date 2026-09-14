import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext.jsx';

import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import SpacesPage from './pages/SpacesPage.jsx';
import SpaceDetailPage from './pages/SpaceDetailPage.jsx';
import ProjectLayout from './pages/ProjectLayout.jsx';
import ProjectDashboardPage from './pages/ProjectDashboardPage.jsx';
import MaterialsPage from './pages/MaterialsPage.jsx';
import TutorPage from './pages/TutorPage.jsx';
import QuizPage from './pages/QuizPage.jsx';
import GrowthPage from './pages/GrowthPage.jsx';
import AnalyticsPage from './pages/AnalyticsPage.jsx';
import AdminPage from './pages/AdminPage.jsx';

function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

function RequireAdmin({ children }) {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'ADMIN') return <Navigate to="/spaces" replace />;
  return children;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route path="/spaces" element={<RequireAuth><SpacesPage /></RequireAuth>} />
          <Route path="/spaces/:spaceId" element={<RequireAuth><SpaceDetailPage /></RequireAuth>} />

          <Route path="/projects/:projectId" element={<RequireAuth><ProjectLayout /></RequireAuth>}>
            <Route index element={<ProjectDashboardPage />} />
            <Route path="materials" element={<MaterialsPage />} />
            <Route path="tutor" element={<TutorPage />} />
            <Route path="quiz" element={<QuizPage />} />
            <Route path="growth" element={<GrowthPage />} />
            <Route path="analytics" element={<AnalyticsPage />} />
          </Route>

          <Route path="/admin" element={<RequireAdmin><AdminPage /></RequireAdmin>} />

          <Route path="/" element={<Navigate to="/spaces" replace />} />
          <Route path="*" element={<Navigate to="/spaces" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
