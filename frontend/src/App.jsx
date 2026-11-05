import { useEffect, useState } from 'react';
import { Link, Navigate, NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import api from './api.js';
import { ROLE_LABEL, useAuth } from './auth.jsx';
import Login from './pages/Login.jsx';
import Topics from './pages/Topics.jsx';
import MyTopics from './pages/MyTopics.jsx';
import Registrations from './pages/Registrations.jsx';
import TeamPage from './pages/TeamPage.jsx';
import Dashboard from './pages/Dashboard.jsx';
import TeamDetail from './pages/TeamDetail.jsx';
import MilestoneDetail from './pages/MilestoneDetail.jsx';
import Notifications from './pages/Notifications.jsx';
import AdminUsers from './pages/AdminUsers.jsx';

function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    const load = () =>
      api.get('/notifications/unread-count').then((r) => setUnread(r.data.count)).catch(() => {});
    load();
    const t = setInterval(load, 30000);
    return () => clearInterval(t);
  }, []);

  const links = [];
  if (user.role === 'STUDENT') {
    links.push(['/team', 'Nhóm của tôi'], ['/topics', 'Đề tài']);
  } else if (user.role === 'LECTURER') {
    links.push(['/dashboard', 'Tiến độ các nhóm'], ['/my-topics', 'Đề tài của tôi'], ['/registrations', 'Đăng ký đề tài']);
  } else {
    links.push(['/dashboard', 'Tổng quan'], ['/admin/users', 'Người dùng'], ['/topics', 'Đề tài']);
  }

  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand">Quản lý đồ án</Link>
        <nav>
          {links.map(([to, label]) => (
            <NavLink key={to} to={to} className={({ isActive }) => (isActive ? 'active' : '')}>
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="spacer" />
        <Link to="/notifications" className="bell">
          Thông báo {unread > 0 && <span className="count">{unread}</span>}
        </Link>
        <span className="who">
          {user.fullName} · {ROLE_LABEL[user.role]}
        </span>
        <button
          className="link"
          onClick={() => {
            logout();
            navigate('/login');
          }}
        >
          Đăng xuất
        </button>
      </header>
      <main className="content">{children}</main>
    </div>
  );
}

function Protected({ roles, children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to="/" replace />;
  return <Layout>{children}</Layout>;
}

function Home() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <Navigate to={user.role === 'STUDENT' ? '/team' : '/dashboard'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Home />} />
      <Route path="/team" element={<Protected roles={['STUDENT']}><TeamPage /></Protected>} />
      <Route path="/topics" element={<Protected><Topics /></Protected>} />
      <Route path="/my-topics" element={<Protected roles={['LECTURER']}><MyTopics /></Protected>} />
      <Route path="/registrations" element={<Protected roles={['LECTURER']}><Registrations /></Protected>} />
      <Route path="/dashboard" element={<Protected roles={['LECTURER', 'ADMIN']}><Dashboard /></Protected>} />
      <Route path="/teams/:teamId" element={<Protected><TeamDetail /></Protected>} />
      <Route path="/milestones/:id" element={<Protected><MilestoneDetail /></Protected>} />
      <Route path="/notifications" element={<Protected><Notifications /></Protected>} />
      <Route path="/admin/users" element={<Protected roles={['ADMIN']}><AdminUsers /></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
