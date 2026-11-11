import { useEffect, useState } from 'react';
import { Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import api from './api.js';
import { ROLE_LABEL, useAuth } from './auth.jsx';
import { Icon } from './ui.jsx';
import Login from './pages/Login.jsx';
import Topics from './pages/Topics.jsx';
import MyTopics from './pages/MyTopics.jsx';
import Registrations from './pages/Registrations.jsx';
import TeamPage from './pages/TeamPage.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Contributions from './pages/Contributions.jsx';
import TeamContributions from './pages/TeamContributions.jsx';
import TeamEvaluation from './pages/TeamEvaluation.jsx';
import MemberContribution from './pages/MemberContribution.jsx';
import TeamDetail from './pages/TeamDetail.jsx';
import MilestoneDetail from './pages/MilestoneDetail.jsx';
import Notifications from './pages/Notifications.jsx';
import AdminUsers from './pages/AdminUsers.jsx';

const PAGE_INFO = [
  [(p) => /^\/teams\/\d+\/evaluation/.test(p), 'Điểm tổng hợp & đánh giá chéo', 'Điểm tự động, điều chỉnh của giảng viên và đánh giá chéo ẩn danh'],
  [(p) => /^\/teams\/\d+\/members\//.test(p), 'Đóng góp cá nhân', 'Điểm, deadline, sửa lại và lịch sử nộp của thành viên'],
  [(p) => /^\/teams\/\d+\/contributions/.test(p), 'Đóng góp cá nhân', 'So sánh đóng góp các thành viên trong nhóm'],
  ['/contributions', 'Đóng góp cá nhân', 'Đóng góp, đúng hạn/trễ hạn và sửa lại theo từng nhóm'],
  ['/teams/', 'Không gian làm việc nhóm', 'Milestone, tiến độ và bài nộp'],
  ['/team', 'Nhóm của tôi', 'Quản lý nhóm, thành viên và đề tài đăng ký'],
  ['/topics', 'Danh sách đề tài', 'Tìm và đăng ký đề tài đồ án'],
  ['/my-topics', 'Đề tài của tôi', 'Tạo và quản lý đề tài hướng dẫn'],
  ['/registrations', 'Đăng ký đề tài', 'Duyệt hoặc từ chối đăng ký của các nhóm'],
  ['/dashboard', 'Bảng điều khiển', 'Theo dõi tiến độ các nhóm đồ án'],
  ['/milestones/', 'Milestone', 'Công việc, bài nộp và phản hồi'],
  ['/notifications', 'Thông báo', 'Cập nhật mới nhất liên quan đến bạn'],
  ['/admin/users', 'Quản lý người dùng', 'Tạo tài khoản và xem danh sách người dùng'],
];

function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    const load = () =>
      api.get('/notifications/unread-count').then((r) => setUnread(r.data.count)).catch(() => {});
    load();
    const t = setInterval(load, 30000);
    return () => clearInterval(t);
  }, [pathname]);

  const menu = [];
  if (user.role === 'STUDENT') {
    menu.push(['/team', 'Nhóm của tôi', 'users'], ['/topics', 'Đề tài', 'book']);
  } else if (user.role === 'LECTURER') {
    menu.push(['/dashboard', 'Tiến độ các nhóm', 'grid'], ['/contributions', 'Đóng góp cá nhân', 'trend'], ['/my-topics', 'Đề tài của tôi', 'book'], ['/registrations', 'Đăng ký đề tài', 'check']);
  } else {
    menu.push(['/dashboard', 'Tổng quan', 'grid'], ['/contributions', 'Đóng góp cá nhân', 'trend'], ['/admin/users', 'Người dùng', 'user'], ['/topics', 'Đề tài', 'book']);
  }
  menu.push(['/notifications', 'Thông báo', 'bell', unread]);

  const info = PAGE_INFO.find(([m]) => (typeof m === 'function' ? m(pathname) : pathname.startsWith(m))) || PAGE_INFO[0];
  const initials = user.fullName.split(' ').slice(-2).map((w) => w[0]).join('').toUpperCase();

  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand">Đồ án<span>SV</span></Link>
        <div className="spacer" />
        <Link to="/notifications" className="bell" title="Thông báo">
          <Icon name="bell" />
          {unread > 0 && <span className="count">{unread}</span>}
        </Link>
        <div className="avatar">{initials}</div>
        <div className="who">
          <b>{user.fullName}</b>
          <span>{ROLE_LABEL[user.role]}</span>
        </div>
        <button
          className="logout"
          onClick={() => {
            logout();
            navigate('/login');
          }}
        >
          Đăng xuất
        </button>
      </header>
      <div className="body">
        <aside className="sidebar">
          <div className="side-title">Menu</div>
          {menu.map(([to, label, icon, badge]) => (
            <NavLink key={to} to={to} className={({ isActive }) => (isActive ? 'side-link active' : 'side-link')}>
              <Icon name={icon} />
              <span>{label}</span>
              {badge > 0 && <em>{badge}</em>}
            </NavLink>
          ))}
        </aside>
        <div className="main">
          <div className="page-header">
            <div className="ph-icon"><Icon name="grid" size={22} /></div>
            <div>
              <div className="ph-title">{info[1]}</div>
              <div className="ph-sub">{info[2]}</div>
            </div>
          </div>
          <main className="content">{children}</main>
        </div>
      </div>
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
      <Route path="/contributions" element={<Protected roles={['LECTURER', 'ADMIN']}><Contributions /></Protected>} />
      <Route path="/teams/:teamId/evaluation" element={<Protected><TeamEvaluation /></Protected>} />
      <Route path="/teams/:teamId/contributions" element={<Protected><TeamContributions /></Protected>} />
      <Route path="/teams/:teamId/members/:userId" element={<Protected><MemberContribution /></Protected>} />
      <Route path="/teams/:teamId" element={<Protected><TeamDetail /></Protected>} />
      <Route path="/milestones/:id" element={<Protected><MilestoneDetail /></Protected>} />
      <Route path="/notifications" element={<Protected><Notifications /></Protected>} />
      <Route path="/admin/users" element={<Protected roles={['ADMIN']}><AdminUsers /></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
