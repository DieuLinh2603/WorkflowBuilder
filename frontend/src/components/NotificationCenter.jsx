import { useEffect, useState } from 'react';
import { Bell } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../api';

export default function NotificationCenter() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const load = async () => { const r = await apiFetch('/api/notifications'); if (r.ok) setItems(await r.json()); };
  useEffect(() => { load(); const timer = setInterval(load, 10000); return () => clearInterval(timer); }, []);
  const toggle = () => { const next = !open; setOpen(next); if (next) load(); };
  const markRead = async (item) => {
    if (!item.read) {
      const response = await apiFetch(`/api/notifications/${item.id}/read`, { method: 'PATCH', toast: false });
      if (!response.ok) return;
      setItems(value => value.map(current => current.id === item.id ? { ...current, read: true } : current));
    }
    setOpen(false);
    if (item.linkUrl?.startsWith('/')) navigate(item.linkUrl);
  };
  const unread = items.filter(x => !x.read).length;
  return <div className="relative">
    <button onClick={toggle} className="text-gray-400 hover:text-gray-600 relative" aria-label="Thông báo">
      <Bell size={18} />{unread > 0 && <span className="absolute -top-2 -right-2 min-w-4 h-4 px-1 bg-red-500 text-white text-[10px] rounded-full">{unread}</span>}
    </button>
    {open && <div className="absolute right-0 top-8 w-96 max-h-96 overflow-auto bg-white border border-gray-200 rounded-xl shadow-xl z-50">
      <div className="p-3 font-semibold border-b">Thông báo</div>
      {items.length === 0 && <div className="p-5 text-sm text-gray-400">Chưa có thông báo</div>}
      {items.map(item => <button key={item.id} onClick={() => markRead(item)} className={`block w-full text-left p-3 border-b hover:bg-gray-50 ${item.read ? '' : 'bg-orange-50'}`}>
        <div className="text-sm font-semibold">{item.title}</div><div className="text-xs text-gray-500 mt-1">{item.body}</div>
      </button>)}
    </div>}
  </div>;
}
