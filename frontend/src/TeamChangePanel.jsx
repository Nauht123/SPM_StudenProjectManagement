import { useEffect, useState } from 'react';
import api, { errMsg } from './api.js';
import { Alert, Badge, fmtTime } from './ui.jsx';

export const CHANGE_TYPE = {
  ADD_MEMBER: 'Thêm thành viên',
  REMOVE_MEMBER: 'Xóa thành viên',
  REPLACE_MEMBER: 'Thay thành viên',
  TRANSFER_LEADER: 'Chuyển quyền nhóm trưởng',
};
export const CHANGE_STATUS = { PENDING: ['Chờ giảng viên duyệt', 'warn'], APPROVED: ['Đã duyệt', 'good'], REJECTED: ['Bị từ chối', 'bad'], CANCELLED: ['Đã hủy', ''] };

/** Mo ta mot yeu cau bang cau van ngan gon. */
export function describeChange(r) {
  const target = r.target?.fullName;
  const added = r.newMember?.fullName;
  const self = r.target && r.requestedBy && r.target.id === r.requestedBy.id;
  switch (r.type) {
    case 'ADD_MEMBER': return `Thêm ${added} vào nhóm`;
    case 'REMOVE_MEMBER': return self ? `${target} xin rời nhóm` : `Xóa ${target} khỏi nhóm`;
    case 'REPLACE_MEMBER': return `Thay ${target} bằng ${added}`;
    case 'TRANSFER_LEADER': return `Chuyển quyền nhóm trưởng cho ${target}`;
    default: return r.type;
  }
}

/**
 * Sau khi de tai duoc duyet, nhom truong khong the don phuong them/xoa/thay nguoi hay chuyen quyen:
 * phai gui yeu cau de giang vien huong dan duyet. Thanh vien chi duoc xin roi nhom.
 */
export default function TeamChangePanel({ team, user, onChanged }) {
  const isLeader = team.leader.id === user.id;
  const [list, setList] = useState([]);
  const [form, setForm] = useState({ type: 'REMOVE_MEMBER', targetMemberId: '', newMemberCode: '', reason: '' });
  const [leaveReason, setLeaveReason] = useState('');
  const [leaving, setLeaving] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const load = () => api.get(`/teams/${team.id}/change-requests`).then((r) => setList(r.data)).catch(() => {});
  useEffect(() => { load(); }, [team.id]);

  const send = async (body, done) => {
    setError(''); setOk('');
    try {
      await api.post(`/teams/${team.id}/change-requests`, body);
      setOk('Đã gửi yêu cầu. Thay đổi chỉ có hiệu lực khi giảng viên hướng dẫn duyệt.');
      done();
      load();
    } catch (e) {
      setError(errMsg(e));
    }
  };

  const submit = (e) => {
    e.preventDefault();
    const needTarget = form.type !== 'ADD_MEMBER';
    const needCode = form.type === 'ADD_MEMBER' || form.type === 'REPLACE_MEMBER';
    send({
      type: form.type,
      targetMemberId: needTarget ? Number(form.targetMemberId) : null,
      newMemberCode: needCode ? form.newMemberCode : null,
      reason: form.reason,
    }, () => setForm({ ...form, targetMemberId: '', newMemberCode: '', reason: '' }));
  };

  const leave = (e) => {
    e.preventDefault();
    send({ type: 'REMOVE_MEMBER', targetMemberId: user.id, newMemberCode: null, reason: leaveReason }, () => { setLeaveReason(''); setLeaving(false); });
  };

  const cancel = async (r) => {
    setError(''); setOk('');
    try { await api.post(`/change-requests/${r.id}/cancel`); load(); onChanged?.(); } catch (e) { setError(errMsg(e)); }
  };

  const candidates = team.members.filter((m) => m.id !== team.leader.id);
  const needTarget = form.type !== 'ADD_MEMBER';
  const needCode = form.type === 'ADD_MEMBER' || form.type === 'REPLACE_MEMBER';
  const targets = form.type === 'TRANSFER_LEADER' ? candidates : candidates;

  return (
    <div className="card">
      <h3>Thay đổi nhóm (cần giảng viên duyệt)</h3>
      <p className="muted small">Nhóm đã được duyệt đề tài nên không thể tự thêm, xóa hoặc thay thành viên. Mọi thay đổi phải gửi yêu cầu để giảng viên hướng dẫn duyệt.</p>
      <Alert error={error} ok={ok} />

      {isLeader ? (
        <form className="form" onSubmit={submit}>
          <div className="row">
            <label>Loại yêu cầu
              <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value, targetMemberId: '' })}>
                {Object.entries(CHANGE_TYPE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </label>
            {needTarget && (
              <label>{form.type === 'TRANSFER_LEADER' ? 'Nhóm trưởng mới' : 'Thành viên'}
                <select value={form.targetMemberId} onChange={(e) => setForm({ ...form, targetMemberId: e.target.value })} required>
                  <option value="">— Chọn —</option>
                  {targets.map((m) => <option key={m.id} value={m.id}>{m.fullName} ({m.studentCode})</option>)}
                </select>
              </label>
            )}
            {needCode && (
              <label>MSSV sinh viên mới
                <input value={form.newMemberCode} onChange={(e) => setForm({ ...form, newMemberCode: e.target.value })} required />
              </label>
            )}
          </div>
          <label>Lý do (bắt buộc)<textarea rows={2} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} required minLength={5} /></label>
          <button className="primary" type="submit">Gửi yêu cầu cho giảng viên</button>
        </form>
      ) : (
        <div>
          {!leaving && <button onClick={() => setLeaving(true)}>Xin rời nhóm</button>}
          {leaving && (
            <form className="form" onSubmit={leave}>
              <label>Lý do xin rời nhóm<textarea rows={2} value={leaveReason} onChange={(e) => setLeaveReason(e.target.value)} required minLength={5} /></label>
              <div className="actions">
                <button className="danger" type="submit">Gửi yêu cầu xin rời nhóm</button>
                <button type="button" onClick={() => setLeaving(false)}>Hủy</button>
              </div>
            </form>
          )}
        </div>
      )}

      {list.length > 0 && (
        <table className="table plain">
          <thead><tr><th>Yêu cầu</th><th>Người gửi</th><th>Lý do</th><th>Trạng thái</th><th /></tr></thead>
          <tbody>
            {list.map((r) => {
              const st = CHANGE_STATUS[r.status];
              return (
                <tr key={r.id}>
                  <td><b>{describeChange(r)}</b><div className="muted small">{fmtTime(r.createdAt)}</div></td>
                  <td>{r.requestedBy.fullName}</td>
                  <td>{r.reason}</td>
                  <td><Badge kind={st[1]}>{st[0]}</Badge>{r.decisionNote && <div className="muted small">Giảng viên: {r.decisionNote}</div>}</td>
                  <td className="actions">{r.status === 'PENDING' && r.requestedBy.id === user.id && <button onClick={() => cancel(r)}>Hủy</button>}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
