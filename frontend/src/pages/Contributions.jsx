import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { downloadFile, errMsg } from '../api.js';
import { MemberTable, rateText } from '../contribution.jsx';
import { Alert, Badge, StatCard } from '../ui.jsx';

/** Contribution Dashboard cho giang vien/admin: so sanh dong gop, deadline, sua lai theo tung nhom va canh bao. */
export default function Contributions() {
  const [teams, setTeams] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/dashboard/contributions').then((r) => setTeams(r.data.teams)).catch((e) => setError(errMsg(e)));
  }, []);

  if (!teams) return <Alert error={error} />;

  const members = teams.reduce((s, t) => s + t.members.length, 0);
  const done = teams.reduce((s, t) => s + t.doneTasks, 0);
  const onTime = teams.reduce((s, t) => s + t.onTimeTasks, 0);
  const revisions = teams.reduce((s, t) => s + t.revisionCount, 0);
  const flagged = teams.reduce((s, t) => s + t.flags.length, 0);
  const rate = done === 0 ? null : Math.round((onTime * 100) / done);

  const exportTeam = (t) => downloadFile(`/teams/${t.teamId}/contributions/export`, `dong-gop-${t.teamName}.xlsx`).catch((e) => setError(errMsg(e)));

  return (
    <div>
      <Alert error={error} />
      <div className="stat-grid four">
        <StatCard tone="blue" icon="users" value={teams.length} label="Nhóm" note={`${members} thành viên`} />
        <StatCard tone="green" icon="check" value={rateText(rate)} label="Task hoàn thành đúng hạn" note={`${onTime}/${done} task đã xong`} />
        <StatCard tone="purple" icon="clock" value={revisions} label="Lần bị yêu cầu sửa" note="Tổng của các nhóm" />
        <StatCard tone="red" icon="alert" value={flagged} label="Cảnh báo cần xem" note="Mất cân bằng, quá hạn, sửa nhiều" />
      </div>

      {teams.map((t) => (
        <div className="card flush" key={t.teamId}>
          <div className="card-head pad">
            <div>
              <h3><Link to={`/teams/${t.teamId}`}>{t.teamName}</Link></h3>
              <div className="muted small">{t.topicTitle || 'Chưa có đề tài'}</div>
            </div>
            <div className="actions">
              <span className="muted small team-kpi">
                Tổng điểm <b>{t.totalScore}</b> · Đúng hạn <b>{t.doneTasks === 0 ? '—' : `${t.onTimeRate}%`}</b> · Trễ <b>{t.lateTasks}</b> · Sửa lại <b>{t.revisionCount}</b>
              </span>
              <button onClick={() => exportTeam(t)}>Xuất Excel</button>
            </div>
          </div>
          {t.flags.length > 0 && (
            <div className="flags pad">
              {t.flags.map((f) => <Badge key={f} kind="warn">{f}</Badge>)}
            </div>
          )}
          <MemberTable teamId={t.teamId} members={t.members} />
        </div>
      ))}
      {teams.length === 0 && <p className="muted">Chưa có nhóm nào được duyệt đề tài.</p>}
      <p className="muted small">Điểm đóng góp tính theo trọng số, độ khó, chất lượng, đúng hạn và số lần sửa lại của từng task đã được nhóm trưởng xác nhận — không tính theo số lượng task hay người upload.</p>
    </div>
  );
}
