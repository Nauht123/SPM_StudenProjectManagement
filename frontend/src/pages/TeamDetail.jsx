import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, StatCard, fmtDate } from '../ui.jsx';

const GROUP_COLORS = ['#579bfc', '#00c875', '#a25ddc', '#fdab3d', '#e2445c', '#66ccff'];

/** O trang thai to mau day du (kieu monday.com). */
function StatusCell({ m }) {
  let label = 'Đang thực hiện';
  let bg = '#fdab3d';
  const isMain = m.parentId === null || m.parentId === undefined;
  if (m.archived) { label = 'Đã lưu trữ'; bg = '#a0a8b8'; }
  else if (m.overdue) { label = 'Quá hạn'; bg = '#e2445c'; }
  else if (isMain ? m.submissions > 0 : m.totalTasks > 0 && m.doneTasks === m.totalTasks) {
    label = isMain ? 'Đã nộp' : 'Hoàn thành'; bg = '#00c875';
  } else if (m.totalTasks === 0 && !isMain) { label = 'Chưa có task'; bg = '#c4c4c4'; }
  return <span className="status-cell" style={{ background: bg }}>{label}</span>;
}

function MiniProgress({ percent, color }) {
  return (
    <div className="mini-progress" title={`${percent}%`}>
      <div style={{ width: `${percent}%`, background: color }} />
      <span>{percent}%</span>
    </div>
  );
}

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
  const [collapsed, setCollapsed] = useState({});

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
        <div className="member-chips">
          {team.members.map((m) => (
            <span className="member-chip" key={m.id} title={m.studentCode || ''}>
              <i>{m.fullName.split(' ').slice(-1)[0].slice(0, 1).toUpperCase()}</i>{m.fullName}{m.id === team.leader.id && <em>nhóm trưởng</em>}
            </span>
          ))}
        </div>
        <p className="muted small">Giảng viên quản lý milestone chính · Nhóm trưởng quản lý milestone phụ và giao task · Thành viên thực hiện task.</p>
      </div>

      <ContributionSummary teamId={teamId} reloadKey={milestones.length} />

      <h3>Milestone chính</h3>
      {mains.map((m, idx) => {
        const subs = subsOf(m.id);
        const f = subForms[m.id] || { title: '', description: '', dueDate: '' };
        const color = GROUP_COLORS[idx % GROUP_COLORS.length];
        const isClosed = collapsed[m.id];
        return (
          <div className="mgroup" key={m.id} style={{ '--gc': color }}>
            <div className="mgroup-head">
              <button className="chev" aria-label="Thu gọn / mở rộng" onClick={() => setCollapsed({ ...collapsed, [m.id]: !isClosed })}>
                {isClosed ? '▸' : '▾'}
              </button>
              <Link to={`/milestones/${m.id}`} className="mgroup-title">{m.title}</Link>
              <span className="muted small">
                {subs.length} milestone phụ · {m.doneTasks}/{m.totalTasks} task · {m.submissions} bài nộp
                {m.pendingReview > 0 && <> · <b>{m.pendingReview} task chờ xác nhận</b></>}
              </span>
              <span className="grow" />
              {canMain && (
                <span className="row">
                  <button onClick={() => archive(m)}>Lưu trữ</button>
                  {isEmpty(m) && <button className="danger" onClick={() => remove(m)}>Xóa</button>}
                </span>
              )}
            </div>

            {!isClosed && (
              <>
                <table className="mtable">
                  <thead><tr><th>Mục</th><th>Trạng thái</th><th>Tiến độ</th><th>Hạn</th><th>Task</th><th /></tr></thead>
                  <tbody>
                    <tr className="main-row">
                      <td><Link to={`/milestones/${m.id}`}><b>{m.title}</b></Link></td>
                      <td><StatusCell m={m} /></td>
                      <td><MiniProgress percent={m.percent} color={color} /></td>
                      <td>{fmtDate(m.dueDate)}</td>
                      <td>{m.doneTasks}/{m.totalTasks}</td>
                      <td />
                    </tr>
                    {subs.map((sm) => (
                      <tr key={sm.id} className="sub-row">
                        <td className="indent"><Link to={`/milestones/${sm.id}`}>{sm.title}</Link></td>
                        <td><StatusCell m={sm} /></td>
                        <td><MiniProgress percent={sm.percent} color={color} /></td>
                        <td>{fmtDate(sm.dueDate)}</td>
                        <td>{sm.doneTasks}/{sm.totalTasks}{sm.pendingReview > 0 && <> <Badge kind="warn">{sm.pendingReview} chờ xác nhận</Badge></>}</td>
                        <td className="actions">
                          {canSub && <button onClick={() => archive(sm)}>Lưu trữ</button>}{' '}
                          {canSub && sm.totalTasks === 0 && <button className="danger" onClick={() => remove(sm)}>Xóa</button>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {subs.length === 0 && <p className="muted small pad">Chưa có milestone phụ.</p>}
                {archivedSubsOf(m.id).length > 0 && (
                  <details className="subform pad">
                    <summary>Milestone phụ đã lưu trữ ({archivedSubsOf(m.id).length})</summary>
                    <ul>
                      {archivedSubsOf(m.id).map((sm) => (
                        <li key={sm.id}>
                          <Link to={`/milestones/${sm.id}`}>{sm.title}</Link>{' '}
                          {canSub && <button className="link small" onClick={() => restore(sm)}>Khôi phục</button>}
                        </li>
                      ))}
                    </ul>
                  </details>
                )}
                {canSub && (
                  <details className="subform pad">
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
              </>
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

/** Tom tat dong gop (chi tiet nam o trang Dong gop ca nhan de tranh trung lap thong ke). */
function ContributionSummary({ teamId, reloadKey }) {
  const [report, setReport] = useState(null);

  useEffect(() => {
    api.get(`/teams/${teamId}/contributions`).then((r) => setReport(r.data)).catch(() => {});
  }, [teamId, reloadKey]);

  if (!report) return null;
  const top = [...report.members].sort((a, b) => b.score - a.score);
  return (
    <div className="card">
      <div className="card-head">
        <h3>Đóng góp cá nhân</h3>
        <div className="actions">
          <Link className="btn" to={`/teams/${teamId}/calendar`}>Lịch</Link>
          <Link className="btn" to={`/teams/${teamId}/activity`}>Nhật ký</Link>
          <Link className="btn" to={`/teams/${teamId}/github`}>GitHub</Link>
          <Link className="btn" to={`/teams/${teamId}/evaluation`}>Điểm tổng hợp &amp; đánh giá chéo</Link>
          <Link className="btn" to={`/teams/${teamId}/contributions`}>Xem chi tiết →</Link>
        </div>
      </div>
      {top.map((r) => (
        <div className="contrib-row" key={r.member.id}>
          <Link to={`/teams/${teamId}/members/${r.member.id}`}>{r.member.fullName}</Link>
          <div className="contrib-bar" title={`${r.sharePercent}%`}><div style={{ width: `${r.sharePercent}%` }} /></div>
          <span className="small muted">{r.score} điểm · {r.sharePercent}%</span>
        </div>
      ))}
      {top.length === 0 && <p className="muted small">Chưa có dữ liệu.</p>}
    </div>
  );
}
