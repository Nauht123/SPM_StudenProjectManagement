import { useEffect, useState } from 'react';
import api, { errMsg } from '../api.js';
import { fmtTime } from '../ui.jsx';

/** Binh luan trong task: thanh vien, giang vien huong dan va admin cung trao doi. */
export default function TaskComments({ taskId }) {
  const [list, setList] = useState(null);
  const [text, setText] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = () => api.get(`/tasks/${taskId}/comments`).then((r) => setList(r.data)).catch((e) => setError(errMsg(e)));
  useEffect(() => { load(); }, [taskId]);

  const send = async (e) => {
    e.preventDefault();
    if (!text.trim()) return;
    setBusy(true); setError('');
    try { await api.post(`/tasks/${taskId}/comments`, { content: text }); setText(''); await load(); } catch (err) { setError(errMsg(err)); }
    setBusy(false);
  };
  const remove = async (c) => {
    if (!window.confirm('Xóa bình luận này?')) return;
    try { await api.delete(`/comments/${c.id}`); await load(); } catch (err) { setError(errMsg(err)); }
  };

  return (
    <div className="comments small">
      <b>Bình luận{list ? ` (${list.length})` : ''}</b>
      {error && <div className="note warn">{error}</div>}
      <ul>
        {(list || []).map((c) => (
          <li key={c.id}>
            <b>{c.author.fullName}</b>{c.author.role === 'LECTURER' && <span className="muted"> (giảng viên)</span>}{' '}
            <span className="muted">{fmtTime(c.createdAt)}</span>
            {c.canDelete && <button type="button" className="link small" onClick={() => remove(c)}> Xóa</button>}
            <div style={{ whiteSpace: 'pre-wrap' }}>{c.content}</div>
          </li>
        ))}
      </ul>
      {list && list.length === 0 && <p className="muted">Chưa có bình luận.</p>}
      <form className="row-form" onSubmit={send}>
        <input maxLength={1000} placeholder="Viết bình luận…" value={text} onChange={(e) => setText(e.target.value)} />
        <button className="primary" disabled={busy || !text.trim()} type="submit">Gửi</button>
      </form>
    </div>
  );
}
