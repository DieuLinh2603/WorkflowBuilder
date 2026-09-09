import { useEffect, useState } from 'react';
import { CheckCircle2, X, XCircle } from 'lucide-react';

export function notify(message, type = 'success') {
  window.dispatchEvent(new CustomEvent('wf:toast', { detail: { message, type } }));
}

export default function ToastCenter() {
  const [items, setItems] = useState([]);
  useEffect(() => {
    const receive = event => {
      const id = `${Date.now()}-${Math.random()}`;
      setItems(current => [...current, { id, ...event.detail }].slice(-4));
      window.setTimeout(() => setItems(current => current.filter(item => item.id !== id)), 3500);
    };
    window.addEventListener('wf:toast', receive);
    return () => window.removeEventListener('wf:toast', receive);
  }, []);
  const close = id => setItems(current => current.filter(item => item.id !== id));
  return <div className="pointer-events-none fixed right-5 top-5 z-[200] flex w-[360px] max-w-[calc(100vw-40px)] flex-col gap-3">{items.map(item => {
    const success = item.type !== 'error'; const Icon = success ? CheckCircle2 : XCircle;
    return <div key={item.id} role="status" className={`pointer-events-auto flex items-start gap-3 rounded-xl border bg-white p-4 shadow-xl ${success ? 'border-emerald-200' : 'border-red-200'}`}><Icon size={20} className={`mt-0.5 shrink-0 ${success ? 'text-emerald-500' : 'text-red-500'}`}/><div className="min-w-0 flex-1"><p className={`text-sm font-bold ${success ? 'text-emerald-700' : 'text-red-700'}`}>{success ? 'Thành công' : 'Không thành công'}</p><p className="mt-1 text-sm leading-5 text-slate-600">{item.message}</p></div><button type="button" onClick={() => close(item.id)} className="text-slate-400 hover:text-slate-700"><X size={16}/></button></div>;
  })}</div>;
}
