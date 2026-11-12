import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { errMsg } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert } from '../ui.jsx';

const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const DOW = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

/** Lich thang: han milestone va han task cua nhom (task tre han to do). */
export default function TeamCalendar() {
  const { teamId } = useParams();
  const { user } = useAuth();
  const [team, setTeam] = useState(null);
  const [month, setMonth] = useState(() => { const n = new Date(); return new Date(n.getFullYear(), n.getMonth(), 1); });
  const [events, setEvents] = useState([]);
  const [mine, setMine] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { api.get(`/teams/${teamId}`).then((r) => setTeam(r.data)).catch((e) => setError(errMsg(e))); }, [teamId]);
  useEffect(() => {
    const from = new Date(month.getFullYear(), month.getMonth(), 1);
    const to = new Date(month.getFullYear(), month.getMonth() + 1, 0);
    api.get(`/teams/${teamId}/calendar`, { params: { from: iso(from), to: iso(to) } })
      .then((r) => setEvents(r.data)).catch((e) => setError(errMsg(e)));
  }, [teamId, month]);

  const byDay = useMemo(() => {
    const m = {};
    events.filter((e) => !mine || e.type === 'MILESTONE' || e.assignee?.id === user.id)
      .forEach((e) => { (m[e.date] = m[e.date] || []).push(e); });
    return m;
  }, [events, mine, user.id]);

  if (!team) return <Alert error={error} />;
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const lead = (first.getDay() + 6) % 7; // thu Hai la cot dau
  const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const cells = [...Array(lead).fill(null), ...Array.from({ length: days }, (_, i) => i + 1)];
  const today = iso(new Date());
  const shift = (n) => setMonth(new Date(month.getFullYear(), month.getMonth() + n, 1));

  return (
    <div>
      <p><Link to={`/teams/${teamId}`}>← {team.name}</Link></p>
      <h2>Lịch — {team.name}</h2>
      <Alert error={error} />
      <div className="card">
        <div className="card-head">
          <div className="actions">
            <button onClick={() => shift(-1)}>←</button>
            <b>Tháng {month.getMonth() + 1}/{month.getFullYear()}</b>
            <button onClick={() => shift(1)}>→</button>
          </div>
          {user.role === 'STUDENT' && (
            <label className="small"><input type="checkbox" checked={mine} onChange={(e) => setMine(e.target.checked)} /> Chỉ task của tôi</label>
          )}
        </div>
        <div className="cal-grid">
          {DOW.map((d) => <div key={d} className="cal-dow">{d}</div>)}
          {cells.map((d, i) => {
            if (!d) return <div key={`e${i}`} className="cal-cell empty" />;
            const key = iso(new Date(month.getFullYear(), month.getMonth(), d));
            return (
              <div key={key} className={`cal-cell${key === today ? ' today' : ''}`}>
                <div className="cal-day">{d}</div>
                {(byDay[key] || []).map((e) => (
                  <Link key={`${e.type}${e.id}`} to={`/milestones/${e.milestoneId}`}
                    className={`cal-ev ${e.type === 'MILESTONE' ? 'ms' : e.overdue ? 'late' : e.status === 'DONE' ? 'done' : ''}`}
                    title={`${e.title}${e.assignee ? ' — ' + e.assignee.fullName : ''}`}>{e.title}</Link>
                ))}
              </div>
            );
          })}
        </div>
        <p className="small muted">Xanh dương: milestone · đỏ: task trễ hạn · xanh lá: task đã xong. Bấm vào mục để mở milestone.</p>
      </div>
    </div>
  );
}
