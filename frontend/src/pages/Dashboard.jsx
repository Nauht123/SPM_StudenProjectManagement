import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, AreaChart, Badge, HBar, Icon, MiniBars, Progress, Sparkline, StatCard, TargetCard } from '../ui.jsx';
import { rateText } from '../contribution.jsx';

const pct = (a, b) => (b === 0 ? 0 : Math.round((a * 100) / b));
const BAR_COLORS = ['#1d5aa6', '#12a46b', '#6f9bd1', '#f5a524'];

export default function Dashboard() {
  const { user } = useAuth();
  const [rows, setRows] = useState([]);
  const [topics, setTopics] = useState([]);
  const [pending, setPending] = useState(0);
  const [contrib, setContrib] = useState([]); // tong hop dong gop / deadline theo nhom
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/dashboard/teams').then((r) => setRows(r.data)).catch((e) => setError(errMsg(e)));
    api.get('/dashboard/contributions').then((r) => setContrib(r.data.teams)).catch(() => {});
    api.get('/topics').then((r) => setTopics(user.role === 'LECTURER' ? r.data.filter((t) => t.lecturer?.id === user.id) : r.data)).catch(() => {});
    if (user.role === 'LECTURER') {
      api.get('/registrations/lecturer').then((r) => setPending(r.data.filter((x) => x.status === 'PENDING').length)).catch(() => {});
    }
  }, [user.id, user.role]);

  const total = rows.reduce((s, r) => s + r.totalTasks, 0);
  const done = rows.reduce((s, r) => s + r.doneTasks, 0);
  const overdue = rows.reduce((s, r) => s + r.overdueMilestones, 0);
  const late = rows.reduce((s, r) => s + r.lateSubmissions, 0);
  const atRisk = rows.filter((r) => r.atRisk).length;
  const avg = rows.length ? Math.round(rows.reduce((s, r) => s + r.percent, 0) / rows.length) : 0;
  const slotsMax = topics.reduce((s, t) => s + t.maxGroups, 0);
  const slotsUsed = topics.reduce((s, t) => s + t.approvedGroups, 0);
  const byTeam = Object.fromEntries(contrib.map((c) => [c.teamId, c]));
  const doneTasksAll = contrib.reduce((a, c) => a + c.doneTasks, 0);
  const onTimeAll = contrib.reduce((a, c) => a + c.onTimeTasks, 0);
  const flags = contrib.flatMap((c) => c.flags.map((f) => ({ teamId: c.teamId, teamName: c.teamName, text: f })));
  const needAttention = [...rows].sort((a, b) => a.percent - b.percent).slice(0, 4);

  return (
    <div>
      <Alert error={error} />

      <div className="dash-top">
        <div className="card">
          <div className="card-head">
            <h3>Báo cáo tiến độ</h3>
            <span className="muted small">Các nhóm cần chú ý nhất</span>
          </div>
          <div className="hbar-grid">
            {needAttention.map((r, i) => (
              <HBar key={r.teamId} percent={r.percent} label={r.teamName} color={BAR_COLORS[i % BAR_COLORS.length]} />
            ))}
            {needAttention.length === 0 && <p className="muted">Chưa có nhóm nào được duyệt đề tài.</p>}
          </div>
          <div className="chart-title">Tiến độ theo trọng số task của từng nhóm (%)</div>
          <AreaChart values={rows.map((r) => r.percent)} labels={rows.map((r) => r.teamName)} />
        </div>

        <div className="stat-grid">
          <StatCard tone="blue" icon="users" value={rows.length} label="Nhóm đang hướng dẫn" note={`${atRisk} nhóm có nguy cơ chậm`} />
          <StatCard tone="purple" icon="book" value={topics.length} label="Đề tài" note={user.role === 'LECTURER' ? `${pending} đăng ký chờ duyệt` : `${slotsUsed}/${slotsMax} suất đã có nhóm`} />
          <StatCard tone="green" icon="check" value={`${pct(done, total)}%`} label="Task hoàn thành" note={`${done}/${total} task`} />
          <StatCard tone="red" icon="alert" value={overdue} label="Milestone quá hạn chưa nộp" note={`${late} bài nộp trễ hạn`} />
        </div>
      </div>

      <div className="trio">
        <div className="card mini">
          <div className="mini-icon blue"><Icon name="check" size={20} /></div>
          <div className="mini-value">{done}</div>
          <div className="muted small">Task đã hoàn thành</div>
          <Sparkline values={rows.map((r) => r.doneTasks)} color="#12a46b" />
        </div>
        <div className="card mini">
          <div className="mini-icon red"><Icon name="file" size={20} /></div>
          <div className="mini-value">{late}</div>
          <div className="muted small">Bài nộp trễ hạn theo nhóm</div>
          <MiniBars values={rows.map((r) => r.lateSubmissions)} color="#6b8cf5" />
        </div>
        <div className="card mini wide">
          <div className="mini-icon pink"><Icon name="trend" size={20} /></div>
          <div className="mini-value">{avg}%</div>
          <div className="muted small">Tiến độ trung bình toàn bộ nhóm</div>
          <Sparkline values={rows.map((r) => r.percent)} color="#5b4fc9" />
        </div>
      </div>

      <div className="targets">
        <TargetCard percent={pct(done, total)} label="Hoàn thành task" color="#1d5aa6" />
        <TargetCard percent={pct(rows.length - atRisk, rows.length)} label="Nhóm đúng tiến độ" color="#12a46b" />
        <TargetCard percent={pct(onTimeAll, doneTasksAll)} label="Task hoàn thành đúng hạn" color="#f5a524" />
        <TargetCard percent={pct(slotsUsed, slotsMax)} label="Suất đề tài đã có nhóm" color="#6f9bd1" />
      </div>

      {flags.length > 0 && (
        <div className="card">
          <div className="card-head">
            <h3>Cần chú ý</h3>
            <Link className="btn" to="/contributions">Xem đóng góp cá nhân →</Link>
          </div>
          <ul className="attention">
            {flags.slice(0, 8).map((f, i) => (
              <li key={i}><Link to={`/teams/${f.teamId}/contributions`}>{f.teamName}</Link> — {f.text}</li>
            ))}
          </ul>
          {flags.length > 8 && <p className="muted small">và {flags.length - 8} cảnh báo khác…</p>}
        </div>
      )}

      <div className="card flush">
        <div className="card-head pad"><h3>Danh sách nhóm</h3></div>
        <table className="table plain">
          <thead><tr><th>Nhóm</th><th>Đề tài</th><th>Tiến độ (theo trọng số)</th><th>Task đúng hạn</th><th>Quá hạn chưa xong</th><th>Sửa lại</th><th>Milestone chính quá hạn</th><th>Tình trạng</th></tr></thead>
          <tbody>
            {rows.map((r) => {
              const c = byTeam[r.teamId];
              return (
                <tr key={r.teamId}>
                  <td><Link to={`/teams/${r.teamId}`}>{r.teamName}</Link><div><Link className="small" to={`/teams/${r.teamId}/contributions`}>Đóng góp →</Link></div></td>
                  <td>{r.topicTitle || <span className="muted">Chưa có</span>}</td>
                  <td style={{ minWidth: 160 }}><Progress percent={r.percent} /><span className="muted small">{r.doneTasks}/{r.totalTasks} task</span></td>
                  <td>{c ? <>{rateText(c.doneTasks === 0 ? null : c.onTimeRate)} <span className="muted small">({c.onTimeTasks}/{c.doneTasks})</span></> : '—'}{c && c.lateTasks > 0 && <> <Badge kind="bad">{c.lateTasks} trễ</Badge></>}</td>
                  <td>{c ? (c.overdueOpenTasks > 0 ? <Badge kind="bad">{c.overdueOpenTasks}</Badge> : 0) : '—'}</td>
                  <td>{c ? c.revisionCount : '—'}</td>
                  <td>{r.overdueMilestones}</td>
                  <td><Badge kind={r.atRisk || (c && c.overdueOpenTasks > 0) ? 'bad' : 'good'}>{r.atRisk || (c && c.overdueOpenTasks > 0) ? 'Có nguy cơ chậm' : 'Đúng tiến độ'}</Badge></td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {rows.length === 0 && <p className="muted pad">Chưa có nhóm nào được duyệt đề tài.</p>}
      </div>
    </div>
  );
}
