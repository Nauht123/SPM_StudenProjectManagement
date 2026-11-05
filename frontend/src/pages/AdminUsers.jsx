import { useEffect, useState } from 'react';
import api, { errMsg } from '../api.js';
import { ROLE_LABEL } from '../auth.jsx';
import { Alert } from '../ui.jsx';

const EMPTY = { username: '', password: '', fullName: '', studentCode: '', role: 'LECTURER' };

export default function AdminUsers() {
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');

  const load = () => api.get('/admin/users').then((r) => setUsers(r.data)).catch((e) => setError(errMsg(e)));
  useEffect(() => { load(); }, []);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await api.post('/admin/users', form);
      setForm(EMPTY);
      load();
    } catch (err) {
      setError(errMsg(err));
    }
  };

  return (
    <div>
      <h2>Quản lý người dùng</h2>
      <Alert error={error} />
      <form className="card form" onSubmit={submit}>
        <h3>Tạo tài khoản</h3>
        <div className="row">
          <label>Họ tên<input value={form.fullName} onChange={set('fullName')} required /></label>
          <label>Tên đăng nhập<input value={form.username} onChange={set('username')} required /></label>
          <label>Mật khẩu<input type="password" value={form.password} onChange={set('password')} required minLength={6} /></label>
        </div>
        <div className="row">
          <label>Vai trò
            <select value={form.role} onChange={set('role')}>
              <option value="LECTURER">Giảng viên</option>
              <option value="STUDENT">Sinh viên</option>
              <option value="ADMIN">Quản trị viên</option>
            </select>
          </label>
          {form.role === 'STUDENT' && <label>MSSV<input value={form.studentCode} onChange={set('studentCode')} required /></label>}
        </div>
        <button className="primary" type="submit">Tạo tài khoản</button>
      </form>
      <table className="table">
        <thead><tr><th>Họ tên</th><th>Tên đăng nhập</th><th>Vai trò</th><th>MSSV</th></tr></thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}><td>{u.fullName}</td><td>{u.username}</td><td>{ROLE_LABEL[u.role]}</td><td>{u.studentCode}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
