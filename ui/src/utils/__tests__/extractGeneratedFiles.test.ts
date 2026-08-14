import { describe, expect, it } from 'vitest';
import { extractGeneratedFiles } from '@/utils/chat';

describe('extractGeneratedFiles', () => {
  it('maps resultMap.fileList to downloadable attachments', () => {
    const files = extractGeneratedFiles({
      fileList: [
        {
          fileName: 'brand.html',
          ossUrl: 'http://127.0.0.1:1601/v1/file_tool/download/r/brand.html',
          domainUrl: 'http://127.0.0.1:1601/v1/file_tool/preview/r/brand.html',
          fileSize: 128,
        },
      ],
    });
    expect(files).toEqual([
      {
        name: 'brand.html',
        url: 'http://127.0.0.1:1601/v1/file_tool/preview/r/brand.html',
        type: 'html',
        size: 128,
      },
    ]);
  });

  it('rewrites None-prefixed tool urls so the browser can open them', () => {
    const files = extractGeneratedFiles({
      fileList: [
        {
          fileName: '自我介绍.html',
          ossUrl: 'None/download/step-1/自我介绍.html',
          domainUrl: 'None/preview/step-1/自我介绍.html',
          fileSize: 1651,
        },
      ],
    });
    expect(files[0].url).toBe(
      'http://127.0.0.1:1601/v1/file_tool/preview/step-1/自我介绍.html',
    );
  });

  it('returns empty when fileList is missing', () => {
    expect(extractGeneratedFiles({})).toEqual([]);
    expect(extractGeneratedFiles(null)).toEqual([]);
  });
});
