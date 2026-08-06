import { memo, useState } from 'react';
import { Alert, Button, Input, Modal, Space, Typography } from 'antd';
import type { AgentTestResponse } from '@/services/phase2/internalTypes';
import { testAgent } from '@/services/phase2/agents';
import { phase2ErrorMessage } from '../phase2UiError';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

export interface AgentTestModalProps {
  open: boolean;
  agentId: string;
  agentName: string;
  onClose: () => void;
}

const AgentTestModal: GenieType.FC<AgentTestModalProps> = memo(
  ({ open, agentId, agentName, onClose }) => {
    const [query, setQuery] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [result, setResult] = useState<AgentTestResponse | null>(null);

    const reset = () => {
      setQuery('');
      setError(null);
      setResult(null);
      setLoading(false);
    };

    const handleClose = () => {
      reset();
      onClose();
    };

    const handleTest = async () => {
      if (!query.trim()) {
        setError('请输入测试问题');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await testAgent(agentId, { query: query.trim() });
        setResult(data);
      } catch (err: unknown) {
        setResult(null);
        setError(phase2ErrorMessage(err));
      } finally {
        setLoading(false);
      }
    };

    return (
      <Modal
        open={open}
        title={`测试 Agent：${agentName}`}
        onCancel={handleClose}
        footer={null}
        destroyOnClose
        width={560}
        data-testid="agent-test-modal"
      >
        <Space direction="vertical" size={12} className="w-full">
          <Text type="secondary">仅 ONLINE Agent 可测试；不返回 Prompt/凭据/Tool 参数。</Text>
          <TextArea
            rows={3}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="输入测试问题"
            data-testid="agent-test-query"
          />
          <Button
            type="primary"
            loading={loading}
            onClick={() => void handleTest()}
            data-testid="agent-test-submit"
          >
            运行测试
          </Button>
          {error ? <Alert type="error" showIcon message={error} /> : null}
          {result ? (
            <div
              className="rounded-[10px] border border-border p-12 bg-[#FAFAFA]"
              data-testid="agent-test-result"
            >
              <Paragraph className="!mb-8">
                <Text strong>状态：</Text>
                {result.result?.status ?? '—'}
              </Paragraph>
              {result.result?.output ? (
                <Paragraph className="!mb-8 whitespace-pre-wrap">
                  <Text strong>输出：</Text>
                  {result.result.output}
                </Paragraph>
              ) : null}
              {result.result?.errorCode ? (
                <Paragraph className="!mb-8">
                  <Text strong>错误码：</Text>
                  {result.result.errorCode}
                  {result.result.retryable ? '（可重试）' : ''}
                </Paragraph>
              ) : null}
              {result.model ? (
                <Paragraph className="!mb-8">
                  <Text strong>模型：</Text>
                  {result.model}
                </Paragraph>
              ) : null}
              <Paragraph className="!mb-8">
                <Text strong>耗时：</Text>
                {result.elapsedMillis} ms
              </Paragraph>
              <Paragraph className="!mb-8">
                <Text strong>进度事件：</Text>
                {result.progressEventCount}
              </Paragraph>
              {result.capabilityKeys?.length ? (
                <Paragraph className="!mb-8">
                  <Text strong>能力：</Text>
                  {result.capabilityKeys.join(', ')}
                </Paragraph>
              ) : null}
              {result.skillSummary?.length ? (
                <Paragraph className="!mb-0">
                  <Text strong>Skill：</Text>
                  {result.skillSummary.join(', ')}
                </Paragraph>
              ) : null}
            </div>
          ) : null}
        </Space>
      </Modal>
    );
  },
);

AgentTestModal.displayName = 'AgentTestModal';

export default AgentTestModal;
