import assert from 'node:assert/strict';
import test from 'node:test';
import sharp from 'sharp';
import {splitMasterPixels} from '../master-layer-split.mjs';

const size = 1024;

async function rgba(data) {
  return sharp(data, {raw: {width: size, height: size, channels: 4}}).png().toBuffer();
}

function pixel(buffer, x, y, color) {
  const index = (y * size + x) * 4;
  buffer[index] = color[0];
  buffer[index + 1] = color[1];
  buffer[index + 2] = color[2];
  buffer[index + 3] = color[3];
}

test('master 픽셀을 그대로 유지하고 레이어마다 이동·확대하지 않는다', async () => {
  const master = new Uint8Array(size * size * 4);
  const face = new Uint8Array(master.length);
  const eyes = new Uint8Array(master.length);
  const hair = new Uint8Array(master.length);
  pixel(master, 100, 100, [10, 20, 30, 255]);
  pixel(master, 200, 200, [40, 50, 60, 255]);
  pixel(master, 300, 300, [70, 80, 90, 255]);
  pixel(face, 100, 100, [255, 255, 255, 255]);
  pixel(eyes, 200, 200, [255, 255, 255, 255]);
  pixel(hair, 300, 300, [255, 255, 255, 255]);

  const result = await splitMasterPixels(await rgba(master), {
    face: await rgba(face), eyes: await rgba(eyes), hair: await rgba(hair),
  });
  const decoded = await Promise.all(Object.values(result.parts).map((part) => sharp(part).raw().toBuffer()));
  assert.deepEqual([...decoded[0].subarray(100 * size * 4 + 100 * 4, 100 * size * 4 + 100 * 4 + 4)], [10, 20, 30, 255]);
  assert.deepEqual([...decoded[1].subarray(200 * size * 4 + 200 * 4, 200 * size * 4 + 200 * 4 + 4)], [40, 50, 60, 255]);
  assert.deepEqual([...decoded[2].subarray(300 * size * 4 + 300 * 4, 300 * size * 4 + 300 * 4 + 4)], [70, 80, 90, 255]);
  assert.equal(result.metrics.method, 'master-pixel-mask');
  assert.deepEqual(result.metrics.transforms.face, {scale: 1, offsetX: 0, offsetY: 0});
});
