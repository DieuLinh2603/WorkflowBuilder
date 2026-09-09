import { ChevronLeft, ChevronRight } from 'lucide-react';

export default function Pagination({page,totalItems,pageSize,onPageChange,itemLabel='bản ghi'}){
 const totalPages=Math.max(1,Math.ceil(totalItems/pageSize));
 const safePage=Math.min(page,totalPages-1);
 const start=totalItems?safePage*pageSize+1:0,end=Math.min((safePage+1)*pageSize,totalItems);
 const first=Math.max(0,Math.min(safePage-2,totalPages-5));
 const pages=Array.from({length:Math.min(5,totalPages)},(_,index)=>first+index);
 return <div className="flex flex-wrap items-center justify-between gap-3 border-t border-grayBorder bg-white px-6 py-4 text-sm text-gray-500"><p>Hiển thị <span className="font-semibold text-slate-800">{start}-{end}</span> trong <span className="font-semibold text-slate-800">{totalItems}</span> {itemLabel}</p><div className="flex items-center gap-1"><button type="button" disabled={safePage===0} onClick={()=>onPageChange(safePage-1)} className="flex h-8 w-8 items-center justify-center rounded-md border border-grayBorder disabled:opacity-40"><ChevronLeft size={15}/></button>{first>0&&<span className="px-1">…</span>}{pages.map(value=><button type="button" key={value} onClick={()=>onPageChange(value)} className={`h-8 min-w-8 rounded-md px-2 text-xs font-semibold ${safePage===value?'bg-orange-500 text-white':'border border-grayBorder text-slate-600 hover:bg-slate-50'}`}>{value+1}</button>)}{first+pages.length<totalPages&&<span className="px-1">…</span>}<button type="button" disabled={safePage>=totalPages-1||!totalItems} onClick={()=>onPageChange(safePage+1)} className="flex h-8 w-8 items-center justify-center rounded-md border border-grayBorder disabled:opacity-40"><ChevronRight size={15}/></button></div></div>;
}
