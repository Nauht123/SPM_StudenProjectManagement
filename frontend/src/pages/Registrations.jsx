import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { Alert, Badge, REG_KIND, REG_LABEL, fmtTime } from '../ui.jsx';
import { CHANGE_STATUS, describeChange } from '../TeamChangePanel.jsx';

export default function Registrations() {
  const [list, setList] = useState([]);
  const [changes, setChanges] = useState([]);
  const [error, setError] = useState('');

  const load = () => api.get('/registrations/lecturer').then((r) => setList(r.data)).catch((e) => setError(errMsg(e)));
  const loadChanges = () => api.get('/change-requests/lecturer').then((r) => setChanges(r.data)).catch(() => {});
  useEffect(() => { load(); loadChanges(); }, []);

  const decide = async (r, approve) => {
    const note = window.prompt(approve ? 'Ghi chú khi duyệt (có thể để trống):' : 'Lý do từ chối:') ;
    if (note === null) return;
    setError('');
    try {
      await api.post(`/registrations/${r.id}/decision`, { approve, note });
      load();
    } catch (e) {
      setError(errMsg(e));
    }
  };

  const decideChange = async (r, approve) => {
    const note = window.prompt(approve ? 'Ghi chú khi duyệt (có thể để trống):' : 'Lý do từ chối (bắt buộc):');
    if (note === null) return;
    if (!approve && !note.trim()) { setError('Vui lòng ghi lý do từ chối'); return; }
    setError('');
    try {
      await api.post(`/change-requests/${r.id}/decision`, { approve, note });
      loadChanges();
    } catch (e) {
      setError(errMsg(e));
    }
  };

  const pendingChanges = changes.filter((c) => c.status === 'PENDING').length;

  return (
    <div>
      <Alert error={error} />
      <table className="table">
        <thead><tr><th>Nhóm</th><th>Đề tài</th><th>Thời gian</th><th>Trạng thái</th><th /></tr></thead>
        <tbody>
          {list.map((r) => (
            <tr key={r.id}>
              <td>{r.status === 'APPROVED' ? <Link to={`/teams/${r.teamId}`}>{r.teamName}</Link> : r.teamName}</td>
              <td>{r.topic.title}<div className="muted small">{r.topic.approvedGroups}/{r.topic.maxGroups} nhóm đã duyệt</div></td>
              <td>{fmtTime(r.createdAt)}</td>
              <td><Badge kind={REG_KIND[r.status]}>{REG_LABEL[r.status]}</Badge>{r.note && <div className="muted small">{r.note}</div>}</td>
              <td className="actions">
                {r.status === 'PENDING' && (
                  <>
                    <button className="primary" onClick={() => decide(r, true)}>Duyệt</button>
                    <button className="danger" onClick={() => decide(r, false)}>Từ chối</button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {list.length === 0 && <p className="muted">Chưa có nhóm nào đăng ký.</p>}

      <h3>Yêu cầu thay đổi nhóm {pendingChanges > 0 && <Badge kind="warn">{pendingChanges} chờ duyệt</Badge>}</h3>
      <p className="muted small">Sau khi đề tài được duyệt, nhóm không tự thêm/xóa/thay thành viên hay đổi nhóm trưởng được; mọi yêu cầu cần giảng viên hướng dẫn quyết định. Khi duyệt xóa thành viên, các task chưa xong của người đó được bỏ giao để nhóm trưởng giao lại; task đã hoàn thành vẫn giữ đóng góp.</p>
      <table className="table">
        <thead><tr><th>Nhóm</th><th>Yêu cầu</th><th>Người gửi</th><th>Lý do</th><th>Trạng thái</th><th /></tr></thead>
        <tbody>
          {changes.map((c) => {
            const st = CHANGE_STATUS[c.status];
            return (
              <tr key={c.id}>
                <td><Link to={`/teams/${c.teamId}`}>{c.teamName}</Link></td>
                <td><b>{describeChange(c)}</b><div className="muted small">{fmtTime(c.createdAt)}</div></td>
                <td>{c.requestedBy.fullName}</td>
                <td>{c.reason}</td>
                <td><Badge kind={st[1]}>{st[0]}</Badge>{c.decisionNote && <div className="muted small">{c.decisionNote}</div>}</td>
                <td className="actions">
                  {c.status === 'PENDING' && (
                    <>
                      <button className="primary" onClick={() => decideChange(c, true)}>Duyệt</button>
                      <button className="danger" onClick={() => decideChange(c, false)}>Từ chối</button>
                    </>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {changes.length === 0 && <p className="muted">Chưa có yêu cầu thay đổi nhóm nào.</p>}
    </div>
  );
}
