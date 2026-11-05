import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { downloadSubmission, errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, fmtDate, fmtTime } from '../ui.jsx';

const COLUMNS = [
  ['TODO', 'Cần làm'],
  ['DOING', 'Đang làm'],
  ['DONE', 'Hoàn thành'],
];

export default function MilestoneDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const [milestone, setMilestone] = useState(null);
  const [team, setTeam] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [subs, setSubs] = useState([]);
  const [taskForm, setTaskForm] = useState({ title: '', description: '', assigneeId: '', dueDate: '' });
  const [file, setFile] = useState(null);
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const load = async () => {
    try {
      const m = await api.get(`/milestones/${id}`);
      setMilestone(m.data);
      const [t, tk, sb] = await Promise.all([
        api.get(`/teams/${m.data.teamId}`),
        api.get(`/milestones/${id}/tasks`),
        api.get(`/milestones/${id}/submissions`),
      ]);
      setTeam(t.data); setTasks(tk.data); setSubs(sb.data);
    } catch (e) {
      setError(errMsg(e));
    }
  };
  useEffect(() => { load(); }, [id]);

  const run = async (fn, msg) => {
    setError(''); setOk('');
    try { await fn(); if (msg) setOk(msg); await load(); } catch (e) { setError(errMsg(e)); }
  };

  if (!milestone || !team) return <Alert error={error} />;

  const isMember = team.members.some((m) => m.id === user.id);
  const isLeader = team.leader.id === user.id;

  const addTask = (e) => {
    e.preventDefault();
    const body = { ...taskForm, assigneeId: taskForm.assigneeId ? Number(taskForm.assigneeId) : null, dueDate: taskForm.dueDate || null };
    run(async () => {
      await api.post(`/milestones/${id}/tasks`, body);
      setTaskForm({ title: '', description: '', assigneeId: '', dueDate: '' });
    });
  };

  const move = (t, status) => run(() => api.patch(`/tasks/${t.id}/status`, { status }));

  const upload = (e) => {
    e.preventDefault();
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    if (note) fd.append('note', note);
    run(async () => {
      await api.post(`/milestones/${id}/submissions`, fd);
      setFile(null); setNote('');
      e.target.reset();
    }, 'Nộp bài thành công');
  };

  return (
    <div>
      <p><Link to={`/teams/${milestone.teamId}`}>← Nhóm {team.name}</Link></p>
      <h2>{milestone.title}</h2>
      <p className="muted">Hạn nộp: <b>{fmtDate(milestone.dueDate)}</b> {milestone.overdue && <Badge kind="bad">Quá hạn chưa nộp</Badge>}</p>
      {milestone.description && <p>{milestone.description}</p>}
      <Alert error={error} ok={ok} />

      <h3>Bảng công việc</h3>
      <div className="kanban">
        {COLUMNS.map(([status, label]) => (
          <div className="column" key={status}>
            <h4>{label} <span className="muted">({tasks.filter((t) => t.status === status).length})</span></h4>
            {tasks.filter((t) => t.status === status).map((t) => (
              <div className="task" key={t.id}>
                <b>{t.title}</b>
                {t.description && <div className="muted small">{t.description}</div>}
                <div className="small">{t.assignee ? t.assignee.fullName : <span className="muted">Chưa giao</span>}{t.dueDate && ` · hạn ${fmtDate(t.dueDate)}`}</div>
                {isMember && (
                  <div className="actions">
                    {COLUMNS.filter(([s]) => s !== status).map(([s, l]) => (
                      <button key={s} onClick={() => move(t, s)}>→ {l}</button>
                    ))}
                    {isLeader && <button className="danger" onClick={() => window.confirm('Xóa task?') && run(() => api.delete(`/tasks/${t.id}`))}>Xóa</button>}
                  </div>
                )}
              </div>
            ))}
          </div>
        ))}
      </div>

      {isMember && (
        <form className="card form" onSubmit={addTask}>
          <h3>Thêm task</h3>
          <div className="row">
            <label>Tên task<input value={taskForm.title} onChange={(e) => setTaskForm({ ...taskForm, title: e.target.value })} required /></label>
            <label>Giao cho
              <select value={taskForm.assigneeId} onChange={(e) => setTaskForm({ ...taskForm, assigneeId: e.target.value })}>
                <option value="">— Chưa giao —</option>
                {team.members.map((m) => <option key={m.id} value={m.id}>{m.fullName}</option>)}
              </select>
            </label>
            <label>Hạn<input type="date" value={taskForm.dueDate} onChange={(e) => setTaskForm({ ...taskForm, dueDate: e.target.value })} /></label>
          </div>
          <label>Mô tả<input value={taskForm.description} onChange={(e) => setTaskForm({ ...taskForm, description: e.target.value })} /></label>
          <button className="primary" type="submit">Thêm task</button>
        </form>
      )}

      <h3>Bài nộp</h3>
      {isMember && (
        <form className="card form" onSubmit={upload}>
          <div className="row">
            <label>File báo cáo/sản phẩm (tối đa 20MB)<input type="file" onChange={(e) => setFile(e.target.files[0])} required /></label>
            <label>Ghi chú<input value={note} onChange={(e) => setNote(e.target.value)} /></label>
          </div>
          <button className="primary" type="submit">Nộp bài (phiên bản mới)</button>
        </form>
      )}
      {subs.map((s) => (
        <SubmissionCard key={s.id} sub={s} canFeedback={user.role === 'LECTURER'} onError={setError} />
      ))}
      {subs.length === 0 && <p className="muted">Chưa có bài nộp.</p>}
    </div>
  );
}

function SubmissionCard({ sub, canFeedback, onError }) {
  const [feedback, setFeedback] = useState([]);
  const [text, setText] = useState('');

  const load = () => api.get(`/submissions/${sub.id}/feedback`).then((r) => setFeedback(r.data)).catch(() => {});
  useEffect(() => { load(); }, [sub.id]);

  const send = async (e) => {
    e.preventDefault();
    try {
      await api.post(`/submissions/${sub.id}/feedback`, { comment: text });
      setText('');
      load();
    } catch (err) {
      onError(errMsg(err));
    }
  };

  return (
    <div className="card">
      <div className="row between">
        <div>
          <b>Phiên bản {sub.version}</b> — {sub.fileName}{' '}
          {sub.late ? <Badge kind="bad">Nộp trễ</Badge> : <Badge kind="good">Đúng hạn</Badge>}
          <div className="muted small">{sub.submittedBy.fullName} · {fmtTime(sub.submittedAt)}{sub.note && ` · ${sub.note}`}</div>
        </div>
        <button onClick={() => downloadSubmission(sub).catch((e) => onError(errMsg(e)))}>Tải xuống</button>
      </div>
      {feedback.map((f) => (
        <div className="feedback" key={f.id}>
          <b>{f.lecturer.fullName}</b> <span className="muted small">{fmtTime(f.createdAt)}</span>
          <div>{f.comment}</div>
        </div>
      ))}
      {canFeedback && (
        <form className="row" onSubmit={send}>
          <input placeholder="Nhập nhận xét cho bài nộp này…" value={text} onChange={(e) => setText(e.target.value)} required />
          <button className="primary" type="submit">Gửi phản hồi</button>
        </form>
      )}
    </div>
  );
}
