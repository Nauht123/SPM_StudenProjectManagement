import { useEffect, useState } from 'react';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge } from '../ui.jsx';

export default function Topics() {
  const { user } = useAuth();
  const [topics, setTopics] = useState([]);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [q, setQ] = useState('');

  const load = () => api.get('/topics').then((r) => setTopics(r.data)).catch((e) => setError(errMsg(e)));
  useEffect(() => { load(); }, []);

  const register = async (t) => {
    setError(''); setOk('');
    try {
      await api.post('/registrations', { topicId: t.id });
      setOk(`Đã gửi đăng ký đề tài "${t.title}". Vui lòng chờ giảng viên duyệt.`);
      load();
    } catch (e) {
      setError(errMsg(e));
    }
  };

  const shown = topics.filter((t) => (t.title + ' ' + (t.lecturer?.fullName || '')).toLowerCase().includes(q.toLowerCase()));

  return (
    <div>
      <h2>Danh sách đề tài</h2>
      <Alert error={error} ok={ok} />
      <input className="search" placeholder="Tìm theo tên đề tài hoặc giảng viên…" value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="grid">
        {shown.map((t) => {
          const full = t.approvedGroups >= t.maxGroups;
          return (
            <div className="card" key={t.id}>
              <div className="row between">
                <h3>{t.title}</h3>
                <Badge kind={t.status === 'OPEN' && !full ? 'good' : 'bad'}>
                  {t.status !== 'OPEN' ? 'Đã đóng' : full ? 'Đủ nhóm' : 'Đang mở'}
                </Badge>
              </div>
              <p className="muted">{t.description}</p>
              <p>GVHD: <b>{t.lecturer?.fullName}</b> · {t.semester}</p>
              <p>Số nhóm: {t.approvedGroups}/{t.maxGroups}</p>
              {user.role === 'STUDENT' && (
                <button className="primary" disabled={t.status !== 'OPEN' || full} onClick={() => register(t)}>
                  Đăng ký đề tài
                </button>
              )}
            </div>
          );
        })}
        {shown.length === 0 && <p className="muted">Chưa có đề tài phù hợp.</p>}
      </div>
    </div>
  );
}
