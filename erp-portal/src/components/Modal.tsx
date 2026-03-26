import type { ReactNode } from 'react';
import { Box, Modal as MantineModal } from '@mantine/core';

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
  const size = extraWide ? 'xl' : wide ? 'lg' : 'md';
  return (
    <MantineModal
      opened
      onClose={onClose}
      title={title}
      size={size}
      centered
      padding="lg"
      closeButtonProps={{ 'aria-label': 'Close' }}
    >
      {children}
      {footer ? <Box mt="md">{footer}</Box> : null}
    </MantineModal>
  );
}
