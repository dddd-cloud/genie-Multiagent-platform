import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { strFromU8, unzipSync } from 'fflate';
import { loadPyodide } from 'pyodide';
import { beforeAll, describe, expect, it } from 'vitest';

let pyodide: Awaited<ReturnType<typeof loadPyodide>>;

async function runSkill(
  packageName: string,
  functionName: string,
  args: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const entrypoint = resolve(
    process.cwd(),
    '..',
    'genie-backend',
    'src',
    'main',
    'resources',
    'marketplace',
    'packages',
    packageName,
    'scripts',
    'entrypoint.py',
  );
  await pyodide.runPythonAsync(readFileSync(entrypoint, 'utf8'));
  pyodide.globals.set('_marketplace_args_json', JSON.stringify(args));
  const output = pyodide.runPython(
    `json.dumps(${functionName}(**json.loads(_marketplace_args_json)))`,
  );
  pyodide.globals.delete('_marketplace_args_json');
  return JSON.parse(String(output)) as Record<string, unknown>;
}

describe('MarketplacePythonSkillsTest', () => {
  beforeAll(async () => {
    pyodide = await loadPyodide({indexURL: `${resolve(process.cwd(), 'node_modules', 'pyodide')}/`,});
  }, 30_000);

  it('executes the JSON workbench entrypoint in Pyodide', async () => {
    const result = await runSkill('json-workbench', 'inspect_json', {
      json_text: '{"items":[{"id":1},{"id":2}]}',
      path: 'items.1.id',
      flatten: true,
    });

    expect(result).toMatchObject({
      ok: true,
      selectedPath: 'items.1.id',
      selected: 2,
    });
  });

  it('executes the Markdown-to-HTML entrypoint in Pyodide', async () => {
    const result = await runSkill('markdown-html', 'render_markdown', {
      markdown: '# Runtime PASS\n- **Pyodide**',
      title: 'Skill Runtime Report',
    });

    expect(result.filename).toBe('markdown-preview.html');
    expect(result.mimeType).toBe('text/html');
    expect(result.html).toContain('<h1>Runtime PASS</h1>');
  });

  it('executes the CSV-to-SVG entrypoint in Pyodide', async () => {
    const result = await runSkill('csv-chart-svg', 'create_bar_chart', {
      csv_text: 'name,value\nAlpha,10\nBeta,20',
      label_column: 'name',
      value_column: 'value',
      title: 'Browser chart',
    });

    expect(result).toMatchObject({
      ok: true,
      filename: 'chart.svg',
      mimeType: 'image/svg+xml',
      itemCount: 2,
      minimum: 10,
      maximum: 20,
    });
    expect(result.content).toContain('<svg');
  });

  it('executes the ZIP entrypoint and returns a valid archive in Pyodide', async () => {
    const result = await runSkill('file-archive', 'create_zip', {
      files: [
        {
          name: 'a.txt',
          contentText: 'hello'
        },
        {
          name: 'data/b.json',
          contentText: '{"ok":true}'
        },
      ],
      filename: 'bundle.zip',
    });
    const archive = unzipSync(
      new Uint8Array(Buffer.from(String(result.contentBase64), 'base64')),
    );

    expect(result).toMatchObject({
      ok: true,
      filename: 'bundle.zip',
      mimeType: 'application/zip',
      fileCount: 2,
    });
    expect(Object.keys(archive).sort()).toEqual(['a.txt', 'data/b.json']);
    expect(strFromU8(archive['a.txt'])).toBe('hello');
  });
});
