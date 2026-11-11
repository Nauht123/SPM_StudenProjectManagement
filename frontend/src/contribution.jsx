import { Link } from 'react-router-dom';
import { Badge, Progress } from './ui.jsx';

export const TASK_STATUS = { SUGGESTED: 'Đề xuất', TODO: 'Cần làm', DOING: 'Đang làm', REVIEW: 'Chờ xác nhận', DONE: 'Hoàn thành', CANCELLED: 'Đã hủy' };
export const OUTCOME = { PENDING: ['Chờ duyệt', 'warn'], APPROVED: ['Đã duyệt', 'good'], REVISION_REQUESTED: ['Yêu cầu sửa', 'bad'] };
export const SUB_ROLE = { PERFORMER: 'Người thực hiện', UPLOADER: 'Người upload', CONTRIBUTOR: 'Người đóng góp', UPLOADER_CONTRIBUTOR: 'Upload và đóng góp' };

/** Ty le hoan thanh dung han; null neu chua hoan thanh task nao. */
export const onTimeRate = (done, onTime) => (done > 0 ? Math.round((onTime * 100) / done) : null);
export const rateText = (rate) => (rate === null ? '—' : `${rate}%`);

export function ShareBar({ percent }) {
  return (
    <div style={{ minWidth: 110 }}>
      <div className="contrib-bar" title={`${percent}%`}><div style={{ width: `${percent}%` }} /></div>
      <span className="small muted">{percent}%</span>
    </div>
  );
}

/** Thanh chia mau: dung han / tre han / qua han chua xong / chua xong khac. */
export function StackBar({ segments }) {
  const total = segments.reduce((s, x) => s + x.value, 0);
  return (
    <div>
      <div className="stackbar">
        {total === 0 && <div className="seg empty" style={{ width: '100%' }} />}
        {segments.filter((x) => x.value > 0).map((x) => (
          <div key={x.label} className="seg" style={{ width: `${(x.value * 100) / total}%`, background: x.color }} title={`${x.label}: ${x.value}`} />
        ))}
      </div>
      <div className="legend">
        {segments.map((x) => (
          <span key={x.label}><i style={{ background: x.color }} />{x.label}: <b>{x.value}</b></span>
        ))}
      </div>
    </div>
  );
}

/** Bang so sanh dong gop cac thanh vien cua mot nhom (moi dong dan toi trang chi tiet ca nhan). */
export function MemberTable({ teamId, members }) {
  return (
    <table className="table plain contrib">
      <thead>
        <tr>
          <th>Thành viên</th><th>Tỷ lệ đóng góp</th><th>Điểm</th><th>Task xong / được giao</th><th>Đúng hạn</th>
          <th>Trễ hạn</th><th>Chưa xong</th><th>Bị yêu cầu sửa</th><th>Tiến độ cá nhân</th><th />
        </tr>
      </thead>
      <tbody>
        {members.map((r) => (
          <tr key={r.member.id}>
            <td>
              <Link to={`/teams/${teamId}/members/${r.member.id}`}><b>{r.member.fullName}</b></Link>
              {r.leader && <> <Badge kind="warn">Nhóm trưởng</Badge></>}
              <div className="small muted">{r.member.studentCode}</div>
            </td>
            <td><ShareBar percent={r.sharePercent} /></td>
            <td><b>{r.score}</b></td>
            <td>{r.doneTasks}/{r.assignedTasks}</td>
            <td>{r.onTimeTasks}{r.doneTasks > 0 && <span className="small muted"> ({rateText(onTimeRate(r.doneTasks, r.onTimeTasks))})</span>}</td>
            <td>{r.lateTasks > 0 ? <Badge kind="bad">{r.lateTasks}</Badge> : 0}</td>
            <td>{r.unfinishedTasks}{r.overdueOpenTasks > 0 && <> <Badge kind="bad">{r.overdueOpenTasks} quá hạn</Badge></>}</td>
            <td>{r.revisionCount >= 3 ? <Badge kind="warn">{r.revisionCount}</Badge> : r.revisionCount}</td>
            <td style={{ minWidth: 120 }}><Progress percent={r.progressPercent} /><span className="small muted">{r.weightDone}/{r.weightAssigned} điểm TS</span></td>
            <td className="no-print"><Link className="small" to={`/teams/${teamId}/members/${r.member.id}`}>Chi tiết →</Link></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
