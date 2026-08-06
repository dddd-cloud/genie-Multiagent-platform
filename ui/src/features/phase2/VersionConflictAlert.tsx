import { Alert, Button } from 'antd';

export interface VersionConflictAlertProps {
  onReload: () => void;
  disabled?: boolean;
}

export default function VersionConflictAlert({
  onReload,
  disabled = false,
}: VersionConflictAlertProps) {
  return (
    <Alert
      type="warning"
      showIcon
      data-testid="version-conflict-alert"
      message={getPhase2ConflictTitle()}
      description={
        <Button
          type="primary"
          size="small"
          disabled={disabled}
          onClick={onReload}
          data-testid="version-conflict-reload"
        >
          重新加载服务器版本
        </Button>
      }
    />
  );
}

function getPhase2ConflictTitle(): string {
  return '数据已被他人更新，请刷新后重试';
}
