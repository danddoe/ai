import type { ReactNode } from 'react';

type ModalProps = {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  /** Wider dialog for dense forms (e.g. admin editors). */
  wide?: boolean;
  /** Large dialog for code / JSON viewers. */
  extraWide?: boolean;
};

export function Modal({ title, onClose, children, footer, wide, extraWide }: ModalProps) {
  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        padding: 16,
      }}
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="modal-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        style={{
          background: '#fff',
          borderRadius: 10,
          maxWidth: extraWide ? 920 : wide ? 560 : 480,
          width: '100%',
          maxHeight: '90vh',
          overflow: 'auto',
          boxShadow: '0 20px 40px rgba(0,0,0,0.15)',
        }}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '14px 18px',
            borderBottom: '1px solid #e4e4e7',
          }}
        >
          <h2 id="modal-title" style={{ margin: 0, fontSize: '1rem' }}>
            {title}
          </h2>
          <button type="button" className="btn btn-ghost" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>
        <div style={{ padding: 18 }}>{children}</div>
        {footer && (
          <div
            style={{
              padding: '12px 18px',
              borderTop: '1px solid #e4e4e7',
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
            }}
          >
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
