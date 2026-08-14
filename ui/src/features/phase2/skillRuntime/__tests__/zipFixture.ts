import { BROWSER_SKILL_EXECUTION_MANIFEST_PATH } from '@/contracts';

/** Minimal ZIP (STORE) builder — avoids fflate zipSync Uint8Array realm issues in jsdom. */

function crc32(data: Uint8Array): number {
  let c = ~0;
  for (let i = 0; i < data.length; i += 1) {
    c ^= data[i]!;
    for (let k = 0; k < 8; k += 1) {
      c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
    }
  }
  return ~c >>> 0;
}

export function buildStoreZip(files: Record<string, string>): ArrayBuffer {
  const enc = new TextEncoder();
  const locals: Uint8Array[] = [];
  const centrals: Uint8Array[] = [];
  let offset = 0;

  for (const [name, text] of Object.entries(files)) {
    const nameBytes = enc.encode(name);
    const data = enc.encode(text);
    const crc = crc32(data);

    const local = new Uint8Array(30 + nameBytes.length + data.length);
    const lv = new DataView(local.buffer);
    lv.setUint32(0, 0x04034b50, true);
    lv.setUint16(8, 0, true);
    lv.setUint32(14, crc, true);
    lv.setUint32(18, data.length, true);
    lv.setUint32(22, data.length, true);
    lv.setUint16(26, nameBytes.length, true);
    local.set(nameBytes, 30);
    local.set(data, 30 + nameBytes.length);

    const central = new Uint8Array(46 + nameBytes.length);
    const cv = new DataView(central.buffer);
    cv.setUint32(0, 0x02014b50, true);
    cv.setUint16(10, 0, true);
    cv.setUint32(16, crc, true);
    cv.setUint32(20, data.length, true);
    cv.setUint32(24, data.length, true);
    cv.setUint16(28, nameBytes.length, true);
    cv.setUint32(42, offset, true);
    central.set(nameBytes, 46);

    locals.push(local);
    centrals.push(central);
    offset += local.length;
  }

  const centralOffset = offset;
  let centralSize = 0;
  for (const c of centrals) centralSize += c.length;

  const end = new Uint8Array(22);
  const ev = new DataView(end.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(8, locals.length, true);
  ev.setUint16(10, locals.length, true);
  ev.setUint32(12, centralSize, true);
  ev.setUint32(16, centralOffset, true);

  const out = new Uint8Array(offset + centralSize + 22);
  let p = 0;
  for (const l of locals) {
    out.set(l, p);
    p += l.length;
  }
  for (const c of centrals) {
    out.set(c, p);
    p += c.length;
  }
  out.set(end, p);
  return out.buffer;
}

/** Valid skill execution ZIP with manifest + script for runner tests. */
export function buildValidExecutionZip(options: {
  executionId: string;
  entrypointName?: string;
  scriptRelativePath?: string;
  scriptSource?: string;
  packages?: string[];
  inputJson?: string;
}): ArrayBuffer {
  const entrypointName = options.entrypointName ?? 'main';
  const scriptRelativePath = options.scriptRelativePath ?? 'scripts/run.py';
  const scriptSource =
    options.scriptSource ??
    'def main(input):\n    return {"ok": True, "input": input}\n';
  const packages = options.packages ?? [];
  const inputJson = options.inputJson ?? '{}';
  const manifest = {
    schemaVersion: 1,
    executionId: options.executionId,
    entrypointName,
    scriptRelativePath,
    packages,
    inputJson,
  };
  return buildStoreZip({
    [BROWSER_SKILL_EXECUTION_MANIFEST_PATH]: `${JSON.stringify(manifest, null, 2)}\n`,
    [scriptRelativePath]: scriptSource,
  });
}
