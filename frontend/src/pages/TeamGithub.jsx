import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Badge, MiniBars, fmtDate, fmtTime } from '../ui.jsx';

const PR_KIND = { OPEN: 'warn', MERGED: 'good', CLOSED: 'bad' };
const PR_LABEL = { OPEN: 'Đang mở', MERGED: 'Đã merge', CLOSED: 'Đã đóng' };

/** Tich hop GitHub: lien ket repo, dong bo commit/PR, thong ke theo thanh vien. */
export default function TeamGithub() {
  const { teamId } = useParams();
  const { user } = useAuth();
  const [team, setTeam] = useState(null);
  const [gh, setGh] = useState(null);
  const [form, setForm] = useState({ repoUrl: '', token: '' });
  const [logins, setLogins] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const load = async () => {
    try {
      const [t, g] = await Promise.all([api.get(`/teams/${teamId}`), api.get(`/teams/${teamId}/github`)]);
      setTeam(t.data); setGh(g.data);
      setLogins(Object.fromEntries(g.data.members.map((m) => [m.member.id, m.login || ''])));
    } catch (e) { setError(errMsg(e)); }
  };
  useEffect(() => { load(); }, [teamId]);

  const run = async (fn, msg) => {
    setError(''); setOk(''); setBusy(true);
    try { const res = await fn(); if (res?.data) setGh(res.data); if (msg) setOk(msg); } catch (e) { setError(errMsg(e)); await load(); }
    setBusy(false);
  };

  if (!team || !gh) return <Alert error={error} />;
  const canMap = (m) => gh.canManage || m.member.id === user.id;

  return (
    <div>
      <p><Link to={`/teams/${teamId}`}>← {team.name}</Link></p>
      <h2>GitHub — {team.name}</h2>
      <Alert error={error} ok={ok} />

      <div className="card">
        <div className="card-head">
          <h3>Repo liên kết</h3>
          {gh.linked && (
            <div className="actions">
              <button className="primary" disabled={busy}
                onClick={() => run(() => api.post(`/teams/${teamId}/github/sync`), 'Đã đồng bộ từ GitHub')}>
                {busy ? 'Đang đồng bộ…' : 'Đồng bộ ngay'}
              </button>
              {gh.canManage && (
                <button className="danger" disabled={busy} onClick={() => {
                  if (!window.confirm('Gỡ liên kết repo? Dữ liệu commit/PR đã đồng bộ sẽ bị xóa khỏi hệ thống.')) return;
                  run(async () => { await api.delete(`/teams/${teamId}/github`); await load(); }, 'Đã gỡ liên kết');
                }}>Gỡ liên kết</button>
              )}
            </div>
          )}
        </div>
        {gh.linked ? (
          <p>
            <a href={gh.repoUrl} target="_blank" rel="noreferrer">{gh.owner}/{gh.repo}</a>{' '}
            <Badge kind={gh.hasToken ? 'good' : 'warn'}>{gh.hasToken ? 'Có token' : 'Không token (repo công khai)'}</Badge>
            <br />
            <span className="small muted">
              {gh.lastSyncAt ? `Đồng bộ lần cuối: ${fmtTime(gh.lastSyncAt)}` : 'Chưa đồng bộ lần nào — bấm "Đồng bộ ngay"'}
            </span>
            {gh.lastSyncError && <span className="small" style={{ color: '#d92550' }}> · Lỗi: {gh.lastSyncError}</span>}
          </p>
        ) : <p className="muted">Nhóm chưa liên kết repo GitHub.</p>}

        {gh.canManage && (
          <form className="row-form" onSubmit={(e) => {
            e.preventDefault();
            run(() => api.put(`/teams/${teamId}/github`, { repoUrl: form.repoUrl, token: form.token || null }),
              'Đã liên kết repo').then(() => setForm({ repoUrl: '', token: '' }));
          }}>
            <input required placeholder="https://github.com/ten-nguoi-dung/ten-repo" value={form.repoUrl}
              onChange={(e) => setForm({ ...form, repoUrl: e.target.value })} />
            <input type="password" autoComplete="off" placeholder="Personal access token (bỏ trống nếu repo công khai)"
              value={form.token} onChange={(e) => setForm({ ...form, token: e.target.value })} />
            <button className="primary" disabled={busy} type="submit">{gh.linked ? 'Đổi repo / token' : 'Liên kết'}</button>
          </form>
        )}
        {gh.canManage && (
          <p className="small muted">
            Token cá nhân (GitHub → Settings → Developer settings → Personal access tokens) chỉ cần quyền đọc nội dung repo
            (Contents: read, Pull requests: read). Token được mã hóa khi lưu và không hiển thị lại.
          </p>
        )}
        {!gh.canManage && !gh.linked && <p className="small muted">Chỉ nhóm trưởng mới liên kết được repo.</p>}
      </div>

      <div className="card flush">
        <div className="card-head pad"><h3>Đóng góp trên GitHub theo thành viên</h3>
          <span className="muted small">{gh.totalCommits} commit · {gh.totalPulls} pull request</span></div>
        <table className="table plain">
          <thead><tr><th>Thành viên</th><th>Tài khoản GitHub</th><th>Commit</th><th>PR đã tạo</th><th>PR đã merge</th><th>Hoạt động gần nhất</th></tr></thead>
          <tbody>
            {gh.members.map((m) => (
              <tr key={m.member.id}>
                <td><b>{m.member.fullName}</b></td>
                <td>
                  {canMap(m) ? (
                    <form className="row-form" onSubmit={(e) => {
                      e.preventDefault();
                      run(() => api.put(`/teams/${teamId}/github/accounts`, { userId: m.member.id, login: logins[m.member.id] || '' }),
                        'Đã lưu tài khoản GitHub');
                    }}>
                      <input placeholder="username" value={logins[m.member.id] ?? ''}
                        onChange={(e) => setLogins({ ...logins, [m.member.id]: e.target.value })} />
                      <button disabled={busy} type="submit">Lưu</button>
                    </form>
                  ) : (m.login || <span className="muted">Chưa khai báo</span>)}
                </td>
                <td>{m.commits}</td><td>{m.pullsOpened}</td><td>{m.pullsMerged}</td>
                <td>{m.lastActivity ? fmtTime(m.lastActivity) : <span className="muted">—</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {gh.unmapped.length > 0 && (
          <div className="pad small">
            <b>Chưa gán cho thành viên nào:</b>{' '}
            {gh.unmapped.map((u) => `${u.author} (${u.commits} commit, ${u.pulls} PR)`).join(' · ')}
            <div className="muted">Hãy khai báo đúng username GitHub ở bảng trên để các commit này được tính cho người thực hiện.</div>
          </div>
        )}
      </div>

      {gh.linked && (
        <div className="card">
          <h3>Commit theo tuần (8 tuần gần nhất)</h3>
          <MiniBars values={gh.weekly.map((w) => w.commits)} height={90} />
          <div className="small muted">{gh.weekly.map((w) => `${fmtDate(w.weekStart)}: ${w.commits}`).join(' · ')}</div>
        </div>
      )}

      {gh.linked && (
        <div className="grid2">
          <div className="card flush">
            <div className="card-head pad"><h3>Commit gần đây</h3></div>
            <table className="table plain">
              <tbody>
                {gh.recentCommits.map((c) => (
                  <tr key={c.sha}>
                    <td><a href={c.url} target="_blank" rel="noreferrer">{c.sha.slice(0, 7)}</a></td>
                    <td>{c.message}</td>
                    <td className="small">{c.member ? c.member.fullName : (c.authorLogin || c.authorName)}</td>
                    <td className="small muted">{fmtTime(c.committedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {gh.recentCommits.length === 0 && <p className="muted pad">Chưa có commit nào được đồng bộ.</p>}
          </div>
          <div className="card flush">
            <div className="card-head pad"><h3>Pull request gần đây</h3></div>
            <table className="table plain">
              <tbody>
                {gh.recentPulls.map((p) => (
                  <tr key={p.number}>
                    <td><a href={p.url} target="_blank" rel="noreferrer">#{p.number}</a></td>
                    <td>{p.title}</td>
                    <td className="small">{p.member ? p.member.fullName : p.authorLogin}</td>
                    <td><Badge kind={PR_KIND[p.state]}>{PR_LABEL[p.state]}</Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>
            {gh.recentPulls.length === 0 && <p className="muted pad">Chưa có pull request nào.</p>}
          </div>
        </div>
      )}
    </div>
  );
}
