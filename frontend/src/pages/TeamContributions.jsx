import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { downloadFile, errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { MemberTable } from '../contribution.jsx';
import { Alert } from '../ui.jsx';

/** Dong gop ca nhan cua mot nhom: GV/admin/nhom truong xem moi thanh vien, thanh vien chi xem cua minh. */
export default function TeamContributions() {
  const { teamId } = useParams();
  const { user } = useAuth();
  const [team, setTeam] = useState(null);
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([api.get(`/teams/${teamId}`), api.get(`/teams/${teamId}/contributions`)])
      .then(([t, r]) => { setTeam(t.data); setReport(r.data); })
      .catch((e) => setError(errMsg(e)));
  }, [teamId]);

  if (!team || !report) return <Alert error={error} />;
  const canExport = user.role === 'LECTURER' || user.role === 'ADMIN' || team.leader.id === user.id;
  const exportXlsx = () => downloadFile(`/teams/${teamId}/contributions/export`, `dong-gop-${team.name}.xlsx`).catch((e) => setError(errMsg(e)));

  return (
    <div>
      <p className="no-print"><Link to={`/teams/${teamId}`}>← {team.name}</Link> · <Link to={`/teams/${teamId}/evaluation`}>Điểm tổng hợp &amp; đánh giá chéo →</Link></p>
      <div className="print-only report-title">
        <h1>Báo cáo đóng góp nhóm {team.name}</h1>
        <div>Đề tài: {team.registration?.topic?.title} · Ngày xuất: {new Date().toLocaleDateString('vi-VN')}</div>
      </div>
      <div className="row between">
        <h2>Đóng góp cá nhân — {team.name}</h2>
        {canExport && (
          <div className="actions no-print">
            <button onClick={exportXlsx}>Xuất Excel</button>
            <button onClick={() => window.print()}>In / Lưu PDF</button>
          </div>
        )}
      </div>
      <Alert error={error} />
      <div className="card flush">
        <div className="card-head pad">
          <h3>Thành viên</h3>
          <span className="muted small">Tổng điểm nhóm: {report.totalScore}</span>
        </div>
        <MemberTable teamId={teamId} members={report.members} />
        {report.members.length === 0 && <p className="muted pad">Chưa có dữ liệu.</p>}
        {user.role === 'STUDENT' && team.leader.id !== user.id && (
          <p className="muted small pad">Bạn chỉ xem được kết quả đóng góp của chính mình.</p>
        )}
        <div className="formula small muted pad">{report.formula}</div>
      </div>
    </div>
  );
}
