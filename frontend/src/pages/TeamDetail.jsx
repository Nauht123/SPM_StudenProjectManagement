import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, StatCard, fmtDate } from '../ui.jsx';

function MilestoneStatus({ m }) {
  if (m.overdue) return <Badge kind="bad">Quá hạn</Badge>;
  if (m.parentId === null || m.parentId === undefined) {
    return m.submissions > 0 ? <Badge kind="good">Đã nộp</Badge> : <Badge>Đang thực hiện</Badge>;
  }
  return m.totalTasks > 0 && m.doneTasks === m.totalTasks ? <Badge kind="good">Hoàn thành</Badge> : <Badge>Đang thực hiện</Badge>;
}

export default function TeamDetail() {
  const { teamId } = useParams();
  const { user } = useAuth();
  const [team, setTeam] = useState(null);
  const [progress, setProgress] = useState(null);
  const [milestones, setMilestones] = useState([]);
  const [mainForm, setMainForm] = useState({ title: '', description: '', dueDate: '' });
  const [subForms, setSubForms] = useState({});
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

  const run = async (fn) => {
    setError('');
    try { await fn(); await load(); } catch (e) { setError(errMsg(e)); }
  };

  const createMain = (e) => {
    e.preventDefault();
    run(async () => {
      await api.post(`/teams/${teamId}/milestones`, mainForm);
      setMainForm({ title: '', description: '', dueDate: '' });
    });
  };

  const createSub = (e, parentId) => {
    e.preventDefault();
    const f = subForms[parentId] || {};
    run(async () => {
      await api.post(`/teams/${teamId}/milestones`, { ...f, parentId });
      setSubForms({ ...subForms, [parentId]: { title: '', description: '', dueDate: '' } });
    });
  };

  const setSub = (parentId, k, v) => setSubForms({ ...subForms, [parentId]: { ...(subForms[parentId] || {}), [k]: v } });

  const remove = (m) => {
    if (!window.confirm(`Xóa milestone "${m.title}"?`)) return;
    run(() => api.delete(`/milestones/${m.id}`));
  };

  if (!team) return <Alert error={error} />;

  // Quyen: giang vien quan ly milestone chinh; nhom truong (hoac admin) quan ly milestone phu
  const canMain = user.role === 'LECTURER' || user.role === 'ADMIN';
  const canSub = user.role === 'ADMIN' || (user.role === 'STUDENT' && team.leader.id === user.id);
  const mains = milestones.filter((m) => !m.parentId);
  const subsOf = (id) => milestones.filter((m) => m.parentId === id);

  return (
    <div>
      <h2>{team.name}</h2>
      <Alert error={error} />
      {progress && (
        <div className="stat-grid four">
          <StatCard tone="blue" icon="target" value={`${progress.percent}%`} label="Tiến độ task" />
          <StatCard tone="green" icon="check" value={`${progress.doneTasks}/${progress.totalTasks}`} label="Task hoàn thành" />
          <StatCard tone="purple" icon="clock" value={progress.overdueMilestones} label="Milestone chính quá hạn chưa nộp" />
          <StatCard tone="red" icon="alert" value={progress.lateSubmissions} label="Bài nộp trễ hạn" />
        </div>
      )}
      <div className="card">
        <p><b>Đề tài:</b> {team.registration?.topic.title} · <b>GVHD:</b> {team.registration?.topic.lecturer?.fullName}</p>
        <p><b>Thành viên:</b> {team.members.map((m) => m.fullName + (m.id === team.leader.id ? ' (nhóm trưởng)' : '')).join(', ')}</p>
        <p className="muted small">Giảng viên quản lý milestone chính · Nhóm trưởng quản lý milestone phụ và giao task · Thành viên thực hiện task.</p>
      </div>

      <h3>Milestone chính</h3>
      {mains.map((m) => {
        const subs = subsOf(m.id);
        const f = subForms[m.id] || { title: '', description: '', dueDate: '' };
        return (
          <div className="card" key={m.id}>
            <div className="row between">
              <div>
                <Link to={`/milestones/${m.id}`}><b>{m.title}</b></Link>{' '}
                <MilestoneStatus m={m} />
                <div className="muted small">
                  Hạn nộp {fmtDate(m.dueDate)} · {m.subMilestones} milestone phụ · {m.doneTasks}/{m.totalTasks} task · {m.submissions} bài nộp
                  {m.pendingReview > 0 && <> · <b>{m.pendingReview} task chờ xác nhận</b></>}
                </div>
              </div>
              {canMain && <button className="danger" onClick={() => remove(m)}>Xóa</button>}
            </div>

            {subs.length > 0 && (
              <table className="table plain">
                <thead><tr><th>Milestone phụ</th><th>Hạn</th><th>Task</th><th>Trạng thái</th><th /></tr></thead>
                <tbody>
                  {subs.map((s) => (
                    <tr key={s.id}>
                      <td><Link to={`/milestones/${s.id}`}>{s.title}</Link></td>
                      <td>{fmtDate(s.dueDate)}</td>
                      <td>{s.doneTasks}/{s.totalTasks}{s.pendingReview > 0 && <> <Badge kind="warn">{s.pendingReview} chờ xác nhận</Badge></>}</td>
                      <td><MilestoneStatus m={s} /></td>
                      <td className="actions">{canSub && <button className="danger" onClick={() => remove(s)}>Xóa</button>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {subs.length === 0 && <p className="muted small">Chưa có milestone phụ.</p>}

            {canSub && (
              <details className="subform">
                <summary>+ Thêm milestone phụ</summary>
                <form className="form" onSubmit={(e) => createSub(e, m.id)}>
                  <div className="row">
                    <label>Tên milestone phụ<input value={f.title} onChange={(e) => setSub(m.id, 'title', e.target.value)} required /></label>
                    <label>Hạn (không sau {fmtDate(m.dueDate)})<input type="date" max={m.dueDate} value={f.dueDate} onChange={(e) => setSub(m.id, 'dueDate', e.target.value)} required /></label>
                  </div>
                  <label>Mô tả<input value={f.description} onChange={(e) => setSub(m.id, 'description', e.target.value)} /></label>
                  <button className="primary" type="submit">Thêm milestone phụ</button>
                </form>
              </details>
            )}
          </div>
        );
      })}
      {mains.length === 0 && <p className="muted">Giảng viên chưa tạo milestone chính nào.</p>}

      {canMain && (
        <form className="card form" onSubmit={createMain}>
          <h3>Thêm milestone chính</h3>
          <div className="row">
            <label>Tên milestone chính<input value={mainForm.title} onChange={(e) => setMainForm({ ...mainForm, title: e.target.value })} required /></label>
            <label>Hạn nộp<input type="date" value={mainForm.dueDate} onChange={(e) => setMainForm({ ...mainForm, dueDate: e.target.value })} required /></label>
          </div>
          <label>Mô tả / yêu cầu<textarea rows={2} value={mainForm.description} onChange={(e) => setMainForm({ ...mainForm, description: e.target.value })} /></label>
          <button className="primary" type="submit">Thêm milestone chính</button>
        </form>
      )}
    </div>
  );
}
