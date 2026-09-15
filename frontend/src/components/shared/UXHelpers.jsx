import { useState } from 'react';
import { ChevronDown, Info, X } from 'lucide-react';

/**
 * Custom hook to manage persistent banner dismissal in localStorage.
 */
export function useDismissibleBanner(key) {
  const [dismissed, setDismissed] = useState(() => {
    try {
      return localStorage.getItem(key) === 'true';
    } catch {
      return false;
    }
  });

  const dismiss = () => {
    try {
      localStorage.setItem(key, 'true');
    } catch {
      // Ignore localStorage errors (e.g. storage disabled or private window)
    }
    setDismissed(true);
  };

  return [dismissed, dismiss];
}

/**
 * Level 4: Dismissible informative banner with localStorage persistence.
 * Best for guidance notes that experienced users can hide.
 */
export function DismissibleBanner({
  storageKey,
  children,
  icon: Icon = Info,
  className = ''
}) {
  const [dismissed, dismiss] = useDismissibleBanner(storageKey);
  if (dismissed) return null;

  return (
    <div className={`relative flex gap-2.5 rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 pr-8 text-[11px] leading-5 text-blue-700 ${className}`}>
      <Icon size={15} className="mt-0.5 shrink-0 text-blue-500" />
      <div className="flex-1 min-w-0">{children}</div>
      <button
        type="button"
        onClick={dismiss}
        title="Không nhắc lại"
        aria-label="Đã hiểu, không nhắc lại"
        className="absolute right-2 top-2 rounded p-1 text-blue-400 hover:bg-blue-100 hover:text-blue-600 transition-colors"
      >
        <X size={13} />
      </button>
    </div>
  );
}

/**
 * Level 2: Collapsible container for long technical notes.
 */
export function CollapsibleNote({
  summary,
  children,
  defaultOpen = false,
  className = '',
  icon: Icon = Info
}) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className={`rounded-xl border border-blue-100 bg-blue-50/70 text-xs leading-5 text-blue-700 transition-colors ${className}`}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center justify-between gap-2 px-3.5 py-2 text-left font-semibold text-blue-800 hover:text-blue-900"
        aria-expanded={open}
      >
        <span className="flex items-center gap-2 min-w-0">
          <Icon size={14} className="shrink-0 text-blue-500" />
          <span className="truncate">{summary}</span>
        </span>
        <ChevronDown
          size={14}
          className={`shrink-0 transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
        />
      </button>
      {open && (
        <div className="border-t border-blue-100/80 px-3.5 pb-2.5 pt-1.5 text-[11px] leading-5 text-blue-700">
          {children}
        </div>
      )}
    </div>
  );
}

/**
 * Level 3: Inline tooltip for technical labels and keywords.
 */
export function LabelWithTooltip({
  label,
  tooltip,
  required = false,
  className = ''
}) {
  return (
    <span className={`inline-flex items-center gap-1.5 ${className}`}>
      <span>{label}</span>
      {required && <span className="text-red-500">*</span>}
      {tooltip && (
        <span className="group relative inline-flex items-center">
          <Info
            size={13}
            className="cursor-help text-slate-400 hover:text-slate-600 transition-colors"
          />
          <span className="pointer-events-none absolute bottom-full left-1/2 z-50 mb-1.5 -translate-x-1/2 w-52 rounded-lg bg-slate-800 px-2.5 py-1.5 text-[11px] leading-4 text-white opacity-0 shadow-xl transition-opacity duration-150 group-hover:opacity-100">
            {tooltip}
          </span>
        </span>
      )}
    </span>
  );
}
