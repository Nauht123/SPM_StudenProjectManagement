import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert } from '../ui.jsx';

export default function Login() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ username: '', password: '', fullName: '', studentCode: '' });
  const [error, setError] = useState('');

  if (user) return <Navigate to="/" replace />;

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const url = mode === 'login' ? '/auth/login' : '/auth/register';
      const body =
        mode === 'login'
          ? { username: form.username, password: form.password }
          : form;
      const res = await api.post(url, body);
      login(res.data);
      navigate('/');
    } catch (err) {
      setError(errMsg(err));
    }
  };

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Quản lý đồ án sinh viên</h1>
        <p className="muted">{mode === 'login' ? 'Đăng nhập để tiếp tục' : 'Sinh viên đăng ký tài khoản'}</p>
        <Alert error={error} />
        {mode === 'register' && (
          <>
            <label>Họ và tên<input value={form.fullName} onChange={set('fullName')} required /></label>
            <label>Mã số sinh viên<input value={form.studentCode} onChange={set('studentCode')} required /></label>
          </>
        )}
        <label>Tên đăng nhập<input value={form.username} onChange={set('username')} required autoFocus /></label>
        <label>Mật khẩu<input type="password" value={form.password} onChange={set('password')} required minLength={mode === 'register' ? 6 : 1} /></label>
        <button type="submit" className="primary">{mode === 'login' ? 'Đăng nhập' : 'Đăng ký'}</button>
        <button type="button" className="link" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>
          {mode === 'login' ? 'Chưa có tài khoản? Đăng ký (sinh viên)' : 'Đã có tài khoản? Đăng nhập'}
        </button>
      </form>
    </div>
  );
}
