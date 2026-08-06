import { memo, useState } from 'react';
import { Alert, Button, Spin, Typography } from 'antd';
import { previewPrompt } from '@/services/phase2/agents';
import {
  formStateToPreviewRequest,
  parsePromptConfigText,
  type AgentFormState,
} from './agentFormModel';
import { phase2ErrorMessage } from '../phase2UiError';

const { Text, Paragraph } = Typography;

export interface PromptPreviewPanelProps {
  formState: AgentFormState;
}

const PromptPreviewPanel: GenieType.FC<PromptPreviewPanelProps> = memo(
  ({ formState }) => {
    const [loading, setLoading] = useState(false);
    const [preview, setPreview] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    const handlePreview = async () => {
      if (formState.promptMode === 'STRUCTURED') {
        const parsed = parsePromptConfigText(
          formState.promptConfigText,
          'STRUCTURED',
        );
        if (!parsed.ok) {
          setError(parsed.error);
          setPreview(null);
          return;
        }
      }
      setLoading(true);
      setError(null);
      try {
        const result = await previewPrompt(formStateToPreviewRequest(formState));
        setPreview(result?.preview ?? '');
      } catch (err: unknown) {
        setPreview(null);
        setError(phase2ErrorMessage(err));
      } finally {
        setLoading(false);
      }
    };

    return (
      <div
        className="rounded-[10px] border border-border p-16 bg-[#FAFAFA]"
        data-testid="prompt-preview-panel"
      >
        <div className="flex items-center justify-between gap-8 mb-12">
          <Text strong>Prompt 预览</Text>
          <Button size="small" loading={loading} onClick={() => void handlePreview()}>
            生成预览
          </Button>
        </div>
        {error ? <Alert type="error" showIcon message={error} className="mb-12" /> : null}
        <Spin spinning={loading}>
          {preview != null ? (
            <Paragraph
              className="!mb-0 whitespace-pre-wrap text-[13px] text-text-primary"
              data-testid="prompt-preview-text"
            >
              {preview || '（空预览）'}
            </Paragraph>
          ) : (
            <Text type="secondary">点击「生成预览」查看服务端编译结果</Text>
          )}
        </Spin>
      </div>
    );
  },
);

PromptPreviewPanel.displayName = 'PromptPreviewPanel';

export default PromptPreviewPanel;
