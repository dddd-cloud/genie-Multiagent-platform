import { memo, useEffect, useRef } from 'react';
import { Checkbox, Input, Radio, Tag, Typography } from 'antd';
import type {
  McpAuthType,
  Phase2McpServerResponse,
} from '@/contracts/phase2';

const { Text } = Typography;

export interface McpFormState {
  name: string;
  serverUrl: string;
  authType: McpAuthType;
  authName: string;
  clearCredential: boolean;
  version: number | null;
  status: Phase2McpServerResponse['status'] | null;
  credentialConfigured: boolean;
  lastCheckStatus: string | null;
  lastCheckCode: string | null;
}

export function emptyMcpFormState(): McpFormState {
  return {
    name: '',
    serverUrl: '',
    authType: 'NONE',
    authName: '',
    clearCredential: false,
    version: null,
    status: null,
    credentialConfigured: false,
    lastCheckStatus: null,
    lastCheckCode: null,
  };
}

export function serverToFormState(
  server: Phase2McpServerResponse,
): McpFormState {
  return {
    name: server.name,
    serverUrl: server.serverUrl,
    authType: server.authType,
    authName: server.authName ?? '',
    clearCredential: false,
    version: server.version,
    status: server.status,
    credentialConfigured: server.credentialConfigured,
    lastCheckStatus: server.lastCheckStatus,
    lastCheckCode: server.lastCheckCode,
  };
}

export function validateMcpForm(state: McpFormState): string | null {
  if (!state.name.trim()) return '请填写 MCP 名称';
  if (!state.serverUrl.trim()) return '请填写 serverUrl';
  if (state.authType === 'QUERY_PARAM' && !state.authName.trim()) {
    return 'QUERY_PARAM 需要填写 authName';
  }
  return null;
}

export interface McpFormProps {
  value: McpFormState;
  onChange: (next: McpFormState) => void;
  /** Write-only credential kept in component state only. */
  credential: string;
  onCredentialChange: (credential: string) => void;
  disabled?: boolean;
  readOnly?: boolean;
  isNew?: boolean;
}

const McpForm: GenieType.FC<McpFormProps> = memo(
  ({
    value,
    onChange,
    credential,
    onCredentialChange,
    disabled = false,
    readOnly = false,
    isNew = false,
  }) => {
    const locked = disabled || readOnly;
    const needsCredential =
      value.authType === 'BEARER_TOKEN' || value.authType === 'QUERY_PARAM';
    const clearCredentialRef = useRef(onCredentialChange);
    clearCredentialRef.current = onCredentialChange;

    // Clear credential from component state on unmount (never persist).
    useEffect(() => {
      return () => {
        clearCredentialRef.current('');
      };
    }, []);

    const patch = (partial: Partial<McpFormState>) => {
      onChange({
        ...value,
        ...partial
      });
    };

    return (
      <div className="flex flex-col gap-16" data-testid="mcp-form">
        <div>
          <Text strong>名称</Text>
          <Input
            className="mt-6"
            value={value.name}
            disabled={locked}
            onChange={(e) => patch({ name: e.target.value })}
            data-testid="mcp-name"
          />
        </div>
        <div>
          <Text strong>serverUrl</Text>
          <Input
            className="mt-6"
            value={value.serverUrl}
            disabled={locked}
            onChange={(e) => patch({ serverUrl: e.target.value })}
            placeholder="https://..."
            data-testid="mcp-server-url"
          />
        </div>
        <div>
          <Text strong>认证类型</Text>
          <div className="mt-6">
            <Radio.Group
              value={value.authType}
              disabled={locked}
              onChange={(e) => {
                const next = e.target.value as McpAuthType;
                patch({
                  authType: next,
                  authName: next === 'NONE' ? '' : value.authName,
                  clearCredential: false,
                });
                if (next === 'NONE') {
                  onCredentialChange('');
                }
              }}
              options={[
                {
                  label: 'NONE',
                  value: 'NONE'
                },
                {
                  label: 'BEARER_TOKEN',
                  value: 'BEARER_TOKEN'
                },
                {
                  label: 'QUERY_PARAM',
                  value: 'QUERY_PARAM'
                },
              ]}
              optionType="button"
              buttonStyle="solid"
              data-testid="mcp-auth-type"
            />
          </div>
        </div>
        {value.authType !== 'NONE' ? (
          <div>
            <Text strong>authName</Text>
            <Input
              className="mt-6"
              value={value.authName}
              disabled={locked}
              onChange={(e) => patch({ authName: e.target.value })}
              placeholder={
                value.authType === 'QUERY_PARAM' ? '查询参数名' : '可选'
              }
              data-testid="mcp-auth-name"
            />
          </div>
        ) : null}
        {needsCredential ? (
          <div>
            <Text strong>凭据（仅写入，不回显）</Text>
            <Input.Password
              className="mt-6"
              value={credential}
              disabled={locked}
              autoComplete="new-password"
              placeholder={
                value.credentialConfigured
                  ? '已配置 — 留空表示不修改'
                  : '输入凭据'
              }
              onChange={(e) => onCredentialChange(e.target.value)}
              data-testid="mcp-credential"
            />
            {!isNew && value.credentialConfigured ? (
              <div className="mt-8 flex items-center gap-8">
                <Tag color="blue" data-testid="mcp-credential-configured">
                  已配置凭据
                </Tag>
                <Checkbox
                  checked={value.clearCredential}
                  disabled={locked}
                  onChange={(e) => {
                    patch({ clearCredential: e.target.checked });
                    if (e.target.checked) {
                      onCredentialChange('');
                    }
                  }}
                  data-testid="mcp-clear-credential"
                >
                  清除已配置凭据
                </Checkbox>
              </div>
            ) : (
              <Text type="secondary" className="mt-6 block">
                详情仅显示是否已配置，永不展示凭据值
              </Text>
            )}
          </div>
        ) : null}
        {value.status ? (
          <div className="flex flex-wrap items-center gap-8">
            <Text type="secondary">状态</Text>
            <Tag>{value.status}</Tag>
            {value.version != null ? (
              <Text type="secondary">version {value.version}</Text>
            ) : null}
            {value.lastCheckStatus ? (
              <Text type="secondary">
                最近检测：{value.lastCheckStatus}
                {value.lastCheckCode ? ` (${value.lastCheckCode})` : ''}
              </Text>
            ) : null}
          </div>
        ) : null}
      </div>
    );
  },
);

McpForm.displayName = 'McpForm';

export default McpForm;

/** Test helper — credential must never be written to these stores. */
export function assertCredentialNotPersisted(credential: string): void {
  if (!credential) return;
  const haystacks = [
    window.location.href,
    window.localStorage ? JSON.stringify(window.localStorage) : '',
    window.sessionStorage ? JSON.stringify(window.sessionStorage) : '',
  ];
  for (const hay of haystacks) {
    if (hay.includes(credential)) {
      throw new Error('Credential leaked into URL or web storage');
    }
  }
}
