import React, { Suspense, lazy } from 'react';
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
} from 'react-router-dom';
import { Spin } from 'antd';
import { tokenStore } from './api/tokenStore';
import AdminLayout from './components/AdminLayout';

const Login = lazy(() => import('./pages/Login/Login'));
const EhrManagement = lazy(() => import('./pages/EhrManagement/EhrManagement'));
const RoutingRuleBuilder = lazy(() => import('./pages/RoutingRules/RoutingRuleBuilder'));
const TransformLogs = lazy(() => import('./pages/TransformLogs/TransformLogs'));

const PageFallback = <Spin size="large" style={{ display: 'block', margin: '80px auto' }} />;

const RequireAuth: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  if (!tokenStore.isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

const App: React.FC = () => (
  <BrowserRouter>
    <Suspense fallback={PageFallback}>
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route
          path="/"
          element={
            <RequireAuth>
              <AdminLayout />
            </RequireAuth>
          }
        >
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<EhrManagement />} />
          <Route path="ehrs" element={<EhrManagement />} />
          <Route path="routing-rules" element={<RoutingRuleBuilder />} />
          <Route path="transform-logs" element={<TransformLogs />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Route>
      </Routes>
    </Suspense>
  </BrowserRouter>
);

export default App;
