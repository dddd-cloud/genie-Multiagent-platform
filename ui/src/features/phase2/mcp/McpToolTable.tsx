import { memo, useState } from 'react';
import { Switch, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Phase2McpToolResponse } from '@/contracts/phase2';
import { setMcpToolEnabled } from '@/services/phase2/mcp';
import {
  isVersionConflict,
  phase2ErrorMessage,
} from '../phase2UiError';

export interface McpToolTableProps {
  serverId: string;
  tools: Phase2McpToolResponse[];
  onToolsChange: (tools: Phase2McpToolResponse[]) => void;
  disabled?: boolean;
}

const McpToolTable: GenieType.FC<McpToolTableProps> = memo(
  ({ serverId, tools, onToolsChange, disabled = false }) => {
    const [busyId, setBusyId] = useState<string | null>(null);

    const columns: ColumnsType<Phase2McpToolResponse> = [
      {
        title: '工具名',
        dataIndex: 'toolName',
        key: 'toolName',
      },
      {
        title: '运行时名',
        dataIndex: 'runtimeName',
        key: 'runtimeName',
      },
      {
        title: '描述',
        dataIndex: 'description',
        key: 'description',
        ellipsis: true,
      },
      {
        title: '可用',
        dataIndex: 'available',
        key: 'available',
        width: 90,
        render: (available: boolean) => (
          <Tag color={available ? 'green' : 'default'}>
            {available ? '是' : '否'}
          </Tag>
        ),
      },
      {
        title: '启用',
        dataIndex: 'enabled',
        key: 'enabled',
        width: 100,
        render: (enabled: boolean, row) => (
          <Switch
            checked={enabled}
            disabled={disabled || busyId === row.id}
            loading={busyId === row.id}
            onChange={(next) => {
              void (async () => {
                setBusyId(row.id);
                try {
                  const updated = await setMcpToolEnabled(serverId, row.id, {
                    enabled: next,
                    version: row.version,
                  });
                  if (updated) {
                    onToolsChange(
                      tools.map((t) => (t.id === updated.id ? updated : t)),
                    );
                  }
                } catch (err: unknown) {
                  if (isVersionConflict(err)) {
                    message.error(phase2ErrorMessage(err));
                  } else {
                    message.error(phase2ErrorMessage(err));
                  }
                } finally {
                  setBusyId(null);
                }
              })();
            }}
            data-testid={`mcp-tool-enabled-${row.id}`}
          />
        ),
      },
    ];

    return (
      <Table
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={tools}
        pagination={false}
        locale={{ emptyText: '暂无工具，请先刷新工具列表' }}
        data-testid="mcp-tool-table"
      />
    );
  },
);

McpToolTable.displayName = 'McpToolTable';

export default McpToolTable;
