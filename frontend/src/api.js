import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('token');
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

api.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401 && !window.location.pathname.startsWith('/login')) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export const errMsg = (e) => e?.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại';

export async function downloadFile(url, fileName) {
  const res = await api.get(url, { responseType: 'blob' });
  const href = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(href);
}

export const downloadSubmission = (sub) => downloadFile(`/submissions/${sub.id}/download`, sub.fileName);
export const downloadTaskResult = (task) => downloadFile(`/tasks/${task.id}/result`, task.resultFileName);

export const downloadTaskAttempt = (attempt) => downloadFile(`/task-submissions/${attempt.id}/download`, attempt.fileName);

export default api;
