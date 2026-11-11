import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, fmtTime } from '../ui.jsx';

const signed = (v) => (v > 0 ? `+${v}` : `${v}`);
const EMPTY = { userId: '', delta: '', reason: '', milestoneId: '' };

/** Diem dong gop tong hop: diem tu dong + dieu chinh cua giang vien (kem ly do) + danh gia cheo tham khao. */
export default function TeamEvaluation() {
  const { teamId } = useParams();
  const { user } = useAuth();
  const [team, setTeam] = useState(null);
  const [summary, setSummary] = useState(null);
  const [mains, setMains] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [open, setOpen] = useState(null);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const load = async () => {
    try {
      const [t, s, m] = await Promise.all([
        api.get(`/teams/${teamId}`),
        api.get(`/teams/${teamId}/evaluation-summary`),
        api.get(`/teams/${teamId}/milestones`),
      ]);
      setTeam(t.data); setSummary(s.data); setMains(m.data.filter((x) => !x.parentId && !x.archived));
    } catch (e) {
      setError(errMsg(e));
    }
  };
  useEffect(() => { load(); }, [teamId]);

  const run = async (fn, msg) => {
    setError(''); setOk('');
    try { await fn(); if (msg) setOk(msg); await load(); } catch (e) { setError(errMsg(e)); }
  };

  const submit = (e) => {
    e.preventDefault();
    run(async () => {
      await api.post(`/teams/${teamId}/adjustments`, {
        userId: Number(form.userId),
        delta: Number(form.delta),
        reason: form.reason,
        milestoneId: form.milestoneId ? Number(form.milestoneId) : null,
      });
      setForm(EMPTY);
    }, 'Đã điều chỉnh điểm và thông báo cho thành viên');
  };

  const revoke = (a) => {
    if (!window.confirm(`Thu hồi điều chỉnh ${signed(a.delta)} điểm của ${a.member.fullName}?`)) return;
    run(() => api.delete(`/adjustments/${a.id}`), 'Đã thu hồi điều chỉnh');
  };

  if (!team || !summary) return <Alert error={error} />;

  return (
    <div>
      <p><Link to={`/teams/${teamId}`}>← {team.name}</Link> · <Link to={`/teams/${teamId}/contributions`}>Đóng góp cá nhân</Link></p>
      <h2>Điểm tổng hợp — {team.name}</h2>
      <Alert error={error} ok={ok} />

      <div className="card flush">
        <div className="card-head pad">
          <h3>Điểm đóng góp cuối cùng</h3>
          {summary.seeAll && <span className="muted small">Tổng điểm nhóm: {summary.totalFinalScore}</span>}
        </div>
        <table className="table plain contrib">
          <thead>
            <tr>
              <th>Thành viên</th><th>Điểm tự động (theo task)</th><th>Điều chỉnh của giảng viên</th>
              <th>Điểm cuối cùng</th>{summary.seeAll && <th>Tỷ lệ</th>}<th>Đánh giá chéo TB (/5)</th><th />
            </tr>
          </thead>
          <tbody>
            {summary.members.map((r) => (
              <Row key={r.member.id} r={r} seeAll={summary.seeAll} canAdjust={summary.canAdjust}
                open={open === r.member.id} onToggle={() => setOpen(open === r.member.id ? null : r.member.id)} onRevoke={revoke} />
            ))}
          </tbody>
        </table>
        {summary.members.length === 0 && <p className="muted pad">Chưa có dữ liệu.</p>}
        <div className="formula small muted pad">
          Điểm cuối cùng = điểm tự động + các điều chỉnh còn hiệu lực của giảng viên (không âm). Đánh giá chéo chỉ để giảng viên tham khảo khi điều chỉnh,
          không tự động cộng vào điểm. {!summary.seeAll && 'Bạn chỉ xem được kết quả của chính mình.'}
        </div>
      </div>

      {summary.canAdjust && (
        <form className="card form" onSubmit={submit}>
          <h3>Điều chỉnh điểm đóng góp</h3>
          <p className="muted small">Điều chỉnh được lưu lại cùng lý do và thông báo cho thành viên. Có thể thu hồi nhưng không xóa lịch sử.</p>
          <div className="row">
            <label>Thành viên
              <select value={form.userId} onChange={(e) => setForm({ ...form, userId: e.target.value })} required>
                <option value="">— Chọn —</option>
                {team.members.map((m) => <option key={m.id} value={m.id}>{m.fullName}</option>)}
              </select>
            </label>
            <label>Số điểm (+ cộng, − trừ)
              <input type="number" step="0.5" min="-100" max="100" value={form.delta} onChange={(e) => setForm({ ...form, delta: e.target.value })} required />
            </label>
            <label>Milestone liên quan (tùy chọn)
              <select value={form.milestoneId} onChange={(e) => setForm({ ...form, milestoneId: e.target.value })}>
                <option value="">— Toàn đồ án —</option>
                {mains.map((m) => <option key={m.id} value={m.id}>{m.title}</option>)}
              </select>
            </label>
          </div>
          <label>Lý do (bắt buộc, tối thiểu 5 ký tự)
            <textarea rows={2} minLength={5} maxLength={500} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} required />
          </label>
          <button className="primary" type="submit">Lưu điều chỉnh</button>
        </form>
      )}
    </div>
  );
}

function Row({ r, seeAll, canAdjust, open, onToggle, onRevoke }) {
  return (
    <>
      <tr>
        <td><b>{r.member.fullName}</b>{r.leader && <> <Badge kind="warn">Nhóm trưởng</Badge></>}</td>
        <td>{r.autoScore}</td>
        <td>{r.adjustment === 0 ? '—' : <b className={r.adjustment > 0 ? '' : 'bad-text'}>{signed(r.adjustment)}</b>}</td>
        <td><b>{r.finalScore}</b></td>
        {seeAll && <td>{r.finalSharePercent === null ? '—' : `${r.finalSharePercent}%`}</td>}
        <td>{r.peerAverage === null ? <span className="muted small">Chưa có / chưa đủ</span> : <>{r.peerAverage} <span className="muted small">({r.peerMilestonesRated} milestone)</span></>}</td>
        <td><button className="link small" onClick={onToggle}>{open ? 'Ẩn' : `Lịch sử điều chỉnh (${r.adjustments.length})`}</button></td>
      </tr>
      {open && (
        <tr>
          <td colSpan={seeAll ? 7 : 6}>
            {r.adjustments.length === 0 && <p className="muted small">Chưa có điều chỉnh nào.</p>}
            <ul className="adj-list">
              {r.adjustments.map((a) => (
                <li key={a.id} className={a.revoked ? 'revoked' : ''}>
                  <b className={a.delta > 0 ? '' : 'bad-text'}>{signed(a.delta)}</b> · {a.reason}
                  <div className="muted small">
                    {a.adjustedBy.fullName} · {fmtTime(a.createdAt)}{a.milestoneTitle && ` · ${a.milestoneTitle}`}
                    {a.revoked && <> · <Badge kind="bad">Đã thu hồi {fmtTime(a.revokedAt)}</Badge></>}
                    {canAdjust && !a.revoked && <> · <button className="link small" onClick={() => onRevoke(a)}>Thu hồi</button></>}
                  </div>
                </li>
              ))}
            </ul>
          </td>
        </tr>
      )}
    </>
  );
}
