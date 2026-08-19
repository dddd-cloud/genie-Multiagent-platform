import { memo } from 'react';
import { Modal } from 'antd';
import { useSettingsModal } from './SettingsModalContext';
import SettingsHashRouter from './SettingsHashRouter';
import SettingsRoutes from './SettingsRoutes';

const SettingsModal: GenieType.FC = memo(() => {
  const { open, closeSettings, session } = useSettingsModal();

  return (
    <Modal
      open={open}
      onCancel={closeSettings}
      footer={null}
      closable={false}
      maskClosable
      centered
      width={960}
      destroyOnHidden
      data-testid="settings-modal"
      className="settings-modal"
      styles={{
        content: { padding: 0, borderRadius: 16, overflow: 'hidden' },
        body: { padding: 0 },
      }}
    >
      {open ? (
        <div data-testid="settings-modal">
          <SettingsHashRouter key={session}>
            <SettingsRoutes />
          </SettingsHashRouter>
        </div>
      ) : null}
    </Modal>
  );
});

SettingsModal.displayName = 'SettingsModal';

export default SettingsModal;
