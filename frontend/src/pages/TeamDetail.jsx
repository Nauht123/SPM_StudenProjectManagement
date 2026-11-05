import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, Progress, StatCard, fmtDate } from '../ui.jsx';

function MilestoneStatus({ m }) {
  if (m.archived) return <Badge kind="bad">Đã lưu trữ</Badge>;
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
    if (!window.confirm(`Xóa milestone "${m.title}"? Chỉ xóa được khi milestone chưa có dữ liệu.`)) return;
    run(() => api.delete(`/milestones/${m.id}`));
  };

  // Milestone da co bai nop / milestone phu / task thi luu tru (archive) thay vi xoa
  const archive = (m) => {
    if (!window.confirm(`Lưu trữ milestone "${m.title}"? Milestone sẽ chuyển sang chỉ xem và không tính vào tiến độ.`)) return;
    run(() => api.post(`/milestones/${m.id}/archive`));
  };
  const restore = (m) => run(() => api.post(`/milestones/${m.id}/restore`));
  const isEmpty = (m) => m.submissions === 0 && m.subMilestones === 0 && m.totalTasks === 0 && !milestones.some((x) => x.parentId === m.id);

  if (!team) return <Alert error={error} />;

  // Quyen: giang vien quan ly milestone chinh; nhom truong (hoac admin) quan ly milestone phu
  const canMain = user.role === 'LECTURER' || user.role === 'ADMIN';
  const canSub = user.role === 'ADMIN' || (user.role === 'STUDENT' && team.leader.id === user.id);
  const allMains = milestones.filter((m) => !m.parentId);
  const mains = allMains.filter((m) => !m.archived);
  const archivedMains = allMains.filter((m) => m.archived);
  const subsOf = (id) => milestones.filter((m) => m.parentId === id && !m.archived);
  const archivedSubsOf = (id) => milestones.filter((m) => m.parentId === id && m.archived);

  return (
    <div>
      <h2>{team.name}</h2>
      <Alert error={error} />
      {progress && (
        <div className="stat-grid four">
          <StatCard tone="blue" icon="target" value={`${progress.percent}%`} label="Tiến độ (theo trọng số task)" note={`${progress.doneWeight}/${progress.totalWeight} điểm trọng số`} />
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

      <ContributionPanel teamId={teamId} reloadKey={milestones.length} />

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
              {canMain && (
                <div className="row">
                  <button onClick={() => archive(m)}>Lưu trữ</button>
                  {isEmpty(m) && <button className="danger" onClick={() => remove(m)}>Xóa</button>}
                </div>
              )}
            </div>

            {subs.length > 0 && (
              <table className="table plain">
                <thead><tr><th>Milestone phụ</th><th>Hạn</th><th>Task</th><th>Trạng thái</th><th /></tr></thead>
                <tbody>
                  {subs.map((s) => (
                    <tr key={s.id}>
                      <td><Link to={`/milestones/${s.id}`}>{s.title}</Link></td>
                      <td>{fmtDate(s.dueDate)}</td>
                      <td>{s.doneTasks}/{s.totalTasks} ({s.percent}%){s.pendingReview > 0 && <> <Badge kind="warn">{s.pendingReview} chờ xác nhận</Badge></>}</td>
                      <td><MilestoneStatus m={s} /></td>
                      <td className="actions">
                        {canSub && <button onClick={() => archive(s)}>Lưu trữ</button>}{' '}
                        {canSub && s.totalTasks === 0 && <button className="danger" onClick={() => remove(s)}>Xóa</button>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {subs.length === 0 && <p className="muted small">Chưa có milestone phụ.</p>}
            {archivedSubsOf(m.id).length > 0 && (
              <details className="subform">
                <summary>Milestone phụ đã lưu trữ ({archivedSubsOf(m.id).length})</summary>
                <ul>
                  {archivedSubsOf(m.id).map((s) => (
                    <li key={s.id}>
                      <Link to={`/milestones/${s.id}`}>{s.title}</Link>{' '}
                      {canSub && <button className="link small" onClick={() => restore(s)}>Khôi phục</button>}
                    </li>
                  ))}
                </ul>
              </details>
            )}

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

      {archivedMains.length > 0 && (
        <details className="card">
          <summary><b>Milestone chính đã lưu trữ ({archivedMains.length})</b> <span className="muted small">— chỉ xem, không tính vào tiến độ</span></summary>
          <ul>
            {archivedMains.map((m) => (
              <li key={m.id}>
                <Link to={`/milestones/${m.id}`}>{m.title}</Link> <span className="muted small">hạn {fmtDate(m.dueDate)} · {m.submissions} bài nộp</span>{' '}
                {canMain && <button className="link small" onClick={() => restore(m)}>Khôi phục</button>}
              </li>
            ))}
          </ul>
        </details>
      )}

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

/** Dong gop ca nhan: GV/admin/nhom truong xem ca nhom, thanh vien chi xem cua minh. */
function ContributionPanel({ teamId, reloadKey }) {
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(null);

  useEffect(() => {
    api.get(`/teams/${teamId}/contributions`).then((r) => setReport(r.data)).catch((e) => setError(errMsg(e)));
  }, [teamId, reloadKey]);

  if (error) return <Alert error={error} />;
  if (!report) return null;
  const STATUS = { TODO: 'Cần làm', DOING: 'Đang làm', REVIEW: 'Chờ xác nhận', DONE: 'Hoàn thành' };

  return (
    <div className="card flush">
      <div className="card-head pad">
        <h3>Đóng góp cá nhân</h3>
        <span className="muted small">Tổng điểm nhóm: {report.totalScore}</span>
      </div>
      <table className="table plain contrib">
        <thead>
          <tr>
            <th>Thành viên</th><th>Tỷ lệ đóng góp</th><th>Điểm</th><th>Task xong / được giao</th>
            <th>Đúng hạn</th><th>Trễ hạn</th><th>Chưa xong</th><th>Bị yêu cầu sửa</th><th>Tiến độ cá nhân</th>
            <th>Bài nộp (upload / đóng góp)</th><th />
          </tr>
        </thead>
        <tbody>
          {report.members.map((r) => (
            <MemberRows key={r.member.id} r={r} open={open === r.member.id} onToggle={() => setOpen(open === r.member.id ? null : r.member.id)} status={STATUS} />
          ))}
        </tbody>
      </table>
      {report.members.length === 0 && <p className="muted pad">Chưa có dữ liệu.</p>}
      <div className="formula small muted pad">{report.formula}</div>
    </div>
  );
}

function MemberRows({ r, open, onToggle, status }) {
  return (
    <>
      <tr>
        <td><b>{r.member.fullName}</b>{r.leader && <> <Badge kind="warn">Nhóm trưởng</Badge></>}</td>
        <td style={{ minWidth: 130 }}>
          <div className="contrib-bar" title={`${r.sharePercent}%`}><div style={{ width: `${r.sharePercent}%` }} /></div>
          <span className="small muted">{r.sharePercent}%</span>
        </td>
        <td><b>{r.score}</b></td>
        <td>{r.doneTasks}/{r.assignedTasks}</td>
        <td>{r.onTimeTasks}</td>
        <td>{r.lateTasks > 0 ? <Badge kind="bad">{r.lateTasks}</Badge> : 0}</td>
        <td>{r.unfinishedTasks}{r.overdueOpenTasks > 0 && <> <Badge kind="bad">{r.overdueOpenTasks} quá hạn</Badge></>}</td>
        <td>{r.revisionCount}</td>
        <td style={{ minWidth: 120 }}><Progress percent={r.progressPercent} /><span className="small muted">{r.weightDone}/{r.weightAssigned} điểm TS</span></td>
        <td>{r.submissionsUploaded} / {r.submissionsContributed}</td>
        <td><button className="link small" onClick={onToggle}>{open ? 'Ẩn chi tiết' : 'Chi tiết'}</button></td>
      </tr>
      {open && (
        <tr>
          <td colSpan={11}>
            <div className="small muted">
              Khối lượng đã hoàn thành: {r.workloadHoursDone}h · Task hủy: {r.cancelledTasks} · Đề xuất task: {r.suggestedTasks} (được duyệt {r.approvedSuggestions})
            </div>
            <table className="table plain contrib">
              <thead><tr><th>Task</th><th>Milestone phụ</th><th>Trạng thái</th><th>Trọng số</th><th>Độ khó</th><th>Giờ</th><th>Hạn</th><th>Đúng hạn</th><th>Sửa lại</th><th>Chất lượng</th><th>Điểm</th></tr></thead>
              <tbody>
                {r.tasks.map((t) => (
                  <tr key={t.taskId}>
                    <td>{t.title}</td><td>{t.milestoneTitle}</td><td>{status[t.status] || t.status}</td>
                    <td>{t.weight}</td><td>{t.difficulty}</td><td>{t.workloadHours}</td><td>{fmtDate(t.dueDate)}</td>
                    <td>{t.onTime === null ? '—' : t.onTime ? <Badge kind="good">Đúng hạn</Badge> : <Badge kind="bad">Trễ</Badge>}</td>
                    <td>{t.revisions}</td><td>{t.qualityRating ? `${t.qualityRating}/5` : '—'}</td><td>{t.status === 'DONE' ? t.points : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {r.tasks.length === 0 && <p className="muted small">Chưa được giao task nào.</p>}
          </td>
        </tr>
      )}
    </>
  );
}
