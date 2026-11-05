import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, AreaChart, Badge, HBar, Icon, MiniBars, Progress, Sparkline, StatCard, TargetCard } from '../ui.jsx';

const pct = (a, b) => (b === 0 ? 0 : Math.round((a * 100) / b));
const BAR_COLORS = ['#d92550', '#3ac47d', '#3f6ad8', '#f7b924'];

export default function Dashboard() {
  const { user } = useAuth();
  const [rows, setRows] = useState([]);
  const [topics, setTopics] = useState([]);
  const [pending, setPending] = useState(0);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/dashboard/teams').then((r) => setRows(r.data)).catch((e) => setError(errMsg(e)));
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
          <Sparkline values={rows.map((r) => r.doneTasks)} color="#16a34a" />
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
          <Sparkline values={rows.map((r) => r.percent)} color="#7c5cdb" />
        </div>
      </div>

      <div className="targets">
        <TargetCard percent={pct(done, total)} label="Hoàn thành task" color="#d92550" />
        <TargetCard percent={pct(rows.length - atRisk, rows.length)} label="Nhóm đúng tiến độ" color="#3ac47d" />
        <TargetCard percent={pct(rows.filter((r) => r.lateSubmissions === 0).length, rows.length)} label="Nhóm không nộp trễ" color="#f7b924" />
        <TargetCard percent={pct(slotsUsed, slotsMax)} label="Suất đề tài đã có nhóm" color="#3f6ad8" />
      </div>

      <div className="card flush">
        <div className="card-head pad"><h3>Danh sách nhóm</h3></div>
        <table className="table plain">
          <thead><tr><th>Nhóm</th><th>Đề tài</th><th>Tiến độ (theo trọng số)</th><th>Quá hạn chưa nộp</th><th>Nộp trễ</th><th>Tình trạng</th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.teamId}>
                <td><Link to={`/teams/${r.teamId}`}>{r.teamName}</Link></td>
                <td>{r.topicTitle || <span className="muted">Chưa có</span>}</td>
                <td style={{ minWidth: 160 }}><Progress percent={r.percent} /><span className="muted small">{r.doneTasks}/{r.totalTasks} task</span></td>
                <td>{r.overdueMilestones}</td>
                <td>{r.lateSubmissions}</td>
                <td><Badge kind={r.atRisk ? 'bad' : 'good'}>{r.atRisk ? 'Có nguy cơ chậm' : 'Đúng tiến độ'}</Badge></td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && <p className="muted pad">Chưa có nhóm nào được duyệt đề tài.</p>}
      </div>
    </div>
  );
}
