#!/usr/bin/env node
// 이미 저장된 아바타 머리를 팔레트 PNG(q95)로 다시 줄인다(S15P31A207-300). 새 머리는 HEAD_PNG_COMPRESSION=palette로 저장할 때 줄어든다.
//
// 대상(사용자 폴더 <data>/users/<sha256>/ 아래, user-storage.mjs·head-jobs.mjs 저장 구조)
//   heads/<머리 ID>-{face,eyes,hair,right-face,right-eyes,right-hair}.png   보여 주는 6레이어 파일
//   jobs/<작업 ID>/result.json 의 faceImage·eyesImage·hairImage·rightFaceImage·rightEyesImage·rightHairImage   머리 조회 응답 원본
// 합성(<머리 ID>.png)·원본(-original)·우측 전체(-right)·frontImage·rightImage·mobile.renderFaceImage는 건드리지 않는다.
//
// 이미 팔레트 PNG면 건너뛰고(멱등), 1024 알파 PNG가 아니거나 더 작아지지 않으면 그대로 둔다(head-png.mjs compressHeadPng).
// 파일마다 임시 파일에 쓴 뒤 rename으로 바꾼다. 읽은 뒤 서버가 그 파일을 다시 썼으면(크기·수정 시각이 다르면) 바꾸지 않는다.
// 기록에는 개수와 바이트만 남긴다(사용자 ID·폴더 이름·머리 ID를 찍지 않는다).
//
// 사용: node scripts/recompress-heads.mjs [--data <dir>] [--dry-run] [--limit <n>]
//   --data     avatar 데이터 폴더(users/가 든 곳). 기본은 이 스크립트 옆 ../data(컨테이너에서는 /app/data)
//   --dry-run  바꾸지 않고 대상 파일 수와 예상 감소량만 알린다(메모리에서 실제로 압축해 잰다)
//   --limit    앞에서 n개 파일만 본다(머리 PNG 하나, result.json 하나가 각각 1개)
// 끝 코드: 0 정상, 1 실패한 파일이 있음, 2 인자·데이터 폴더 오류
import {readdir, readFile, writeFile, rename, stat, unlink} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {HEAD_PNG_LAYER_KEYS, compressHeadPng} from '../head-png.mjs';

const DIR = path.dirname(fileURLToPath(import.meta.url));
const TAG = '[recompress-heads]';
const DATA_URL = 'data:image/png;base64,';
const USER_DIR = /^[a-f0-9]{64}$/;
const JOB_DIR = /^[a-f0-9-]{36}$/;
const HEAD_LAYER_FILE = /^[a-f0-9-]{36}-(?:right-)?(?:face|eyes|hair)\.png$/;
const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const PROGRESS_EVERY = 100;
export const RESULT_IMAGE_FIELDS = Object.freeze(HEAD_PNG_LAYER_KEYS.map(key => key + 'Image'));

/** IHDR 색 형식이 3(팔레트)인 PNG인가. 이미 줄인 레이어를 다시 양자화하지 않는다 */
export function isPalettePng(buffer) {
  return Buffer.isBuffer(buffer) && buffer.length >= 26 && buffer.subarray(0, 8).equals(PNG_SIGNATURE)
    && buffer.toString('latin1', 12, 16) === 'IHDR' && buffer[25] === 3;
}

const usage = message => Object.assign(new Error(`인자 오류: ${message}`), {code: 'USAGE'});

export function parseArgs(argv) {
  const options = {data: path.resolve(DIR, '..', 'data'), dryRun: false, limit: Infinity, help: false};
  for (let i = 0; i < argv.length; i++) {
    const [flag, inline] = argv[i].startsWith('--') && argv[i].includes('=') ? [argv[i].slice(0, argv[i].indexOf('=')), argv[i].slice(argv[i].indexOf('=') + 1)] : [argv[i], undefined];
    const value = () => {
      const next = inline ?? argv[++i];
      if (next === undefined || next === '') throw usage(`${flag} 뒤에 값이 필요하다`);
      return next;
    };
    if (flag === '--dry-run') options.dryRun = true;
    else if (flag === '--help' || flag === '-h') options.help = true;
    else if (flag === '--data') options.data = path.resolve(value());
    else if (flag === '--limit') {
      const raw = value(), limit = Number(raw);
      if (!/^\d+$/.test(raw) || !Number.isSafeInteger(limit) || limit < 1) throw usage('--limit은 1 이상의 정수다');
      options.limit = limit;
    } else throw usage(`모르는 인자 ${flag}`);
  }
  return options;
}

async function directories(dir, pattern) {
  try {
    return (await readdir(dir, {withFileTypes: true})).filter(entry => entry.isDirectory() && pattern.test(entry.name)).map(entry => entry.name).sort();
  } catch (error) {
    if (error.code === 'ENOENT') return [];
    throw error;
  }
}

/** 대상 파일 목록. 심볼릭 링크는 따라가지 않는다(Dirent는 링크 자체를 본다) */
async function listTargets(data) {
  const users = path.join(data, 'users'), targets = [];
  for (const user of await directories(users, USER_DIR)) {
    const heads = path.join(users, user, 'heads');
    try {
      const files = (await readdir(heads, {withFileTypes: true})).filter(entry => entry.isFile() && HEAD_LAYER_FILE.test(entry.name)).map(entry => entry.name).sort();
      for (const name of files) targets.push({kind: 'png', file: path.join(heads, name)});
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
    const jobs = path.join(users, user, 'jobs');
    for (const job of await directories(jobs, JOB_DIR)) {
      const file = path.join(jobs, job, 'result.json');
      try {
        if ((await stat(file)).isFile()) targets.push({kind: 'result', file});
      } catch (error) {
        if (error.code !== 'ENOENT') throw error;
      }
    }
  }
  return targets;
}

/** 레이어 하나를 줄인다. 줄였으면 새 버퍼, 아니면 null */
async function shrinkLayer(buffer, compress, totals) {
  totals.layers++;
  totals.layerBytesBefore += buffer.length;
  if (isPalettePng(buffer)) {
    totals.palette++;
    totals.layerBytesAfter += buffer.length;
    return null;
  }
  const out = await compress(buffer);
  if (!Buffer.isBuffer(out) || out === buffer || out.length >= buffer.length) {
    totals.kept++;
    totals.layerBytesAfter += buffer.length;
    return null;
  }
  totals.shrunk++;
  totals.layerBytesAfter += out.length;
  return out;
}

/** 임시 파일에 쓰고 rename한다. 읽은 뒤 원본이 바뀌었으면 쓰지 않고 false */
async function replaceFile(file, content, before) {
  const temporary = `${file}.${randomUUID()}.tmp`;
  await writeFile(temporary, content, {mode: before.mode & 0o777});
  try {
    const now = await stat(file);
    if (now.size !== before.size || now.mtimeMs !== before.mtimeMs) {
      await unlink(temporary);
      return false;
    }
    // Windows는 방금 읽은 파일을 잠깐 잡고 있을 수 있다(user-storage.mjs atomicJson과 같은 재시도)
    for (let attempt = 0; ; attempt++) {
      try {
        await rename(temporary, file);
        return true;
      } catch (error) {
        if (attempt >= 9 || !['EPERM', 'EBUSY', 'EACCES'].includes(error.code)) throw error;
        await new Promise(resolve => setTimeout(resolve, 20 * (attempt + 1)));
      }
    }
  } catch (error) {
    await unlink(temporary).catch(() => {});
    throw error;
  }
}

async function nextPng(file, compress, totals) {
  const buffer = await readFile(file);
  const out = await shrinkLayer(buffer, compress, totals);
  return {before: buffer.length, content: out};
}

async function nextResult(file, compress, totals) {
  const text = await readFile(file, 'utf8');
  const json = JSON.parse(text), updated = {...json};
  let changed = false;
  for (const field of RESULT_IMAGE_FIELDS) {
    const value = json[field];
    if (typeof value !== 'string' || !value.startsWith(DATA_URL)) continue;
    const out = await shrinkLayer(Buffer.from(value.slice(DATA_URL.length), 'base64'), compress, totals);
    if (out) {
      updated[field] = DATA_URL + out.toString('base64');
      changed = true;
    }
  }
  if (!changed) return {before: Buffer.byteLength(text), content: null};
  // atomicJson과 같은 형식(들여쓰기 2)으로 다시 쓴다. 키 순서와 다른 필드 값은 그대로다
  const content = Buffer.from(JSON.stringify(updated, null, /^\{\r?\n/.test(text) ? 2 : 0));
  return {before: Buffer.byteLength(text), content};
}

const percent = (before, after) => (before ? ((after - before) / before * 100).toFixed(1) : '0.0') + '%';
const megabytes = bytes => (bytes / 1048576).toFixed(1) + 'MB';

/**
 * 저장된 머리를 다시 줄이고 개수·바이트를 돌려준다.
 * compress는 시험에서 바꿔 끼운다. 기본은 head-png.mjs 팔레트(q95)로 환경 변수와 관계없다.
 */
export async function recompressHeads({data, dryRun = false, limit = Infinity, log = console.log, compress = buffer => compressHeadPng(buffer, {mode: 'palette'})} = {}) {
  const all = await listTargets(data), targets = all.slice(0, limit);
  const totals = {
    files: targets.length, layers: 0, shrunk: 0, palette: 0, kept: 0, failed: 0, raced: 0,
    changedFiles: 0, plannedFiles: 0, fileBytesBefore: 0, fileBytesAfter: 0, layerBytesBefore: 0, layerBytesAfter: 0,
  };
  const pngs = targets.filter(target => target.kind === 'png').length;
  log(`${TAG} 대상 파일 ${targets.length}개(머리 PNG ${pngs}개, 작업 결과 ${targets.length - pngs}개${all.length > targets.length ? `, 전체 ${all.length}개 중 --limit` : ''})${dryRun ? ' · 미리 보기: 바꾸지 않는다' : ''}`);
  for (const [index, target] of targets.entries()) {
    try {
      const before = await stat(target.file);
      const {before: size, content} = await (target.kind === 'png' ? nextPng : nextResult)(target.file, compress, totals);
      totals.fileBytesBefore += size;
      if (!content) {
        totals.fileBytesAfter += size;
      } else if (dryRun) {
        totals.plannedFiles++;
        totals.fileBytesAfter += content.length;
      } else if (await replaceFile(target.file, content, before)) {
        totals.changedFiles++;
        totals.fileBytesAfter += content.length;
      } else {
        totals.raced++;
        totals.fileBytesAfter += size;
      }
    } catch (error) {
      totals.failed++;
      log(`${TAG} 파일 하나를 처리하지 못했다(${error.code || error.name || 'error'}). 그대로 두고 계속한다`);
    }
    if ((index + 1) % PROGRESS_EVERY === 0) log(`${TAG} 진행 ${index + 1}/${targets.length}`);
  }
  log(`${TAG} 레이어 ${totals.layers}개: 줄임 ${totals.shrunk} · 이미 팔레트 ${totals.palette} · 그대로 ${totals.kept}`
    + ` (레이어 ${megabytes(totals.layerBytesBefore)} → ${megabytes(totals.layerBytesAfter)}, ${percent(totals.layerBytesBefore, totals.layerBytesAfter)})`);
  log(`${TAG} 파일 ${totals.files}개 ${megabytes(totals.fileBytesBefore)} → ${megabytes(totals.fileBytesAfter)}(${percent(totals.fileBytesBefore, totals.fileBytesAfter)}`
    + `${dryRun ? ', 예상' : ''}) · ${dryRun ? `바꿀 파일 ${totals.plannedFiles}개` : `바꾼 파일 ${totals.changedFiles}개`}`
    + ` · 실행 중 바뀌어 건너뜀 ${totals.raced} · 실패 ${totals.failed}`);
  return totals;
}

async function main(argv) {
  let options;
  try {
    options = parseArgs(argv);
  } catch (error) {
    console.error(`${TAG} ${error.message}`);
    return 2;
  }
  if (options.help) {
    console.log('node scripts/recompress-heads.mjs [--data <dir>] [--dry-run] [--limit <n>]');
    return 0;
  }
  try {
    if (!(await stat(options.data)).isDirectory()) throw Object.assign(new Error('not a directory'), {code: 'ENOTDIR'});
  } catch (error) {
    console.error(`${TAG} 데이터 폴더를 열 수 없다(${error.code || error.message}): ${options.data}`);
    return 2;
  }
  const summary = await recompressHeads(options);
  return summary.failed ? 1 : 0;
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
  process.exitCode = await main(process.argv.slice(2));
}
