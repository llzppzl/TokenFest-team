import { lazy, Suspense, useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from 'react-router'
import { setUnauthorizedHandler } from './api'
import AuthProvider from './AuthProvider'
import { useAuth } from './authContext'
import AdminLayout from './components/AdminLayout'
import KeysPage from './pages/KeysPage'
import LoginPage from './pages/LoginPage'
import ProvidersPage from './pages/ProvidersPage'
import RequestsPage from './pages/RequestsPage'
import SettingsPage from './pages/SettingsPage'

// Charts are large; load them only when needed
const OverviewPage = lazy(() => import('./pages/OverviewPage'))
const UserDashboard = lazy(() => import('./pages/UserDashboard'))

/** Sends the user to the login page when their token expires. */
function SessionWatcher() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  useEffect(() => {
    setUnauthorizedHandler(() => {
      logout()
      navigate('/login')
    })
  }, [logout, navigate])
  return null
}

function RequireRole({ role, children }: { role: 'ADMIN' | 'USER'; children: React.ReactNode }) {
  const { session } = useAuth()
  if (!session) return <Navigate to="/login" replace />
  if (session.role !== role) return <Navigate to={session.role === 'ADMIN' ? '/admin' : '/me'} replace />
  return children
}

function Home() {
  const { session } = useAuth()
  if (!session) return <Navigate to="/login" replace />
  return <Navigate to={session.role === 'ADMIN' ? '/admin' : '/me'} replace />
}

function AppRoutes() {
  return (
    <>
      <SessionWatcher />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/admin"
          element={
            <RequireRole role="ADMIN">
              <AdminLayout />
            </RequireRole>
          }
        >
          <Route index element={<Navigate to="overview" replace />} />
          <Route
            path="overview"
            element={
              <Suspense fallback={<p className="text-sm text-gray-500">Loading...</p>}>
                <OverviewPage />
              </Suspense>
            }
          />
          <Route path="requests" element={<RequestsPage />} />
          <Route path="providers" element={<ProvidersPage />} />
          <Route path="keys" element={<KeysPage />} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>
        <Route
          path="/me"
          element={
            <RequireRole role="USER">
              <Suspense fallback={<p className="p-8 text-sm text-gray-500">Loading...</p>}>
                <UserDashboard />
              </Suspense>
            </RequireRole>
          }
        />
        <Route path="*" element={<Home />} />
      </Routes>
    </>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}
