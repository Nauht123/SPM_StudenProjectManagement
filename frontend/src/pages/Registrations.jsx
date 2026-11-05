import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { Alert, Badge, REG_KIND, REG_LABEL, fmtTime } from '../ui.jsx';

export default function Registrations() {
  const [list, setList] = useState([]);
  const [error, setError] = useState('');

  const load = () => api.get('/registrations/lecturer').then((r) => setList(r.data)).catch((e) => setError(errMsg(e)));
  useEffect(() => { load(); }, []);

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
    </div>
  );
}
