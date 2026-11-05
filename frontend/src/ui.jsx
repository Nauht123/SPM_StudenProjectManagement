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
