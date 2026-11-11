import { useEffect, useState } from 'react';
import api, { errMsg } from '../api.js';
import { Alert, Badge } from '../ui.jsx';

const CONTRIB = ['', 'Rất thấp', 'Thấp', 'Trung bình', 'Cao', 'Rất cao'];
const WORKLOAD = ['', 'Rất nhẹ', 'Nhẹ', 'Vừa', 'Nặng', 'Rất nặng'];

/** Danh gia cheo AN DANH cho mot milestone chinh: sinh vien cham dong doi, giang vien xem ket qua tong hop. */
export default function PeerEvaluation({ milestone, team, user }) {
  const isMember = user.role === 'STUDENT' && team.members.some((m) => m.id === user.id);
  const [mine, setMine] = useState(null);
  const [results, setResults] = useState(null);
  const [form, setForm] = useState({});
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      if (isMember) {
        const m = (await api.get(`/milestones/${milestone.id}/peer-evaluations/mine`)).data;
        setMine(m);
        const f = {};
        m.ratings.forEach((r) => { f[r.evaluateeId] = { c: String(r.contributionScore), w: String(r.workloadScore), comment: r.comment || '' }; });
        setForm(f);
      }
      setResults((await api.get(`/milestones/${milestone.id}/peer-evaluations/results`)).data);
    } catch (e) {
      setError(errMsg(e));
    }
  };
  useEffect(() => { load(); }, [milestone.id]);

  const set = (id, k, v) => setForm((f) => ({ ...f, [id]: { c: '', w: '', comment: '', ...(f[id] || {}), [k]: v } }));

  const save = async (e) => {
    e.preventDefault();
    setError(''); setOk(''); setSaving(true);
    try {
      const ratings = mine.teammates.map((t) => ({
        evaluateeId: t.id,
        contributionScore: Number(form[t.id]?.c),
        workloadScore: Number(form[t.id]?.w),
        comment: form[t.id]?.comment || null,
      }));
      await api.put(`/milestones/${milestone.id}/peer-evaluations`, { ratings });
      setOk('Đã lưu đánh giá của bạn. Đánh giá được giữ ẩn danh, bạn có thể sửa lại bất cứ lúc nào.');
      await load();
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setSaving(false);
    }
  };

  const saved = mine && mine.teammates.length > 0 && mine.ratings.length === mine.teammates.length;

  return (
    <div className="card">
      <div className="row between">
        <h3>Đánh giá chéo (ẩn danh)</h3>
        {isMember && mine && (saved ? <Badge kind="good">Bạn đã đánh giá</Badge> : <Badge kind="warn">Bạn chưa đánh giá</Badge>)}
      </div>
      <p className="muted small">
        Mỗi thành viên chấm các bạn trong nhóm theo hai tiêu chí: <b>mức độ đóng góp</b> và <b>độ nặng của công việc được giao</b> (thang 1–5) trong milestone này.
        Không ai (kể cả giảng viên) biết ai đã chấm cho ai.
      </p>
      <Alert error={error} ok={ok} />

      {isMember && mine && mine.teammates.length === 0 && <p className="muted">Nhóm chỉ có một thành viên nên chưa có ai để đánh giá.</p>}
      {isMember && mine && mine.teammates.length > 0 && !mine.open && <div className="note">Milestone đã được lưu trữ, không thể sửa đánh giá.</div>}
      {isMember && mine && mine.teammates.length > 0 && mine.open && (
        <form className="form" onSubmit={save}>
          {mine.teammates.map((t) => {
            const f = form[t.id] || { c: '', w: '', comment: '' };
            return (
              <div className="peer-row" key={t.id}>
                <b>{t.fullName}</b>
                <label>Mức độ đóng góp
                  <select value={f.c} onChange={(e) => set(t.id, 'c', e.target.value)} required>
                    <option value="">— Chọn —</option>
                    {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n} · {CONTRIB[n]}</option>)}
                  </select>
                </label>
                <label>Độ nặng công việc được giao
                  <select value={f.w} onChange={(e) => set(t.id, 'w', e.target.value)} required>
                    <option value="">— Chọn —</option>
                    {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n} · {WORKLOAD[n]}</option>)}
                  </select>
                </label>
                <label className="grow">Nhận xét (tùy chọn, ẩn danh)
                  <input maxLength={500} value={f.comment} onChange={(e) => set(t.id, 'comment', e.target.value)} />
                </label>
              </div>
            );
          })}
          <button className="primary" type="submit" disabled={saving}>{saved ? 'Cập nhật đánh giá' : 'Gửi đánh giá'}</button>
        </form>
      )}

      {results && (
        <div className="peer-results">
          <h4>{results.seeAll ? 'Kết quả tổng hợp' : 'Kết quả đánh giá dành cho bạn'}</h4>
          <p className="muted small">
            {results.evaluatorsDone}/{results.evaluatorsTotal} thành viên đã đánh giá đủ.
            {results.seeAll && results.pendingEvaluators.length > 0 && <> Chưa đánh giá: <b>{results.pendingEvaluators.join(', ')}</b>.</>}
          </p>
          <table className="table plain contrib">
            <thead><tr><th>Thành viên</th><th>Số lượt đánh giá</th><th>Đóng góp (TB /5)</th><th>Độ nặng công việc (TB /5)</th><th>Trung bình chung</th></tr></thead>
            <tbody>
              {results.members.map((r) => (
                <tr key={r.member.id}>
                  <td><b>{r.member.fullName}</b></td>
                  <td>{r.ratingCount}</td>
                  {r.visible ? (
                    <>
                      <td>{r.avgContribution}</td>
                      <td>{r.avgWorkload}</td>
                      <td><b>{r.avgOverall}</b></td>
                    </>
                  ) : (
                    <td colSpan={3} className="muted small">
                      {results.seeAll ? 'Chưa có đánh giá.' : `Cần ít nhất ${results.minRatingsToShow} lượt đánh giá để hiển thị (bảo vệ ẩn danh).`}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          {results.members.filter((r) => r.visible && r.comments.length > 0).map((r) => (
            <details key={r.member.id} className="subform">
              <summary>Nhận xét ẩn danh về {r.member.fullName} ({r.comments.length})</summary>
              <ul>{r.comments.map((c, i) => <li key={i}>{c}</li>)}</ul>
            </details>
          ))}
        </div>
      )}
    </div>
  );
}
