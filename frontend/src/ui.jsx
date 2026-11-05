// Cac thanh phan giao dien dung chung
export function Alert({ error, ok }) {
  if (!error && !ok) return null;
  return <div className={error ? 'alert error' : 'alert ok'}>{error || ok}</div>;
}

export function Progress({ percent }) {
  return (
    <div className="progress" title={`${percent}%`}>
      <div className="bar" style={{ width: `${percent}%` }} />
      <span>{percent}%</span>
    </div>
  );
}

export function Badge({ kind, children }) {
  return <span className={`badge ${kind || ''}`}>{children}</span>;
}

export const REG_LABEL = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối' };
export const REG_KIND = { PENDING: 'warn', APPROVED: 'good', REJECTED: 'bad' };
export const fmtDate = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '');
export const fmtTime = (d) => (d ? new Date(d).toLocaleString('vi-VN') : '');

// ---------- Icon (bo icon nho dang net don, khong can thu vien) ----------
const ICONS = {
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
  users: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  book: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5zM4 19.5A2.5 2.5 0 0 0 6.5 22H20v-5',
  check: 'M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11',
  bell: 'M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  file: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6',
  alert: 'M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2',
  target: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12zM12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  trend: 'M23 6l-9.5 9.5-5-5L1 18M17 6h6v6',
};

export function Icon({ name, size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={ICONS[name] || ICONS.grid} />
    </svg>
  );
}

// ---------- The thong ke gradient ----------
export function StatCard({ tone, icon, value, label, note }) {
  return (
    <div className={`stat-card ${tone}`}>
      <div className="stat-icon"><Icon name={icon} size={20} /></div>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      {note && <div className="stat-note">{note}</div>}
    </div>
  );
}

// ---------- Bieu do SVG ----------
function smoothPath(points) {
  if (points.length < 2) return '';
  let d = `M ${points[0][0]} ${points[0][1]}`;
  for (let i = 0; i < points.length - 1; i++) {
    const [x0, y0] = points[i];
    const [x1, y1] = points[i + 1];
    const cx = (x0 + x1) / 2;
    d += ` C ${cx} ${y0}, ${cx} ${y1}, ${x1} ${y1}`;
  }
  return d;
}

function toPoints(values, w, h, pad, max) {
  const vals = values.length === 1 ? [values[0], values[0]] : values;
  const m = max || Math.max(1, ...vals);
  return vals.map((v, i) => [pad + (i * (w - 2 * pad)) / (vals.length - 1), h - pad - (v / m) * (h - 2 * pad)]);
}

let gid = 0;
export function AreaChart({ values, labels = [], color = '#f97316', height = 150 }) {
  const id = `ag${++gid}`;
  const w = 600;
  const data = values.length ? values : [0, 0];
  const pts = toPoints(data, w, height, 8, 100);
  const line = smoothPath(pts);
  const area = `${line} L ${pts[pts.length - 1][0]} ${height} L ${pts[0][0]} ${height} Z`;
  return (
    <div>
      <svg viewBox={`0 0 ${w} ${height}`} preserveAspectRatio="none" className="chart" style={{ height }}>
        <defs>
          <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.85" />
            <stop offset="100%" stopColor={color} stopOpacity="0.05" />
          </linearGradient>
        </defs>
        {[25, 50, 75].map((p) => (
          <line key={p} x1="0" x2={w} y1={height - 8 - (p / 100) * (height - 16)} y2={height - 8 - (p / 100) * (height - 16)} stroke="#eef0f3" strokeWidth="1" />
        ))}
        <path d={area} fill={`url(#${id})`} />
        <path d={line} fill="none" stroke={color} strokeWidth="2.5" vectorEffect="non-scaling-stroke" />
      </svg>
      {labels.length > 0 && (
        <div className="chart-labels">
          {labels.map((l, i) => <span key={i}>{l}</span>)}
        </div>
      )}
    </div>
  );
}

export function Sparkline({ values, color = '#16a34a', height = 70 }) {
  const w = 300;
  const data = values.length ? values : [0, 0];
  const pts = toPoints(data, w, height, 8);
  return (
    <div style={{ position: 'relative', height }}>
      <svg viewBox={`0 0 ${w} ${height}`} preserveAspectRatio="none" className="chart" style={{ height }}>
        <path d={smoothPath(pts)} fill="none" stroke={color} strokeWidth="2.5" vectorEffect="non-scaling-stroke" />
      </svg>
      {pts.map(([x, y], i) => (
        <span key={i} className="dot" style={{ left: `${(x / w) * 100}%`, top: `${(y / height) * 100}%`, borderColor: color }} />
      ))}
    </div>
  );
}

export function MiniBars({ values, color = '#6b8cf5', height = 70 }) {
  const data = values.length ? values : [0];
  const max = Math.max(1, ...data);
  return (
    <div className="minibars" style={{ height }}>
      {data.map((v, i) => (
        <div key={i} className="mb" style={{ height: `${Math.max(6, (v / max) * 100)}%`, background: color }} title={v} />
      ))}
    </div>
  );
}

// ---------- Thanh tien do co nhan (kieu "63% Generated Leads") ----------
export function HBar({ percent, label, color = '#d92550' }) {
  return (
    <div className="hbar">
      <div className="hbar-head"><b>{percent}%</b><span>{label}</span></div>
      <div className="hbar-track"><div style={{ width: `${percent}%`, background: color }} /></div>
    </div>
  );
}

// ---------- The muc tieu nho (hang duoi dashboard) ----------
export function TargetCard({ percent, label, color }) {
  return (
    <div className="target">
      <b style={{ color }}>{percent}%</b>
      <div>
        <div className="muted small">{label}</div>
        <div className="hbar-track"><div style={{ width: `${percent}%`, background: color }} /></div>
      </div>
    </div>
  );
}
