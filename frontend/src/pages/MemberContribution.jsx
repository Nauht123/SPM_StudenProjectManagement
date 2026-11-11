import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { downloadFile, errMsg } from '../api.js';
import { OUTCOME, SUB_ROLE, StackBar, TASK_STATUS, onTimeRate, rateText } from '../contribution.jsx';
import { Alert, Badge, Progress, StatCard, fmtDate, fmtTime } from '../ui.jsx';

/** Chi tiet dong gop ca nhan: diem, hieu suat deadline, task, theo doi sua lai, lich su nop; xuat Excel / in PDF. */
export default function MemberContribution() {
  const { teamId, userId } = useParams();
  const [d, setD] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setD(null);
    api.get(`/teams/${teamId}/members/${userId}/contribution`).then((r) => setD(r.data)).catch((e) => setError(errMsg(e)));
  }, [teamId, userId]);

  if (!d) return <Alert error={error} />;
  const s = d.summary;
  const rate = onTimeRate(s.doneTasks, s.onTimeTasks);
  const otherOpen = Math.max(0, s.unfinishedTasks - s.overdueOpenTasks);
  const exportXlsx = () => downloadFile(`/teams/${teamId}/members/${userId}/report`, `bao-cao-${s.member.fullName}.xlsx`).catch((e) => setError(errMsg(e)));

  return (
    <div>
      <p className="no-print"><Link to={`/teams/${teamId}/contributions`}>← Đóng góp cá nhân — {d.teamName}</Link></p>
      <div className="print-only report-title">
        <h1>Báo cáo đóng góp cá nhân</h1>
        <div>Nhóm: {d.teamName} · Đề tài: {d.topicTitle} · Ngày xuất: {new Date().toLocaleDateString('vi-VN')}</div>
      </div>
      <div className="row between">
        <h2>{s.member.fullName} {s.leader && <Badge kind="warn">Nhóm trưởng</Badge>} <span className="muted small">{s.member.studentCode}</span></h2>
        {d.canExport && (
          <div className="actions no-print">
            <button onClick={exportXlsx}>Xuất Excel</button>
            <button onClick={() => window.print()}>In / Lưu PDF</button>
          </div>
        )}
      </div>
      <Alert error={error} />

      <div className="stat-grid four">
        <StatCard tone="blue" icon="target" value={s.score} label="Điểm đóng góp" note={`${s.sharePercent}% của nhóm`} />
        <StatCard tone="green" icon="check" value={`${s.doneTasks}/${s.assignedTasks}`} label="Task hoàn thành" note={`${s.workloadHoursDone} giờ công`} />
        <StatCard tone="purple" icon="clock" value={rateText(rate)} label="Hoàn thành đúng hạn" note={`${s.lateTasks} task trễ hạn`} />
        <StatCard tone="red" icon="alert" value={s.revisionCount} label="Lần bị yêu cầu sửa" />
      </div>

      <div className="card">
        <h3>Tiến độ và hiệu suất deadline</h3>
        <p className="muted small">Tiến độ cá nhân theo trọng số task: {s.weightDone}/{s.weightAssigned} điểm</p>
        <Progress percent={s.progressPercent} />
        <div style={{ height: 14 }} />
        <StackBar segments={[
          { label: 'Đúng hạn', value: Number(s.onTimeTasks), color: '#3ac47d' },
          { label: 'Trễ hạn', value: Number(s.lateTasks), color: '#d92550' },
          { label: 'Quá hạn chưa xong', value: Number(s.overdueOpenTasks), color: '#f7b924' },
          { label: 'Đang thực hiện', value: otherOpen, color: '#b8c0d0' },
        ]} />
      </div>

      <div className="card flush">
        <div className="card-head pad"><h3>Task được giao</h3></div>
        <table className="table plain contrib">
          <thead><tr><th>Task</th><th>Milestone phụ</th><th>Trạng thái</th><th>Trọng số</th><th>Độ khó</th><th>Giờ</th><th>Hạn</th><th>Đúng hạn</th><th>Sửa lại</th><th>Chất lượng</th><th>Điểm</th></tr></thead>
          <tbody>
            {s.tasks.map((t) => (
              <tr key={t.taskId}>
                <td>{t.title}</td><td>{t.milestoneTitle}</td><td>{TASK_STATUS[t.status] || t.status}</td>
                <td>{t.weight}</td><td>{t.difficulty}</td><td>{t.workloadHours}</td><td>{fmtDate(t.dueDate)}</td>
                <td>{t.onTime === null ? '—' : t.onTime ? <Badge kind="good">Đúng hạn</Badge> : <Badge kind="bad">Trễ</Badge>}</td>
                <td>{t.revisions}</td><td>{t.qualityRating ? `${t.qualityRating}/5` : '—'}</td><td>{t.status === 'DONE' ? t.points : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {s.tasks.length === 0 && <p className="muted pad">Chưa được giao task nào.</p>}
      </div>

      <div className="card flush">
        <div className="card-head pad"><h3>Theo dõi sửa lại</h3><span className="muted small">{d.revisions.length} lần bị yêu cầu sửa</span></div>
        <table className="table plain contrib">
          <thead><tr><th>Task</th><th>Milestone phụ</th><th>Lần nộp</th><th>Nộp lúc</th><th>Yêu cầu sửa lúc</th><th>Người duyệt</th><th>Nội dung yêu cầu sửa</th></tr></thead>
          <tbody>
            {d.revisions.map((r, i) => (
              <tr key={`${r.taskId}-${r.attemptNo}-${i}`}>
                <td>{r.taskTitle}</td><td>{r.milestoneTitle}</td><td>{r.attemptNo}</td>
                <td>{fmtTime(r.submittedAt)}</td><td>{fmtTime(r.reviewedAt)}</td>
                <td>{r.reviewedBy?.fullName}</td><td>{r.reviewNote}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {d.revisions.length === 0 && <p className="muted pad">Chưa có lần nào bị yêu cầu sửa.</p>}
      </div>

      <div className="card flush">
        <div className="card-head pad"><h3>Lịch sử nộp cá nhân</h3><span className="muted small">Báo hoàn thành task và bài nộp milestone mà thành viên upload hoặc đóng góp</span></div>
        <table className="table plain contrib">
          <thead><tr><th>Thời điểm</th><th>Loại</th><th>Nội dung</th><th>Milestone</th><th>Lần / phiên bản</th><th>Vai trò</th><th>Kết quả duyệt</th><th>Hạn nộp</th><th>Ghi chú</th></tr></thead>
          <tbody>
            {d.submissionHistory.map((h) => {
              const o = h.outcome ? OUTCOME[h.outcome] : null;
              return (
                <tr key={`${h.kind}-${h.id}`}>
                  <td>{fmtTime(h.at)}</td>
                  <td>{h.kind === 'TASK' ? 'Task' : 'Bài nộp milestone'}</td>
                  <td>{h.title}{h.fileName && h.kind === 'TASK' && <div className="small muted">📎 {h.fileName}</div>}</td>
                  <td>{h.milestoneTitle}</td><td>{h.attemptNo}</td><td>{SUB_ROLE[h.role] || h.role}</td>
                  <td>{o ? <Badge kind={o[1]}>{o[0]}</Badge> : '—'}</td>
                  <td>{h.late ? <Badge kind="bad">Trễ hạn</Badge> : <Badge kind="good">Đúng hạn</Badge>}</td>
                  <td>{h.note}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {d.submissionHistory.length === 0 && <p className="muted pad">Chưa có lần nộp nào.</p>}
      </div>

      <div className="formula small muted">{d.formula}</div>
    </div>
  );
}
