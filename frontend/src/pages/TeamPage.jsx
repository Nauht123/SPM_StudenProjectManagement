import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, Progress, REG_KIND, REG_LABEL } from '../ui.jsx';

export default function TeamPage() {
  const { user } = useAuth();
  const [team, setTeam] = useState(undefined); // undefined = dang tai, null = chua co nhom
  const [regs, setRegs] = useState([]);
  const [progress, setProgress] = useState(null);
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');

  const load = async () => {
    try {
      const res = await api.get('/teams/me');
      const t = res.status === 204 || !res.data ? null : res.data;
      setTeam(t);
      if (t) {
        api.get('/registrations/mine').then((r) => setRegs(r.data));
        if (t.registration?.status === 'APPROVED') api.get(`/teams/${t.id}/progress`).then((r) => setProgress(r.data));
      }
    } catch (e) {
      setError(errMsg(e));
    }
  };
  useEffect(() => { load(); }, []);

  const act = async (fn) => {
    setError('');
    try { await fn(); await load(); } catch (e) { setError(errMsg(e)); }
  };

  if (team === undefined) return <p className="muted">Đang tải…</p>;

  if (team === null) {
    return (
      <div>
        <h2>Nhóm của tôi</h2>
        <Alert error={error} />
        <form className="card form" onSubmit={(e) => { e.preventDefault(); act(async () => { await api.post('/teams', { name }); setName(''); }); }}>
          <h3>Bạn chưa có nhóm</h3>
          <p className="muted">Tạo nhóm mới (bạn sẽ là nhóm trưởng), sau đó thêm thành viên bằng MSSV.</p>
          <label>Tên nhóm<input value={name} onChange={(e) => setName(e.target.value)} required /></label>
          <button className="primary" type="submit">Tạo nhóm</button>
        </form>
      </div>
    );
  }

  const isLeader = team.leader.id === user.id;
  const locked = team.registration?.status === 'APPROVED';
  const reg = team.registration;

  return (
    <div>
      <h2>Nhóm: {team.name}</h2>
      <Alert error={error} />

      <div className="card">
        <div className="row between">
          <h3>Đề tài</h3>
          {reg && <Badge kind={REG_KIND[reg.status]}>{REG_LABEL[reg.status]}</Badge>}
        </div>
        {reg ? <p><b>{reg.topic.title}</b> · GVHD {reg.topic.lecturer?.fullName}</p> : <p className="muted">Chưa đăng ký đề tài. <Link to="/topics">Xem danh sách đề tài</Link></p>}
        {reg?.note && <p className="muted">Ghi chú của giảng viên: {reg.note}</p>}
        {locked && progress && <Progress percent={progress.percent} />}
        {locked && <p><Link className="btn primary" to={`/teams/${team.id}`}>Mở không gian làm việc (milestone, task, nộp bài)</Link></p>}
        {regs.filter((r) => r.status === 'PENDING').map((r) => (
          isLeader && <button key={r.id} onClick={() => act(() => api.delete(`/registrations/${r.id}`))}>Hủy đăng ký đang chờ</button>
        ))}
      </div>

      <div className="card">
        <h3>Thành viên ({team.members.length}/5)</h3>
        <table className="table">
          <thead><tr><th>Họ tên</th><th>MSSV</th><th>Vai trò</th><th /></tr></thead>
          <tbody>
            {team.members.map((m) => (
              <tr key={m.id}>
                <td>{m.fullName}</td>
                <td>{m.studentCode}</td>
                <td>{m.id === team.leader.id ? 'Nhóm trưởng' : 'Thành viên'}</td>
                <td className="actions">
                  {isLeader && !locked && m.id !== team.leader.id && (
                    <button className="danger" onClick={() => act(() => api.delete(`/teams/${team.id}/members/${m.id}`))}>Xóa</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {isLeader && !locked && (
          <form className="row" onSubmit={(e) => { e.preventDefault(); act(async () => { await api.post(`/teams/${team.id}/members`, { studentCode: code }); setCode(''); }); }}>
            <input placeholder="Nhập MSSV để thêm thành viên" value={code} onChange={(e) => setCode(e.target.value)} required />
            <button className="primary" type="submit">Thêm</button>
          </form>
        )}
        {locked && <p className="muted small">Nhóm đã được duyệt đề tài nên danh sách thành viên được khóa.</p>}
      </div>
    </div>
  );
}
