import test from 'node:test';
import assert from 'node:assert/strict';
import { adjustPixels, frameBounds } from '../public/portrait-editor.mjs';

test('the preview covers the print slot and offsets scale as fractions of that slot', () => {
  const edits = { zoom: 1.2, offsetX: .25, offsetY: -.125 };
  const preview = frameBounds(400, 400, 120, 160, edits);
  const print = frameBounds(400, 400, 240, 320, edits);
  assert.deepEqual(preview, { x: -6, y: -36, width: 192, height: 192 });
  for (const key of Object.keys(preview)) assert.equal(print[key], preview[key] * 2);
});

test('color adjustments clip channels, preserve transparency and match desktop rounding', () => {
  const photo = Uint8ClampedArray.from([220, 40, 40, 113, 250, 250, 250, 255]);
  adjustPixels(photo, { brightness: 20, contrast: 0, saturation: -100 });
  assert.deepEqual([...photo], [104, 104, 104, 113, 255, 255, 255, 255]);
});
