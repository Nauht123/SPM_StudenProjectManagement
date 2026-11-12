import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { downloadSubmission, downloadTaskAttempt, downloadTaskResult, errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, fmtDate, fmtTime } from '../ui.jsx';
import PeerEvaluation from './PeerEvaluation.jsx';
import TaskComments from './TaskComments.jsx';

const COLUMNS = [
  ['TODO', 'Cần làm', ''],
  ['DOING', 'Đang làm', 'info'],
  ['REVIEW', 'Chờ xác nhận', 'warn'],
  ['DONE', 'Hoàn thành', 'good'],
];

const EMPTY_TASK = { title: '', description: '', assigneeId: '', dueDate: '', difficulty: 3, weight: 3, workloadHours: 4 };
const EMPTY_SUGGEST = { title: '', description: '', dueDate: '', difficulty: 3, workloadHours: 4 };

const ACTION_LABEL = {
  CREATED: 'Tạo task', SUGGESTED: 'Đề xuất task', SUGGESTION_APPROVED: 'Duyệt đề xuất', SUGGESTION_REJECTED: 'Từ chối đề xuất',
  UPDATED: 'Chỉnh sửa', ASSIGNED: 'Phân công', UNASSIGNED: 'Bỏ phân công', STARTED: 'Bắt đầu làm',
  SUBMITTED: 'Báo hoàn thành', CONFIRMED: 'Xác nhận', REVISION_REQUESTED: 'Yêu cầu sửa', CANCELLED: 'Hủy task',
};
const OUTCOME_LABEL = { PENDING: ['Chờ xác nhận', 'warn'], APPROVED: ['Đã duyệt', 'good'], REVISION_REQUESTED: ['Yêu cầu sửa', 'bad'] };

const numOrNull = (v) => (v === '' || v === null || v === undefined ? null : Number(v));

function taskBody(f) {
  return {
    title: f.title,
    description: f.description,
    assigneeId: numOrNull(f.assigneeId),
    dueDate: f.dueDate || null,
    difficulty: numOrNull(f.difficulty),
    weight: numOrNull(f.weight),
    workloadHours: numOrNull(f.workloadHours),
  };
}

/** Ba truong do kho / trong so / khoi luong dung chung cho form them, de xuat, duyet, sua task. */
function WeightFields({ value, onChange, withWeight = true }) {
  return (
    <>
      <label>Độ khó (1–5)
        <select value={value.difficulty} onChange={(e) => onChange({ ...value, difficulty: e.target.value })}>
          {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
        </select>
      </label>
      {withWeight && (
        <label>Trọng số (1–10)
          <input type="number" min="1" max="10" value={value.weight} onChange={(e) => onChange({ ...value, weight: e.target.value })} required />
        </label>
      )}
      <label>Khối lượng (giờ)
        <input type="number" min="1" max="200" value={value.workloadHours} onChange={(e) => onChange({ ...value, workloadHours: e.target.value })} required />
      </label>
    </>
  );
}

export default function MilestoneDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const [milestone, setMilestone] = useState(null);
  const [parent, setParent] = useState(null);
  const [team, setTeam] = useState(null);
  const [subs, setSubs] = useState([]); // milestone phu (khi dang xem milestone chinh)
  const [tasks, setTasks] = useState([]);
  const [submissions, setSubmissions] = useState([]);
  const [taskForm, setTaskForm] = useState(EMPTY_TASK);
  const [suggestForm, setSuggestForm] = useState(EMPTY_SUGGEST);
  const [file, setFile] = useState(null);
  const [note, setNote] = useState('');
  const [contributorIds, setContributorIds] = useState([]);
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
  const archived = milestone.archived || !!parent?.archived;
  const canLead = (isLeader || user.role === 'ADMIN') && !archived; // chi nhom truong (va admin) quan ly task
  const canSuggest = user.role === 'STUDENT' && isMember && !archived;

  const addTask = (e) => {
    e.preventDefault();
    run(async () => {
      await api.post(`/milestones/${id}/tasks`, taskBody(taskForm));
      setTaskForm(EMPTY_TASK);
    });
  };

  const suggest = (e) => {
    e.preventDefault();
    const body = {
      title: suggestForm.title,
      description: suggestForm.description,
      dueDate: suggestForm.dueDate || null,
      difficulty: numOrNull(suggestForm.difficulty),
      workloadHours: numOrNull(suggestForm.workloadHours),
    };
    run(async () => {
      await api.post(`/milestones/${id}/tasks/suggest`, body);
      setSuggestForm(EMPTY_SUGGEST);
    }, 'Đã gửi đề xuất, đang chờ nhóm trưởng duyệt');
  };

  const upload = (e) => {
    e.preventDefault();
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    if (note) fd.append('note', note);
    contributorIds.forEach((cid) => fd.append('contributorIds', cid));
    run(async () => {
      await api.post(`/milestones/${id}/submissions`, fd);
      setFile(null); setNote(''); setContributorIds([]);
      e.target.reset();
    }, 'Nộp bài thành công');
  };

  const toggleContributor = (uid) =>
    setContributorIds((cur) => (cur.includes(uid) ? cur.filter((x) => x !== uid) : [...cur, uid]));

  const suggestions = tasks.filter((t) => t.status === 'SUGGESTED');
  const cancelled = tasks.filter((t) => t.status === 'CANCELLED');
  const activeTasks = tasks.filter((t) => !['SUGGESTED', 'CANCELLED'].includes(t.status));
  const totalWeight = activeTasks.reduce((s, t) => s + t.weight, 0);
  const doneWeight = activeTasks.filter((t) => t.status === 'DONE').reduce((s, t) => s + t.weight, 0);

  return (
    <div>
      <p>
        <Link to={`/teams/${milestone.teamId}`}>← {team.name}</Link>
        {parent && <> / <Link to={`/milestones/${parent.id}`}>{parent.title}</Link></>}
      </p>
      <h2>
        {milestone.title} <Badge kind={isMain ? 'warn' : ''}>{isMain ? 'Milestone chính' : 'Milestone phụ'}</Badge>{' '}
        {archived && <Badge kind="bad">Đã lưu trữ</Badge>}
      </h2>
      <p className="muted">Hạn: <b>{fmtDate(milestone.dueDate)}</b> {milestone.overdue && <Badge kind="bad">Quá hạn</Badge>}</p>
      {milestone.description && <p>{milestone.description}</p>}
      {archived && <div className="note">Milestone này đã được lưu trữ: chỉ xem, không chỉnh sửa và không tính vào tiến độ.</div>}
      <Alert error={error} ok={ok} />

      {isMain ? (
        <>
          <h3>Milestone phụ của nhóm</h3>
          <table className="table">
            <thead><tr><th>Milestone phụ</th><th>Hạn</th><th>Task</th><th>Tiến độ (theo trọng số)</th><th>Chờ xác nhận</th></tr></thead>
            <tbody>
              {subs.map((s) => (
                <tr key={s.id}>
                  <td><Link to={`/milestones/${s.id}`}>{s.title}</Link> {s.archived && <Badge kind="bad">Lưu trữ</Badge>}</td>
                  <td>{fmtDate(s.dueDate)}</td>
                  <td>{s.doneTasks}/{s.totalTasks}</td>
                  <td>{s.percent}%</td>
                  <td>{s.pendingReview > 0 ? <Badge kind="warn">{s.pendingReview}</Badge> : 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {subs.length === 0 && <p className="muted">Nhóm trưởng chưa tạo milestone phụ nào.</p>}

          <h3>Bài nộp</h3>
          {isMember && !archived && (
            <form className="card form" onSubmit={upload}>
              <div className="row">
                <label>File báo cáo/sản phẩm (tối đa 20MB)<input type="file" onChange={(e) => setFile(e.target.files[0])} required /></label>
                <label>Ghi chú<input value={note} onChange={(e) => setNote(e.target.value)} /></label>
              </div>
              <fieldset className="contributors">
                <legend>Những thành viên thực sự đóng góp nội dung bài này</legend>
                <div className="chips">
                  {team.members.map((m) => (
                    <label key={m.id} className="check">
                      <input
                        type="checkbox"
                        checked={m.id === user.id || contributorIds.includes(m.id)}
                        disabled={m.id === user.id}
                        onChange={() => toggleContributor(m.id)}
                      />
                      {m.fullName}{m.id === user.id ? ' (người upload)' : ''}
                    </label>
                  ))}
                </div>
                <div className="muted small">Người upload khác với người đóng góp: hãy tích đúng những bạn đã tham gia làm bài.</div>
              </fieldset>
              <button className="primary" type="submit">Nộp bài (phiên bản mới)</button>
            </form>
          )}
          {submissions.map((s) => (
            <SubmissionCard key={s.id} sub={s} canFeedback={user.role === 'LECTURER'} onError={setError} />
          ))}
          {submissions.length === 0 && <p className="muted">Chưa có bài nộp.</p>}

          <PeerEvaluation milestone={milestone} team={team} user={user} />
        </>
      ) : (
        <>
          <div className="row between">
            <h3>Bảng công việc</h3>
            <span className="muted small">
              Tiến độ theo trọng số: <b>{totalWeight === 0 ? 0 : Math.round((doneWeight * 100) / totalWeight)}%</b> ({doneWeight}/{totalWeight} điểm trọng số)
            </span>
          </div>

          {suggestions.length > 0 && (
            <div className="card">
              <h3>Task được đề xuất ({suggestions.length})</h3>
              {suggestions.map((t) => (
                <SuggestionCard key={t.id} task={t} team={team} user={user} milestone={milestone} canLead={canLead} onRun={run} />
              ))}
            </div>
          )}

          <div className="kanban four">
            {COLUMNS.map(([status, label, kind]) => {
              const list = activeTasks.filter((t) => t.status === status);
              return (
                <div className="column" key={status}>
                  <h4>{label} <Badge kind={kind}>{list.length}</Badge></h4>
                  {list.map((t) => (
                    <TaskCard key={t.id} task={t} team={team} milestone={milestone} user={user} canLead={canLead} onRun={run} />
                  ))}
                </div>
              );
            })}
          </div>

          {cancelled.length > 0 && (
            <details className="card">
              <summary><b>Task đã hủy ({cancelled.length})</b> <span className="muted small">— vẫn lưu lịch sử, không tính vào tiến độ</span></summary>
              {cancelled.map((t) => (
                <div className="task cancelled" key={t.id}>
                  <b>{t.title}</b>
                  <div className="small muted">Hủy lúc {fmtTime(t.cancelledAt)}{t.cancelReason && ` · ${t.cancelReason}`}</div>
                  <TaskTimeline taskId={t.id} />
                </div>
              ))}
            </details>
          )}

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
              <div className="row">
                <WeightFields value={taskForm} onChange={setTaskForm} />
              </div>
              <label>Mô tả<input value={taskForm.description} onChange={(e) => setTaskForm({ ...taskForm, description: e.target.value })} /></label>
              <button className="primary" type="submit">Thêm task</button>
            </form>
          )}

          {canSuggest && (
            <form className="card form" onSubmit={suggest}>
              <h3>Đề xuất task mới</h3>
              <p className="muted small">Bạn đề xuất, nhóm trưởng sẽ duyệt, chốt trọng số và giao người thực hiện.</p>
              <div className="row">
                <label>Tên task<input value={suggestForm.title} onChange={(e) => setSuggestForm({ ...suggestForm, title: e.target.value })} required /></label>
                <label>Hạn mong muốn<input type="date" max={milestone.dueDate} value={suggestForm.dueDate} onChange={(e) => setSuggestForm({ ...suggestForm, dueDate: e.target.value })} /></label>
              </div>
              <div className="row">
                <WeightFields value={suggestForm} onChange={setSuggestForm} withWeight={false} />
              </div>
              <label>Mô tả<input value={suggestForm.description} onChange={(e) => setSuggestForm({ ...suggestForm, description: e.target.value })} /></label>
              <button className="primary" type="submit">Gửi đề xuất</button>
            </form>
          )}
          {!canLead && !archived && <p className="muted small">Chỉ nhóm trưởng mới thêm hoặc giao task trực tiếp. Thành viên có thể đề xuất task để nhóm trưởng duyệt.</p>}
        </>
      )}
    </div>
  );
}

function Chips({ task }) {
  return (
    <div className="chips small">
      <span className="chip" title="Độ khó">Khó {task.difficulty}/5</span>
      <span className="chip" title="Trọng số trong tiến độ và điểm đóng góp">Trọng số {task.weight}</span>
      <span className="chip" title="Khối lượng ước tính">{task.workloadHours}h</span>
      {task.revisionCount > 0 && <span className="chip bad" title="Số lần bị yêu cầu sửa">Sửa lại ×{task.revisionCount}</span>}
      {task.qualityRating && <span className="chip good" title="Điểm chất lượng nhóm trưởng chấm">Chất lượng {task.qualityRating}/5</span>}
    </div>
  );
}

/** Lich su task: su kien, cac lan phan cong, cac lan bao hoan thanh. */
function TaskTimeline({ taskId }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    api.get(`/tasks/${taskId}/history`).then((r) => setData(r.data)).catch((e) => setError(errMsg(e)));
  }, [taskId]);
  if (error) return <div className="note warn">{error}</div>;
  if (!data) return <p className="muted small">Đang tải lịch sử…</p>;
  return (
    <div className="timeline small">
      <b>Nhật ký thay đổi</b>
      <ul>
        {data.events.map((h) => (
          <li key={h.id}>
            <span className="muted">{fmtTime(h.createdAt)}</span> · <b>{h.actor?.fullName || 'Hệ thống'}</b> — {ACTION_LABEL[h.action] || h.action}
            {h.detail && <>: {h.detail}</>}
          </li>
        ))}
      </ul>
      {data.assignments.length > 0 && (
        <>
          <b>Lịch sử phân công</b>
          <ul>
            {data.assignments.map((a) => (
              <li key={a.id}>
                {a.assignee.fullName} — giao bởi {a.assignedBy?.fullName || '—'} lúc {fmtTime(a.assignedAt)}
                {a.endedAt ? ` · đến ${fmtTime(a.endedAt)}` : ' · hiện tại'}
              </li>
            ))}
          </ul>
        </>
      )}
      {data.submissions.length > 0 && (
        <>
          <b>Các lần báo hoàn thành</b>
          <ul>
            {data.submissions.map((a) => {
              const [label, kind] = OUTCOME_LABEL[a.outcome] || [a.outcome, ''];
              return (
                <li key={a.id}>
                  Lần {a.attemptNo}: {a.submittedBy.fullName} lúc {fmtTime(a.submittedAt)} <Badge kind={kind}>{label}</Badge>
                  {a.note && <div>Ghi chú: {a.note}</div>}
                  {a.fileName && (
                    <button className="link small" onClick={() => downloadTaskAttempt(a).catch(() => {})}>📎 {a.fileName}</button>
                  )}
                  {a.reviewNote && <div>Phản hồi: {a.reviewNote}{a.reviewedBy && ` (${a.reviewedBy.fullName})`}</div>}
                </li>
              );
            })}
          </ul>
        </>
      )}
    </div>
  );
}

/** Mot de xuat dang cho duyet: nhom truong chot nguoi giao / trong so, hoac tu choi; nguoi de xuat co the rut lai. */
function SuggestionCard({ task, team, user, milestone, canLead, onRun }) {
  const [approving, setApproving] = useState(false);
  const [form, setForm] = useState({ assigneeId: '', dueDate: task.dueDate || '', difficulty: task.difficulty, weight: task.weight, workloadHours: task.workloadHours });
  const mine = task.suggestedBy?.id === user.id;

  const approve = (e) => {
    e.preventDefault();
    onRun(async () => {
      await api.post(`/tasks/${task.id}/approve-suggestion`, {
        assigneeId: numOrNull(form.assigneeId),
        dueDate: form.dueDate || null,
        difficulty: numOrNull(form.difficulty),
        weight: numOrNull(form.weight),
        workloadHours: numOrNull(form.workloadHours),
      });
      setApproving(false);
    }, 'Đã duyệt đề xuất');
  };
  const reject = () => {
    const reason = window.prompt('Lý do từ chối đề xuất:');
    if (!reason || !reason.trim()) return;
    onRun(() => api.post(`/tasks/${task.id}/reject-suggestion`, { note: reason }));
  };
  const withdraw = () => {
    if (!window.confirm('Rút lại đề xuất này?')) return;
    onRun(() => api.post(`/tasks/${task.id}/cancel`, { note: 'Người đề xuất rút lại' }));
  };

  return (
    <div className="task suggested">
      <div className="row between">
        <b>{task.title}</b>
        <Badge kind="warn">Chờ duyệt</Badge>
      </div>
      {task.description && <div className="task-desc">{task.description}</div>}
      <div className="small muted">Đề xuất bởi {task.suggestedBy?.fullName}{task.createdAt && ` lúc ${fmtTime(task.createdAt)}`}{task.dueDate && ` · Hạn mong muốn: ${fmtDate(task.dueDate)}`}</div>
      <Chips task={task} />
      {canLead && !approving && (
        <div className="two-btn">
          <button className="green" onClick={() => setApproving(true)}>Duyệt…</button>
          <button className="outline-danger" onClick={reject}>Từ chối</button>
        </div>
      )}
      {canLead && approving && (
        <form className="report" onSubmit={approve}>
          <label>Giao cho
            <select value={form.assigneeId} onChange={(e) => setForm({ ...form, assigneeId: e.target.value })}>
              <option value="">— Chưa giao —</option>
              {team.members.map((m) => <option key={m.id} value={m.id}>{m.fullName}</option>)}
            </select>
          </label>
          <label>Hạn (không sau {fmtDate(milestone.dueDate)})<input type="date" max={milestone.dueDate} value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} /></label>
          <div className="row"><WeightFields value={form} onChange={setForm} /></div>
          <div className="two-btn">
            <button className="green" type="submit">Duyệt đề xuất</button>
            <button type="button" onClick={() => setApproving(false)}>Hủy</button>
          </div>
        </form>
      )}
      {mine && !canLead && <button className="link small" onClick={withdraw}>Rút lại đề xuất</button>}
    </div>
  );
}

/** The task: noi dung hien thi va nut bam thay doi theo trang thai va vai tro (thanh vien / nhom truong). */
function TaskCard({ task, team, milestone, user, canLead, onRun }) {
  const [reporting, setReporting] = useState(false);
  const [editing, setEditing] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [note, setNote] = useState('');
  const [file, setFile] = useState(null);
  const [quality, setQuality] = useState('');
  const [form, setForm] = useState({
    title: task.title, description: task.description || '', assigneeId: task.assignee?.id || '', dueDate: task.dueDate || '',
    difficulty: task.difficulty, weight: task.weight, workloadHours: task.workloadHours,
  });
  const isAssignee = task.assignee?.id === user.id;
  const assigneeName = task.assignee ? task.assignee.fullName : null;
  const canReassign = task.status === 'TODO' || task.status === 'DOING';

  const start = () => onRun(() => api.post(`/tasks/${task.id}/start`));
  const confirm = () => onRun(() => api.post(`/tasks/${task.id}/confirm`, quality ? { qualityRating: Number(quality) } : {}));
  const reject = () => {
    const reason = window.prompt('Nội dung cần sửa:');
    if (!reason || !reason.trim()) return;
    onRun(() => api.post(`/tasks/${task.id}/reject`, { note: reason }));
  };
  const cancel = () => {
    const reason = window.prompt('Lý do hủy task (task được giữ lại trong lịch sử):');
    if (!reason || !reason.trim()) return;
    onRun(() => api.post(`/tasks/${task.id}/cancel`, { note: reason }));
  };
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
  const save = (e) => {
    e.preventDefault();
    onRun(async () => {
      await api.put(`/tasks/${task.id}`, taskBody(form));
      setEditing(false);
    }, 'Đã cập nhật task');
  };

  return (
    <div className={`task ${task.status.toLowerCase()}`}>
      <div className="row between">
        <b>{task.title}</b>
        {canLead && task.status !== 'DONE' && <button className="link small" onClick={cancel}>Hủy task</button>}
      </div>
      {task.description && <div className="task-desc">{task.description}</div>}
      <div className="small muted">
        Người thực hiện: {assigneeName || 'Chưa giao'}{task.dueDate && ` · Hạn: ${fmtDate(task.dueDate)}`}
      </div>
      {task.assignee && task.assignedAt && (
        <div className="small muted">Giao bởi {task.assignedBy?.fullName || '—'} lúc {fmtTime(task.assignedAt)}</div>
      )}
      <Chips task={task} />

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
            <>
              <label className="small">Điểm chất lượng (tùy chọn)
                <select value={quality} onChange={(e) => setQuality(e.target.value)}>
                  <option value="">— Chưa chấm —</option>
                  {[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n}/5</option>)}
                </select>
              </label>
              <div className="two-btn">
                <button className="green" onClick={confirm}>Xác nhận</button>
                <button className="outline-danger" onClick={reject}>Yêu cầu sửa</button>
              </div>
            </>
          )}
        </>
      )}

      {task.status === 'DONE' && (
        <>
          <Result task={task} />
          {task.confirmedAt && <div className="small muted">Xác nhận lúc: {fmtTime(task.confirmedAt)}</div>}
        </>
      )}

      <div className="task-links small">
        {canLead && task.status !== 'DONE' && !editing && <button className="link small" onClick={() => setEditing(true)}>Sửa / giao lại</button>}
        <button className="link small" onClick={() => setShowHistory((v) => !v)}>{showHistory ? 'Ẩn lịch sử & bình luận' : 'Lịch sử & bình luận'}</button>
      </div>

      {editing && (
        <form className="report" onSubmit={save}>
          <label>Tên task<input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required /></label>
          <label>Mô tả<input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
          <label>Giao cho
            <select value={form.assigneeId} disabled={!canReassign} onChange={(e) => setForm({ ...form, assigneeId: e.target.value })}>
              <option value="">— Chưa giao —</option>
              {team.members.map((m) => <option key={m.id} value={m.id}>{m.fullName}</option>)}
            </select>
          </label>
          <label>Hạn (không sau {fmtDate(milestone.dueDate)})<input type="date" max={milestone.dueDate} value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} /></label>
          <div className="row"><WeightFields value={form} onChange={setForm} /></div>
          <div className="two-btn">
            <button className="green" type="submit">Lưu</button>
            <button type="button" onClick={() => setEditing(false)}>Hủy</button>
          </div>
        </form>
      )}
      {showHistory && <><TaskComments taskId={task.id} /><TaskTimeline taskId={task.id} /></>}
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

  const others = (sub.contributors || []).filter((c) => c.id !== sub.submittedBy.id);

  return (
    <div className="card">
      <div className="row between">
        <div>
          <b>Phiên bản {sub.version}</b> — {sub.fileName}{' '}
          {sub.late ? <Badge kind="bad">Nộp trễ</Badge> : <Badge kind="good">Đúng hạn</Badge>}
          <div className="muted small">Người upload: {sub.submittedBy.fullName} · {fmtTime(sub.submittedAt)}{sub.note && ` · ${sub.note}`}</div>
          <div className="muted small">
            Người đóng góp: {(sub.contributors || []).length ? (sub.contributors || []).map((c) => c.fullName).join(', ') : sub.submittedBy.fullName}
            {others.length > 0 && ` (${others.length} thành viên khác ngoài người upload)`}
          </div>
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
