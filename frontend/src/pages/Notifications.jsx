import { useEffect, useState } from 'react';
import api, { errMsg } from '../api.js';
import { Alert, fmtTime } from '../ui.jsx';

export default function Notifications() {
  const [list, setList] = useState([]);
  const [error, setError] = useState('');

  const load = () => api.get('/notifications').then((r) => setList(r.data)).catch((e) => setError(errMsg(e)));
  useEffect(() => { load(); }, []);

  const read = async (n) => {
    if (n.seen) return;
    await api.post(`/notifications/${n.id}/read`).catch(() => {});
    load();
  };

  const readAll = async () => {
    await api.post('/notifications/read-all').catch(() => {});
    load();
  };

  return (
    <div>
      <div className="row between">
        <h2>Thông báo</h2>
        <button onClick={readAll}>Đánh dấu tất cả đã đọc</button>
      </div>
      <Alert error={error} />
      {list.map((n) => (
        <div key={n.id} className={`card notif ${n.seen ? '' : 'unseen'}`} onClick={() => read(n)}>
          <div>{n.message}</div>
          <div className="muted small">{fmtTime(n.createdAt)}</div>
        </div>
      ))}
      {list.length === 0 && <p className="muted">Không có thông báo.</p>}
    </div>
  );
}
