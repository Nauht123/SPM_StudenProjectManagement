import { useEffect, useState } from 'react';
import api, { errMsg } from '../api.js';
import { Alert, Badge } from '../ui.jsx';

const EMPTY = { title: '', description: '', maxGroups: 1, semester: '', status: 'OPEN' };

export default function MyTopics() {
  const [topics, setTopics] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState('');

  const load = () => api.get('/topics/mine').then((r) => setTopics(r.data)).catch((e) => setError(errMsg(e)));
  useEffect(() => { load(); }, []);

  const set = (k) => (e) => setForm({ ...form, [k]: k === 'maxGroups' ? Number(e.target.value) : e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (editing) await api.put(`/topics/${editing}`, form);
      else await api.post('/topics', form);
      setForm(EMPTY); setEditing(null); load();
    } catch (err) {
      setError(errMsg(err));
    }
  };

  const edit = (t) => {
    setEditing(t.id);
    setForm({ title: t.title, description: t.description || '', maxGroups: t.maxGroups, semester: t.semester || '', status: t.status });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const remove = async (t) => {
    if (!window.confirm(`Xóa đề tài "${t.title}"?`)) return;
    setError('');
    try { await api.delete(`/topics/${t.id}`); load(); } catch (err) { setError(errMsg(err)); }
  };

  return (
    <div>
      <h2>Đề tài của tôi</h2>
      <Alert error={error} />
      <form className="card form" onSubmit={submit}>
        <h3>{editing ? 'Sửa đề tài' : 'Thêm đề tài mới'}</h3>
        <label>Tên đề tài<input value={form.title} onChange={set('title')} required /></label>
        <label>Mô tả<textarea rows={3} value={form.description} onChange={set('description')} /></label>
        <div className="row">
          <label>Số nhóm tối đa<input type="number" min={1} value={form.maxGroups} onChange={set('maxGroups')} required /></label>
          <label>Học kỳ<input value={form.semester} onChange={set('semester')} placeholder="HK1 2026-2027" /></label>
          <label>Trạng thái
            <select value={form.status} onChange={set('status')}>
              <option value="OPEN">Đang mở</option>
              <option value="CLOSED">Đã đóng</option>
            </select>
          </label>
        </div>
        <div className="row">
          <button className="primary" type="submit">{editing ? 'Lưu thay đổi' : 'Thêm đề tài'}</button>
          {editing && <button type="button" onClick={() => { setEditing(null); setForm(EMPTY); }}>Hủy</button>}
        </div>
      </form>

      <table className="table">
        <thead><tr><th>Đề tài</th><th>Học kỳ</th><th>Nhóm</th><th>Trạng thái</th><th /></tr></thead>
        <tbody>
          {topics.map((t) => (
            <tr key={t.id}>
              <td>{t.title}</td>
              <td>{t.semester}</td>
              <td>{t.approvedGroups}/{t.maxGroups}</td>
              <td><Badge kind={t.status === 'OPEN' ? 'good' : 'bad'}>{t.status === 'OPEN' ? 'Đang mở' : 'Đã đóng'}</Badge></td>
              <td className="actions">
                <button onClick={() => edit(t)}>Sửa</button>
                <button className="danger" onClick={() => remove(t)}>Xóa</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
