const frameKeys = ['zoom', 'offsetX', 'offsetY'];
const toneKeys = ['brightness', 'contrast', 'saturation'];
const defaults = { zoom: 1, offsetX: 0, offsetY: 0, brightness: 0, contrast: 0, saturation: 0 };

export function frameBounds(sourceWidth, sourceHeight, width, height, { zoom, offsetX, offsetY }) {
  const scale = Math.max(width / sourceWidth, height / sourceHeight) * zoom;
  const drawnWidth = sourceWidth * scale;
  const drawnHeight = sourceHeight * scale;
  return {
    x: width * (.5 + offsetX) - drawnWidth / 2,
    y: height * (.5 + offsetY) - drawnHeight / 2,
    width: drawnWidth,
    height: drawnHeight,
  };
}

export function adjustPixels(data, { brightness, contrast, saturation }) {
  const gain = Math.pow(2, contrast / 100);
  const color = 1 + saturation / 100;
  const lift = brightness * 1.275;
  for (let i = 0; i < data.length; i += 4) {
    const luminance = .2126 * data[i] + .7152 * data[i + 1] + .0722 * data[i + 2];
    for (let channel = i; channel < i + 3; channel++) {
      data[channel] = Math.round((luminance + (data[channel] - luminance) * color - 127.5) * gain + 127.5 + lift);
    }
  }
}

function resize(bitmap, maxSize) {
  const scale = Math.min(1, maxSize / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(bitmap.width * scale));
  canvas.height = Math.max(1, Math.round(bitmap.height * scale));
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  return canvas;
}

async function makePicture(file, format) {
  const bitmap = await createImageBitmap(file);
  try {
    for (const size of [1400, 1100, 850, 640]) {
      const canvas = resize(bitmap, size);
      const blob = await new Promise(resolve => canvas.toBlob(resolve, format, .91));
      if (!blob || blob.size > 2 * 1024 * 1024) continue;
      const image = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result.split(',')[1]);
        reader.onerror = reject;
        reader.readAsDataURL(blob);
      });
      const decoded = await createImageBitmap(blob);
      try {
        return { image, preview: resize(decoded, 560), width: decoded.width, height: decoded.height };
      } finally {
        decoded.close();
      }
    }
    throw new Error('Choose a smaller photo.');
  } finally {
    bitmap.close();
  }
}

export function createPortraitEditor(removeBackground) {
  const field = id => document.getElementById(id);
  const picker = field('photo');
  const editor = field('portrait-editor');
  const canvas = field('portrait');
  const status = field('photo-status');
  const remove = field('remove-background');
  const restore = field('restore-background');
  const controls = Object.fromEntries([...frameKeys, ...toneKeys].map(key => [key, field(key)]));
  let slotKey, slot, original, cutout, image, drag;
  let state = { ...defaults };
  let pending = false;
  let version = 0;
  let previewFrame = 0;
  let previousTone, previousImage, treated;

  function render() {
    if (!image || !slot) return;
    cancelAnimationFrame(previewFrame);
    previewFrame = requestAnimationFrame(() => {
      if (!image || !slot) return;
      const tone = toneKeys.map(key => state[key]).join(',');
      if (tone !== previousTone || image !== previousImage) {
        treated = resize(image.preview, 560);
        if (tone !== '0,0,0') {
          const context = treated.getContext('2d');
          const pixels = context.getImageData(0, 0, treated.width, treated.height);
          adjustPixels(pixels.data, state);
          context.putImageData(pixels, 0, 0);
        }
        previousTone = tone;
        previousImage = image;
      }
      const width = 300;
      const height = Math.max(1, Math.round(width * slot.height / slot.width));
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
      const context = canvas.getContext('2d');
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, width, height);
      const bounds = frameBounds(image.width, image.height, width, height, state);
      context.drawImage(treated, bounds.x, bounds.y, bounds.width, bounds.height);
    });
  }

  function syncButtons() {
    editor.hidden = !image;
    remove.disabled = pending || !image || image === cutout;
    restore.disabled = pending || !cutout || image === original;
  }

  function resetEdits() {
    state = { ...defaults };
    for (const [key, input] of Object.entries(controls)) input.value = state[key];
    render();
  }

  function cancel() {
    version++;
    pending = false;
    if (!image) picker.value = '';
    syncButtons();
  }

  function reset() {
    original = cutout = image = undefined;
    previousImage = treated = undefined;
    picker.value = '';
    status.textContent = '';
    cancel();
    resetEdits();
  }

  picker.addEventListener('change', async () => {
    const file = picker.files[0];
    original = cutout = image = undefined;
    previousImage = treated = undefined;
    resetEdits();
    const current = ++version;
    pending = Boolean(file);
    syncButtons();
    if (!file) {
      status.textContent = '';
      return;
    }
    status.textContent = 'Preparing photo…';
    try {
      if (file.size > 20 * 1024 * 1024) throw new Error('Choose a photo under 20 MB.');
      const picture = await makePicture(file, file.type === 'image/png' ? 'image/png' : 'image/jpeg');
      if (current !== version) return;
      original = image = picture;
      status.textContent = '';
      render();
    } catch (error) {
      if (current === version) {
        status.textContent = error.message || 'Choose a readable photo.';
        picker.value = '';
      }
    } finally {
      if (current === version) {
        pending = false;
        syncButtons();
      }
    }
  });

  remove.addEventListener('click', async () => {
    if (pending || !original) return;
    if (cutout) {
      image = cutout;
      syncButtons();
      render();
      return;
    }
    const current = version;
    pending = true;
    syncButtons();
    status.textContent = 'Removing background…';
    try {
      const result = await removeBackground(original.image);
      if (current !== version) return;
      const bytes = Uint8Array.from(atob(result.image), character => character.charCodeAt(0));
      const picture = await makePicture(new Blob([bytes], { type: 'image/png' }), 'image/png');
      if (current !== version) return;
      cutout = image = picture;
      status.textContent = 'Background removed.';
      render();
    } catch (error) {
      if (current === version) status.textContent = 'Background removal did not finish. You can still use the original photo. ' + (error.message || '');
    } finally {
      if (current === version) {
        pending = false;
        syncButtons();
      }
    }
  });

  restore.addEventListener('click', () => {
    image = original;
    status.textContent = 'Original background restored.';
    syncButtons();
    render();
  });
  field('reset-photo-edits').addEventListener('click', resetEdits);
  for (const [key, input] of Object.entries(controls)) {
    input.addEventListener('input', () => { state[key] = input.valueAsNumber; render(); });
  }
  canvas.addEventListener('pointerdown', event => {
    if (!event.isPrimary || event.button !== 0) return;
    drag = { x: event.clientX, y: event.clientY, offsetX: state.offsetX, offsetY: state.offsetY };
    canvas.setPointerCapture(event.pointerId);
  });
  canvas.addEventListener('pointermove', event => {
    if (!drag || !canvas.hasPointerCapture(event.pointerId)) return;
    const box = canvas.getBoundingClientRect();
    for (const [key, delta] of [['offsetX', (event.clientX - drag.x) / box.width], ['offsetY', (event.clientY - drag.y) / box.height]]) {
      controls[key].value = Math.max(-1, Math.min(1, drag[key] + delta));
      state[key] = controls[key].valueAsNumber;
    }
    render();
  });
  canvas.addEventListener('lostpointercapture', () => { drag = undefined; });

  return {
    setSlot(photos) {
      const [key, dimensions] = Object.entries(photos)[0] || [];
      if (slotKey !== key) reset();
      slotKey = key;
      slot = dimensions;
      render();
    },
    isBusy: () => pending,
    payload() {
      if (!image || !slot) return undefined;
      const frame = Object.fromEntries(frameKeys.map(key => [key, state[key]]));
      const adjustments = Object.fromEntries(toneKeys.map(key => [key, state[key]]));
      return { [slotKey]: { image: image.image, ...frame, turns: 0, background: 0xffffff, adjustments } };
    },
    cancel,
    reset,
  };
}
