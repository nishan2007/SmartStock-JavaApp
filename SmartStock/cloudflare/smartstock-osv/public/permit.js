import { createPortraitEditor } from './portrait-editor.mjs';

const byId = id => document.getElementById(id);
const invitation = new URLSearchParams(location.hash.slice(1));
const instance = invitation.get('instance');
const token = invitation.get('token');
const access = byId('access');
const permit = byId('permit');
const receipt = byId('receipt');
const signIn = byId('sign-in');
const portraitEditor = createPortraitEditor(image => api('REMOVE_BACKGROUND', { image }));
const fields = new Map();
const fieldDefaults = new Map([
  ['classes', 'E-BIKE'],
  ['placeOfIssue', 'OREALLA VILLAGE'],
]);
const selectFields = new Map([
  ['village', { prompt: 'Choose a village', options: [['Orealla', 'OREALLA VILLAGE'], ['Siparuta', 'SIPARUTA VILLAGE']] }],
  ['sex', { prompt: 'Choose sex', options: [['Male', 'M'], ['Female', 'F']] }],
]);
const dateFields = new Set(['dateOfBirth', 'issueDate', 'expiryDate']);
const months = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
let session = '';
let setup = false;
let schema;
let submissionId = crypto.randomUUID();
let busy = false;

function status(message) {
  byId('status').textContent = message;
}

async function api(action, details = {}) {
  const response = await fetch('/api?' + new URLSearchParams({ instance }), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, token, session, ...details }),
    signal: AbortSignal.timeout(45000),
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.message || 'The store could not complete this request.');
  return result;
}

function showForm(form) {
  const previous = new Map([...fields].map(([key, input]) => [key, input.value]));
  schema = form;
  portraitEditor.setSlot(form.photos);
  fields.clear();
  byId('fields').replaceChildren();
  for (const [key, label] of Object.entries(form.fields)) {
    const group = document.createElement('div');
    const caption = document.createElement('label');
    const choices = selectFields.get(key);
    const input = document.createElement(choices ? 'select' : 'input');
    input.id = 'field-' + key;
    input.required = true;
    const defaultValue = fieldDefaults.get(key) ?? '';
    if (choices) {
      const prompt = new Option(choices.prompt, '', true);
      prompt.disabled = true;
      input.add(prompt);
      for (const [name, value] of choices.options) input.add(new Option(name, value));
    } else {
      input.type = dateFields.has(key) ? 'date' : 'text';
      input.maxLength = 1000;
      input.defaultValue = defaultValue;
    }
    input.value = previous.get(key) ?? defaultValue;
    caption.htmlFor = input.id;
    caption.textContent = label;
    group.append(caption, input);
    byId('fields').append(group);
    fields.set(key, input);
  }
  access.hidden = true;
  receipt.hidden = true;
  permit.hidden = false;
  signIn.hidden = false;
  status('');
}

async function showAccess() {
  if (!instance || !token) {
    status('Open the invitation link provided by your permit administrator.');
    return;
  }
  try {
    const info = await api('INFO');
    setup = info.setupRequired;
    byId('setup').hidden = !setup;
    byId('confirm').required = setup;
    access.hidden = false;
    status(setup ? 'Choose the organization access code for this invitation.' : 'Enter the access code supplied with your invitation.');
  } catch (error) {
    status(error.message);
  }
}

access.addEventListener('submit', async event => {
  event.preventDefault();
  if (busy) return;
  busy = true;
  const button = access.querySelector('button');
  button.disabled = true;
  try {
    const result = await api(setup ? 'SETUP' : 'LOGIN', { code: byId('code').value, confirm: byId('confirm').value });
    session = result.session;
    access.reset();
    showForm(result.form);
  } catch (error) {
    status(error.message);
  } finally {
    busy = false;
    button.disabled = false;
  }
});

function printedValue(key, value) {
  if (!dateFields.has(key)) return value.trim();
  const [year, month, day] = value.split('-');
  return Number(day) + ' ' + months[Number(month) - 1] + ' ' + year;
}

permit.addEventListener('submit', async event => {
  event.preventDefault();
  if (busy) return;
  if (portraitEditor.isBusy()) {
    status('Wait for photo processing to finish before saving.');
    return;
  }
  const photo = portraitEditor.payload();
  if (!photo) {
    status('Upload a portrait before saving.');
    byId('photo').focus();
    return;
  }
  busy = true;
  const controls = [...permit.querySelectorAll('input, select, button'), signIn].map(control => [control, control.disabled]);
  controls.forEach(([control]) => { control.disabled = true; });
  try {
    const values = Object.fromEntries([...fields].map(([key, input]) => [key, printedValue(key, input.value)]));
    const result = await api('SUBMIT', { submissionId, schema: schema.schema, payload: { values, photos: photo } });
    if (!/^OSV-[0-9]{4,}$/.test(result.permitNumber || '')) throw new Error('The store has not confirmed a permit number. Retry this submission.');
    byId('number').textContent = result.permitNumber;
    permit.reset();
    portraitEditor.reset();
    submissionId = crypto.randomUUID();
    permit.hidden = true;
    receipt.hidden = false;
    status('');
    byId('another').focus();
  } catch (error) {
    status(error.name === 'TimeoutError' ? 'The store did not confirm the save. Keep the same entries and retry.' : error.message);
  } finally {
    busy = false;
    controls.forEach(([control, disabled]) => { control.disabled = disabled; });
  }
});

byId('another').addEventListener('click', () => {
  showForm(schema);
  fields.values().next().value?.focus();
});
signIn.addEventListener('click', () => {
  portraitEditor.cancel();
  session = '';
  signIn.hidden = true;
  permit.hidden = true;
  receipt.hidden = true;
  showAccess();
});
showAccess();

