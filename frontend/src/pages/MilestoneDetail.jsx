import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { downloadSubmission, downloadTaskResult, errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, fmtDate, fmtTime } from '../ui.jsx';

const COLUMNS = [
  ['TODO', 'Cần làm', ''],
  ['DOING', 'Đang làm', 'info'],
  ['REVIEW', 'Chờ xác nhận', 'warn'],
  ['DONE', 'Hoàn thành', 'good'],
];

export default function MilestoneDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const [milestone, setMilestone] = useState(null);
  const [parent, setParent] = useState(null);
  const [team, setTeam] = useState(null);
  const [subs, setSubs] = useState([]); // milestone phu (khi dang xem milestone chinh)
  const [tasks, setTasks] = useState([]);
  const [submissions, setSubmissions] = useState([]);
  const [taskForm, setTaskForm] = useState({ title: '', description: '', assigneeId: '', dueDate: '' });
  const [file, setFile] = useState(null);
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const load = async () => {
    try {
      const m = (await api.get(`/milestones/${id}`)).data;
      setMilestone(m);
      const t = await api.get(`/teams/${m.teamId}`);
      setTeam(t.data);
      if (m.parentId) {
        setParent((await api.get(`/milestones/${m.parentId}`)).data);
        setTasks((await api.get(`/milestones/${id}/tasks`)).data);
      } else {
        setParent(null);
        const all = (await api.get(`/teams/${m.teamId}/milestones`)).data;
        setSubs(all.filter((x) => x.parentId === m.id));
        setSubmissions((await api.get(`/milestones/${id}/submissions`)).data);
      }
    } catch (e) {
      setError(errMsg(e));
    }
  };
  useEffect(() => { setMilestone(null); load(); }, [id]);

  const run = async (fn, msg) => {
    setError(''); setOk('');
    try { await fn(); if (msg) setOk(msg); await load(); } catch (e) { setError(errMsg(e)); }
  };

  if (!milestone || !team) return <Alert error={error} />;

  const isMain = !milestone.parentId;
  const isMember = team.members.some((m) => m.id === user.id);
  const isLeader = team.leader.id === user.id;
  const canLead = isLeader || user.role === 'ADMIN'; // chi nhom truong (va admin) quan ly task

  const addTask = (e) => {
    e.preventDefault();
    const body = { ...taskForm, assigneeId: taskForm.assigneeId ? Number(taskForm.assigneeId) : null, dueDate: taskForm.dueDate || null };
    run(async () => {
      await api.post(`/milestones/${id}/tasks`, body);
      setTaskForm({ title: '', description: '', assigneeId: '', dueDate: '' });
    });
  };

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
      <p>
        <Link to={`/teams/${milestone.teamId}`}>← {team.name}</Link>
        {parent && <> / <Link to={`/milestones/${parent.id}`}>{parent.title}</Link></>}
      </p>
      <h2>{milestone.title} <Badge kind={isMain ? 'warn' : ''}>{isMain ? 'Milestone chính' : 'Milestone phụ'}</Badge></h2>
      <p className="muted">Hạn: <b>{fmtDate(milestone.dueDate)}</b> {milestone.overdue && <Badge kind="bad">Quá hạn</Badge>}</p>
      {milestone.description && <p>{milestone.description}</p>}
      <Alert error={error} ok={ok} />

      {isMain ? (
        <>
          <h3>Milestone phụ của nhóm</h3>
          <table className="table">
            <thead><tr><th>Milestone phụ</th><th>Hạn</th><th>Task</th><th>Chờ xác nhận</th></tr></thead>
            <tbody>
              {subs.map((s) => (
                <tr key={s.id}>
                  <td><Link to={`/milestones/${s.id}`}>{s.title}</Link></td>
                  <td>{fmtDate(s.dueDate)}</td>
                  <td>{s.doneTasks}/{s.totalTasks}</td>
                  <td>{s.pendingReview > 0 ? <Badge kind="warn">{s.pendingReview}</Badge> : 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {subs.length === 0 && <p className="muted">Nhóm trưởng chưa tạo milestone phụ nào.</p>}

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
          {submissions.map((s) => (
            <SubmissionCard key={s.id} sub={s} canFeedback={user.role === 'LECTURER'} onError={setError} />
          ))}
          {submissions.length === 0 && <p className="muted">Chưa có bài nộp.</p>}
        </>
      ) : (
        <>
          <h3>Bảng công việc</h3>
          <div className="kanban four">
            {COLUMNS.map(([status, label, kind]) => {
              const list = tasks.filter((t) => t.status === status);
              return (
                <div className="column" key={status}>
                  <h4>{label} <Badge kind={kind}>{list.length}</Badge></h4>
                  {list.map((t) => (
                    <TaskCard key={t.id} task={t} user={user} canLead={canLead} onRun={run} />
                  ))}
                </div>
              );
            })}
          </div>

          {canLead && (
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
                <label>Hạn (không sau {fmtDate(milestone.dueDate)})<input type="date" max={milestone.dueDate} value={taskForm.dueDate} onChange={(e) => setTaskForm({ ...taskForm, dueDate: e.target.value })} /></label>
              </div>
              <label>Mô tả<input value={taskForm.description} onChange={(e) => setTaskForm({ ...taskForm, description: e.target.value })} /></label>
              <button className="primary" type="submit">Thêm task</button>
            </form>
          )}
          {!canLead && <p className="muted small">Chỉ nhóm trưởng mới thêm hoặc giao task trong nhóm.</p>}
        </>
      )}
    </div>
  );
}

/** The task: noi dung hien thi va nut bam thay doi theo trang thai va vai tro (thanh vien / nhom truong). */
function TaskCard({ task, user, canLead, onRun }) {
  const [reporting, setReporting] = useState(false);
  const [note, setNote] = useState('');
  const [file, setFile] = useState(null);
  const isAssignee = task.assignee?.id === user.id;
  const assigneeName = task.assignee ? task.assignee.fullName : null;

  const start = () => onRun(() => api.post(`/tasks/${task.id}/start`));
  const confirm = () => onRun(() => api.post(`/tasks/${task.id}/confirm`));
  const reject = () => {
    const reason = window.prompt('Nội dung cần sửa:');
    if (!reason || !reason.trim()) return;
    onRun(() => api.post(`/tasks/${task.id}/reject`, { note: reason }));
  };
  const remove = () => window.confirm('Xóa task?') && onRun(() => api.delete(`/tasks/${task.id}`));
  const submit = (e) => {
    e.preventDefault();
    const fd = new FormData();
    if (file) fd.append('file', file);
    if (note) fd.append('note', note);
    onRun(async () => {
      await api.post(`/tasks/${task.id}/submit`, fd);
      setReporting(false); setNote(''); setFile(null);
    });
  };

  return (
    <div className={`task ${task.status.toLowerCase()}`}>
      <div className="row between">
        <b>{task.title}</b>
        {canLead && <button className="link small" onClick={remove}>Xóa</button>}
      </div>
      {task.description && <div className="task-desc">{task.description}</div>}
      <div className="small muted">
        Người thực hiện: {assigneeName || 'Chưa giao'}{task.dueDate && ` · Hạn: ${fmtDate(task.dueDate)}`}
      </div>

      {task.status === 'TODO' && (isAssignee || canLead) && task.assignee && (
        <button className="btn-block" onClick={start}>Bắt đầu làm</button>
      )}

      {task.status === 'DOING' && (
        <>
          {task.reviewNote && <div className="note warn">Nhóm trưởng yêu cầu sửa: {task.reviewNote}</div>}
          {isAssignee && !reporting && <button className="btn-block green" onClick={() => setReporting(true)}>Báo hoàn thành</button>}
          {isAssignee && reporting && (
            <form className="report" onSubmit={submit}>
              <textarea rows={2} placeholder="Ghi chú kết quả (tùy chọn)" value={note} onChange={(e) => setNote(e.target.value)} />
              <input type="file" onChange={(e) => setFile(e.target.files[0])} />
              <div className="two-btn">
                <button className="green" type="submit">Gửi yêu cầu hoàn thành</button>
                <button type="button" onClick={() => setReporting(false)}>Hủy</button>
              </div>
            </form>
          )}
          {isAssignee && <div className="small muted">Thành viên có thể gửi file hoặc ghi chú kết quả trước khi báo hoàn thành.</div>}
        </>
      )}

      {task.status === 'REVIEW' && (
        <>
          <div className="note">
            {canLead && assigneeName ? `${assigneeName} đã gửi yêu cầu hoàn thành.` : 'Đã báo hoàn thành, đang chờ nhóm trưởng xác nhận.'}
          </div>
          <Result task={task} />
          {canLead && (
            <div className="two-btn">
              <button className="green" onClick={confirm}>Xác nhận</button>
              <button className="outline-danger" onClick={reject}>Yêu cầu sửa</button>
            </div>
          )}
        </>
      )}

      {task.status === 'DONE' && (
        <>
          <Result task={task} />
          {task.confirmedAt && <div className="small muted">Xác nhận lúc: {fmtTime(task.confirmedAt)}</div>}
        </>
      )}
    </div>
  );
}

function Result({ task }) {
  if (!task.submittedAt) return null;
  return (
    <div className="result small">
      <div>Nộp lần cuối: <b>{fmtTime(task.submittedAt)}</b></div>
      {task.resultNote && <div>Ghi chú: {task.resultNote}</div>}
      {task.resultFileName && (
        <button className="link small" onClick={() => downloadTaskResult(task).catch(() => {})}>📎 {task.resultFileName}</button>
      )}
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
