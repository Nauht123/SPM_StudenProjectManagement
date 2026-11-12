import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { Alert, Badge, fmtTime } from '../ui.jsx';

const TYPE_LABEL = { TASK: 'Task', COMMENT: 'Bình luận', MILESTONE: 'Milestone', MEMBER: 'Thành viên', GITHUB: 'GitHub' };
const TYPE_KIND = { TASK: 'good', COMMENT: 'warn', MILESTONE: 'good', MEMBER: 'bad', GITHUB: 'warn' };
const MEMBER_LABEL = { TEAM_CREATED: 'Tạo nhóm', ADDED: 'Được thêm vào nhóm', REMOVED: 'Bị xóa khỏi nhóm' };

/** Nhat ky hoat dong cua nhom va lich su thay doi thanh vien. */
export default function TeamActivity() {
  const { teamId } = useParams();
  const [team, setTeam] = useState(null);
  const [feed, setFeed] = useState([]);
  const [members, setMembers] = useState([]);
  const [filter, setFilter] = useState('ALL');
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([api.get(`/teams/${teamId}`), api.get(`/teams/${teamId}/activity`), api.get(`/teams/${teamId}/member-history`)])
      .then(([t, a, m]) => { setTeam(t.data); setFeed(a.data); setMembers(m.data); })
      .catch((e) => setError(errMsg(e)));
  }, [teamId]);

  if (!team) return <Alert error={error} />;
  const shown = filter === 'ALL' ? feed : feed.filter((x) => x.type === filter);

  return (
    <div>
      <p><Link to={`/teams/${teamId}`}>← {team.name}</Link></p>
      <h2>Nhật ký hoạt động — {team.name}</h2>
      <Alert error={error} />
      <div className="card flush">
        <div className="card-head pad">
          <h3>Hoạt động gần đây</h3>
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="ALL">Tất cả</option>
            {Object.entries(TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <table className="table plain">
          <tbody>
            {shown.map((a, i) => (
              <tr key={i}>
                <td className="small muted" style={{ whiteSpace: 'nowrap' }}>{fmtTime(a.at)}</td>
                <td><Badge kind={TYPE_KIND[a.type]}>{TYPE_LABEL[a.type]}</Badge></td>
                <td>{a.milestoneId ? <Link to={`/milestones/${a.milestoneId}`}>{a.message}</Link> : a.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {shown.length === 0 && <p className="muted pad">Chưa có hoạt động nào.</p>}
      </div>

      <div className="card flush">
        <div className="card-head pad"><h3>Lịch sử thay đổi thành viên</h3></div>
        <table className="table plain">
          <thead><tr><th>Thời gian</th><th>Thành viên</th><th>Thay đổi</th><th>Thực hiện bởi</th></tr></thead>
          <tbody>
            {members.map((h) => (
              <tr key={h.id}>
                <td className="small muted">{fmtTime(h.createdAt)}</td>
                <td><b>{h.member.fullName}</b></td>
                <td>{MEMBER_LABEL[h.action]}</td>
                <td>{h.actor ? h.actor.fullName : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {members.length === 0 && <p className="muted pad">Chưa ghi nhận thay đổi nào (chỉ ghi từ khi có tính năng này).</p>}
      </div>
    </div>
  );
}
