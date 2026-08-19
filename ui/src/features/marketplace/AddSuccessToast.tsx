import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { CheckCircleFilled } from '@ant-design/icons';
import styles from './AddSuccessToast.module.css';

const HOLD_MS = 3000;
const EXIT_MS = 400;

export default function AddSuccessToast({
  text,
  onDone,
}: {
  text: string;
  onDone: () => void;
}) {
  useEffect(() => {
    const timer = window.setTimeout(onDone, HOLD_MS + EXIT_MS);
    return () => window.clearTimeout(timer);
  }, [onDone]);

  return createPortal(
    <div
      role="status"
      data-testid="marketplace-add-success-toast"
      className={styles.toast}
    >
      <span className={styles.icon} aria-hidden>
        <CheckCircleFilled />
      </span>
      {text}
    </div>,
    document.body,
  );
}
