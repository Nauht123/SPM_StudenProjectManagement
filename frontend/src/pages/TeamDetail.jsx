import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, Progress, fmtDate } from '../ui.jsx';

export default function TeamDetail() {
  const { teamId } = useParams();
  const { user } = useAuth();
  const [team, setTeam] = useState(null);
  const [progress, setProgress] = useState(null);
  const [milestones, setMilestones] = useState([]);
  const [form, setForm] = useState({ title: '', description: '', dueDate: '' });
  const [error, setError] = useState('');

  const load = async () => {
    try {
      const [t, p, m] = await Promise.all([
        api.get(`/teams/${teamId}`),
        api.get(`/teams/${teamId}/progress`),
        api.get(`/teams/${teamId}/milestones`),
      ]);
      setTeam(t.data); setProgress(p.data); setMilestones(m.data);
    } catch (e) {
      setError(errMsg(e));
    }
  };
  useEffect(() => { load(); }, [teamId]);

  const create = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await api.post(`/teams/${teamId}/milestones`, form);
      setForm({ title: '', description: '', dueDate: '' });
      load();
    } catch (err) {
      setError(errMsg(err));
    }
  };

  const remove = async (m) => {
    if (!window.confirm(`Xóa milestone "${m.title}"?`)) return;
    setError('');
    try { await api.delete(`/milestones/${m.id}`); load(); } catch (err) { setError(errMsg(err)); }
  };

  if (!team) return <Alert error={error} />;

  const canManage = user.role === 'LECTURER' || (user.role === 'STUDENT' && team.leader.id === user.id);

  return (
    <div>
      <h2>Nhóm {team.name}</h2>
      <Alert error={error} />
      <div className="card">
        <p><b>Đề tài:</b> {team.registration?.topic.title} · <b>GVHD:</b> {team.registration?.topic.lecturer?.fullName}</p>
        <p><b>Thành viên:</b> {team.members.map((m) => m.fullName + (m.id === team.leader.id ? ' (nhóm trưởng)' : '')).join(', ')}</p>
        {progress && (
          <>
            <Progress percent={progress.percent} />
            <p className="muted small">{progress.doneTasks}/{progress.totalTasks} task hoàn thành · {progress.overdueMilestones} milestone quá hạn chưa nộp · {progress.lateSubmissions} bài nộp trễ</p>
          </>
        )}
      </div>

      <h3>Milestone</h3>
      <table className="table">
        <thead><tr><th>Milestone</th><th>Hạn nộp</th><th>Task</th><th>Bài nộp</th><th>Trạng thái</th><th /></tr></thead>
        <tbody>
          {milestones.map((m) => (
            <tr key={m.id}>
              <td><Link to={`/milestones/${m.id}`}>{m.title}</Link></td>
              <td>{fmtDate(m.dueDate)}</td>
              <td>{m.doneTasks}/{m.totalTasks}</td>
              <td>{m.submissions}</td>
              <td>{m.overdue ? <Badge kind="bad">Quá hạn chưa nộp</Badge> : m.submissions > 0 ? <Badge kind="good">Đã nộp</Badge> : <Badge>Đang thực hiện</Badge>}</td>
              <td className="actions">{canManage && <button className="danger" onClick={() => remove(m)}>Xóa</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {milestones.length === 0 && <p className="muted">Chưa có milestone nào.</p>}

      {canManage && (
        <form className="card form" onSubmit={create}>
          <h3>Thêm milestone</h3>
          <div className="row">
            <label>Tên milestone<input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required /></label>
            <label>Hạn nộp<input type="date" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} required /></label>
          </div>
          <label>Mô tả / yêu cầu<textarea rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
          <button className="primary" type="submit">Thêm milestone</button>
        </form>
      )}
    </div>
  );
}
