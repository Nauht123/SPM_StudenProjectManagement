import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { Alert, Badge, Progress } from '../ui.jsx';

export default function Dashboard() {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/dashboard/teams').then((r) => setRows(r.data)).catch((e) => setError(errMsg(e)));
  }, []);

  const atRisk = rows.filter((r) => r.atRisk).length;

  return (
    <div>
      <h2>Tiến độ các nhóm</h2>
      <Alert error={error} />
      <div className="stats">
        <div className="card stat"><b>{rows.length}</b><span>Nhóm đang hướng dẫn</span></div>
        <div className="card stat"><b className={atRisk ? 'bad-text' : ''}>{atRisk}</b><span>Nhóm có nguy cơ chậm</span></div>
        <div className="card stat"><b>{rows.reduce((s, r) => s + r.lateSubmissions, 0)}</b><span>Bài nộp trễ hạn</span></div>
      </div>
      <table className="table">
        <thead><tr><th>Nhóm</th><th>Đề tài</th><th>Tiến độ task</th><th>Milestone quá hạn chưa nộp</th><th>Nộp trễ</th><th>Tình trạng</th></tr></thead>
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
      {rows.length === 0 && <p className="muted">Chưa có nhóm nào được duyệt đề tài.</p>}
    </div>
  );
}
