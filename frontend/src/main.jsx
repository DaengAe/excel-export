import React, {useCallback, useEffect, useState} from 'react';
import {createRoot} from 'react-dom/client';
import './styles.css';

const formatter = new Intl.DateTimeFormat('ko-KR', {dateStyle: 'short', timeStyle: 'medium'});
const text = {pending: '대기 중', processing: '생성 중', done: '완료', failed: '실패'};
function format(value) { return value ? formatter.format(new Date(value)) : '-'; }

function App() {
  const [jobs, setJobs] = useState([]);
  const [requesting, setRequesting] = useState(false);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    try {
      const response = await fetch('/api/export-jobs');
      if (!response.ok) throw new Error('목록을 불러오지 못했습니다.');
      setJobs(await response.json()); setError('');
    } catch (e) { setError(e.message); }
  }, []);
  useEffect(() => { load(); const id = setInterval(load, jobs.some(j => j.status === 'pending' || j.status === 'processing') ? 2000 : 10000); return () => clearInterval(id); }, [load, jobs]);
  const create = async () => {
    setRequesting(true); setError('');
    try { const response = await fetch('/api/export-jobs', {method: 'POST'}); if (!response.ok) throw new Error('생성 요청을 접수하지 못했습니다.'); await load(); }
    catch (e) { setError(e.message); } finally { setRequesting(false); }
  };
  return <main><header><p className="eyebrow">PLAYSTORY PRE-ASSIGNMENT</p><h1>Excel Export Jobs</h1><p>10만 건 주문 데이터를 백그라운드에서 xlsx로 생성합니다.</p></header>
    <section className="toolbar"><button onClick={create} disabled={requesting}>{requesting ? '요청 중…' : '새 엑셀 생성 요청'}</button><button className="secondary" onClick={load}>새로고침</button></section>
    {error && <p className="error">{error}</p>}
    <section className="card"><table><thead><tr><th>Job ID</th><th>상태</th><th>요청</th><th>시작</th><th>완료</th><th>파일</th></tr></thead><tbody>{jobs.length === 0 ? <tr><td colSpan="6" className="empty">아직 요청된 작업이 없습니다.</td></tr> : jobs.map(job => <tr key={job.id}><td className="id">{job.id}</td><td><span className={'badge ' + job.status}>{text[job.status]}</span></td><td>{format(job.requestedAt)}</td><td>{format(job.startedAt)}</td><td>{format(job.finishedAt)}</td><td>{job.status === 'done' ? <a href={`/api/export-jobs/${job.id}/download`}>다운로드</a> : job.status === 'failed' ? <span className="failure" title={job.errorMessage}>{job.errorCode || '생성 실패'}</span> : '-'}</td></tr>)}</tbody></table></section>
  </main>;
}
createRoot(document.getElementById('root')).render(<App/>);
