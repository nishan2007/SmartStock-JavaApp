import { resumeBrowsing } from './navigation.js';

const element = id => document.getElementById(id);
let csrf = '';
let preauth = '';
let folders = [];
let generation = 0;
let canManage = false;
let pendingLocation = null;
let requestedFolders = null;
let selectedFile = null;
const activation = new URLSearchParams(location.hash.slice(1)).get('activate');
const linkedTemplate = new URLSearchParams(location.search);
if (activation) history.replaceState(null, '', location.pathname + location.search);

function account(showActivation = false, preserveLocation = false) {
    const locationFolders = requestedFolders ?? folders;
    if (preserveLocation && locationFolders.length) pendingLocation = { folders: structuredClone(locationFolders), fileId: selectedFile?.id };
    else if (!preserveLocation) pendingLocation = null;
    requestedFolders = null;
    element('scannerCredential').close();element('scannerPassword').value = '';element('scannerCredentialAccount').textContent = '';
    generation++;
    folders = [];
    csrf = '';
    preauth = '';
    element('workspace').hidden = true;
    element('logout').hidden = true;
    element('account').hidden = false;
    element('activate').hidden = !showActivation;
    element('login').hidden = showActivation;
    element('verify').hidden = true;
    element('qr').removeAttribute('src');
    element('secret').textContent = '';
    element('entries').replaceChildren();
    element('management').hidden = true;
    element('managementTab').hidden = true;
    element('health').replaceChildren();
    element('effectiveSettings').replaceChildren();
    element('backupVersions').replaceChildren();
    element('selectedFolders').replaceChildren();
    element('activity').replaceChildren();
    element('scannerControls').replaceChildren();
    element('enrollScanner').reset();element('scannerStore').replaceChildren();
    element('workstationList').replaceChildren();
    element('setupCode').textContent = '';
    element('workstationName').value = '';
    canManage = false;
    element('protectFolder').hidden = true;
    element('unprotectFolder').hidden = true;
    hidePreview();
}

async function api(path, body) {
    const response = await fetch('./api/' + path, body === undefined ? {} : {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
        body: JSON.stringify(body)
    });
    const data = await response.json();
    if (!response.ok) {
        if (response.status === 401 || response.status === 403) account(false, true);
        throw new Error(data.error?.message ?? data.error ?? 'Request failed.');
    }
    return data.data ?? data;
}

function action(button, callback) {
    return async event => {
        event.preventDefault();
        button.disabled = true;
        element('notice').textContent = '';
        try { await callback(); }
        catch (error) { element('notice').textContent = error.message; }
        finally { button.disabled = false; }
    };
}

function hidePreview() {
    selectedFile = null;
    element('preview').hidden = true;
    element('previewContent').replaceChildren();
    element('entries').querySelector('[aria-pressed=true]')?.setAttribute('aria-pressed', 'false');
}

function button(label, callback) {
    const control = document.createElement('button');
    control.type = 'button';
    control.textContent = label;
    control.onclick = action(control, callback);
    return control;
}

function showPreview(file, control) {
    hidePreview();
    selectedFile = file;
    control?.setAttribute('aria-pressed', 'true');
    element('preview').hidden = false;
    element('filename').textContent = file.name;
    const content = element('previewContent');
    content.append(button('Back to files', hidePreview));
    if (!file.preview) {
        const note = document.createElement('p');
        note.textContent = 'Preview unavailable. Open this file on the shared drive.';
        content.append(note);
        return;
    }
    const href = './api/preview?id=' + encodeURIComponent(file.id);
    if (/\.(?:pdf|docx|pptx)$/iu.test(file.name)) {
        const link = document.createElement('a');
        link.href = href;
        link.target = '_blank';
        link.rel = 'noopener';
        link.textContent = 'Open document preview';
        const row = document.createElement('p');
        row.append(link);
        content.append(row);
    } else {
        const image = document.createElement('img');
        image.alt = file.name;
        image.onerror = () => { image.replaceWith(document.createTextNode('Preview unavailable.')); };
        image.src = href;
        content.append(image);
    }
}

async function browse(next = []) {
    const current = ++generation;
    requestedFolders = next;
    const folder = next.at(-1);
    const entries = await api(folder ? 'files?id=' + encodeURIComponent(folder.id) : 'stores');
    if (generation !== current) return;
    requestedFolders = null;
    folders = next;
    element('protectFolder').hidden = !canManage || !folder || next[0]?.manage === false;
    element('unprotectFolder').hidden = !canManage || !folder || next[0]?.manage === false;
    hidePreview();
    element('crumbs').replaceChildren(button('Stores', () => browse()));
    folders.forEach((item, index) => {
        element('crumbs').append(button(item.name, () => browse(folders.slice(0, index + 1))));
    });
    element('entries').replaceChildren();
    for (const entry of entries) {
        const file = folder ? entry : { ...entry, name: (entry.manage === false ? 'Shared from store ' : 'Store ') + entry.store, folder: true };
        const row = document.createElement('li');
        const control = button(file.name + (file.folder ? ' ›' : ''), () => file.folder
            ? browse([...folders, file]) : showPreview(file, control));
        if (!file.folder) control.setAttribute('aria-pressed', 'false');
        if (file.modifiedAt) {
            const details = document.createElement('small');
            const size = file.sizeBytes == null ? '' : file.sizeBytes >= 1e9 ? (file.sizeBytes / 1e9).toFixed(1) + ' GB' :
                file.sizeBytes >= 1e6 ? (file.sizeBytes / 1e6).toFixed(1) + ' MB' : file.sizeBytes >= 1000 ? (file.sizeBytes / 1000).toFixed(1) + ' KB' : file.sizeBytes + ' bytes';
            details.className = 'fileDetails';
            details.textContent = [file.fileType, size, 'Modified ' + new Date(file.modifiedAt).toLocaleString(),
                !file.folder && !file.preview ? 'Preview unavailable' : ''].filter(Boolean).join(' · ');
            control.append(details);
        }
        row.append(control);
        element('entries').append(row);
    }
    if (!entries.length) {
        const row = document.createElement('li');
        row.textContent = folder ? 'This folder is empty.' : 'No stores are available.';
        element('entries').append(row);
    }
    return true;
}

async function enter(session) {
    const entering = generation;
    csrf = session.csrfToken;
    const access = await api('session');
    if (entering !== generation) return;
    canManage = access.canManage;
    element('managementTab').hidden = !access.canManage;
    element('management').hidden = true;
    element('fileBrowser').hidden = false;
    const saved = pendingLocation;pendingLocation = null;
    let restored;
    if (saved) {
        const current = generation;
        try { restored = await resumeBrowsing(saved, api); }
        catch (error) { if (current !== generation) throw error; }
        if (current !== generation) return;
        if (!restored) element('notice').textContent = 'The requested folder is unavailable for this session. Choose an assigned store.';
    }
    if (!await browse(restored?.folders ?? [])) return;
    if (restored?.file) showPreview(restored.file);
    element('account').hidden = true;
    element('workspace').hidden = false;
    element('logout').hidden = false;
    if (!saved && linkedTemplate.has('template')) {
        const current = generation;
        const query = new URLSearchParams({
            kind: linkedTemplate.get('kind') ?? 'BADGE',
            store: linkedTemplate.get('store') ?? '',
            template: linkedTemplate.get('template')
        });
        try {
            const file = await api('template?' + query);
            if (current === generation) showPreview({ ...file, preview: true });
        } catch (error) {
            if (current === generation) element('notice').textContent = error.message;
        }
    }
}

async function health() {
    const current = generation;
    const data = await api('management/status');
    if (current !== generation) return;
    const content = element('health');
    content.replaceChildren();
    const summary = document.createElement('p');
    summary.textContent = 'Service is ' + data.service;
    content.append(summary);
    if (data.backups) {
        const backups = document.createElement('p');
        backups.textContent = 'OneDrive backup: ' + data.backups.state +
            (data.backups.state === 'unavailable' ? ' · Backup status could not be read.' :
                ' · ' + data.backups.selectedFolders + ' selected folders · ' + data.backups.verified + ' verified versions · ' +
                data.backups.pending + ' pending · ' + data.backups.failed + ' awaiting retry' +
                (data.backups.lastVerifiedAt ? ' · Last verified ' + new Date(data.backups.lastVerifiedAt).toLocaleString() : ' · No verified backup recorded'));
        content.append(backups);
    }
    if (data.dependencies?.accounts) {
        const accounts = document.createElement('p');
        accounts.textContent = 'Windows account service: ' + data.dependencies.accounts.state +
            (data.dependencies.accounts.state === 'unavailable' ? ' · Workstation and copier credential changes need attention.' : '') +
            ' · Checked ' + new Date(data.dependencies.accounts.checkedAt).toLocaleTimeString();
        content.append(accounts);
    }
    if (data.previews) {
        const previews = document.createElement('p');
        previews.textContent = 'Office previews: ' + data.previews.state +
            (data.previews.state === 'not-configured' ? '' : ' · ' + data.previews.active + ' active · ' +
                data.previews.queued + ' queued · ' + data.previews.completed + ' completed · ' +
                data.previews.failures + ' failed since service start') +
            (data.previews.lastResult ? ' · Last conversion ' + data.previews.lastResult.result + ' ' + data.previews.lastResult.at : '');
        content.append(previews);
    }
    for (const store of data.stores) {
        const row = document.createElement('p');
        row.textContent = 'Store ' + store.store + ' · ' +
            (Number(store.availableBytes) / 1e9).toFixed(1) + ' GB available' +
            (store.lowSpace ? ' · Low storage: backup copies, restores and Office previews may pause.' : '');
        content.append(row);
    }
    const title = document.createElement('h2');
    title.textContent = 'Scanners';
    content.append(title);
    if (!data.scanners.length) content.append(document.createTextNode('No scanners configured for your stores.'));
    for (const scanner of data.scanners) {
        const row = document.createElement('p');
        row.textContent = scanner.id + ' · ' + (scanner.enabled ? 'Enabled' : 'Disabled') +
            ' · ' + scanner.quarantineCount + ' quarantined' +
            (scanner.lastResult ? ' · ' + scanner.lastResult.result + ' ' + scanner.lastResult.at : ' · Awaiting first scan');
        content.append(row);
    }
}
element('filesTab').onclick = () => {
    element('filesTab').setAttribute('aria-pressed', 'true');
    element('managementTab').setAttribute('aria-pressed', 'false');
    element('management').hidden = true;
    element('fileBrowser').hidden = false;
};
element('managementTab').onclick = action(element('managementTab'), async () => {
    element('filesTab').setAttribute('aria-pressed', 'false');
    element('managementTab').setAttribute('aria-pressed', 'true');
    element('management').hidden = false;
    element('fileBrowser').hidden = true;
    await health();
});
for (const tab of element('managementNav').querySelectorAll('button')) {
    tab.onclick = action(tab, async () => {
        for (const other of element('managementNav').querySelectorAll('button')) {
            other.setAttribute('aria-pressed', String(other === tab));
        }
        for (const panel of document.querySelectorAll('[data-management-panel]')) {
            panel.hidden = panel.dataset.managementPanel !== tab.dataset.panel;
        }
        if (tab.dataset.panel === 'scanners') await showScannerControls();
        if (tab.dataset.panel === 'settings') await showSettings();
    });
}
element('refreshHealth').onclick = action(element('refreshHealth'), health);
async function showSettings() {
    const current = generation;
    const data = await api('management/settings');
    if (current !== generation) return;
    const content = element('effectiveSettings');content.replaceChildren();
    const lines = [['Product', data.productName], ['Company name', data.displayName], ['Installation name', data.installationName],
        ['Network device', data.serverName], ['Network share', data.share], ['Management URL', data.managementUrl],
        ['Assigned stores', data.stores.join(', ')], ['Employee web access', data.webAccess],
        ['Backup destination', data.backup.configured ? data.backup.provider : 'Not configured']];
    if (data.origin) lines.push(['Origin transport', data.origin.transport], ['Origin listener', data.origin.listenAddress + ':' + data.origin.port],
        ['Origin clients', data.origin.allowedAddresses.join(', ')]);
    if (data.backup.configured) lines.push(['OneDrive drive ID', data.backup.driveId], ['OneDrive folder ID', data.backup.folderId],
        ['Backup behavior', data.backup.mode], ['Restore destination', data.backup.restore]);
    for (const [label, value] of lines) { const line = document.createElement('p');line.textContent = label + ': ' + value;content.append(line); }
}
element('refreshSettings').onclick = action(element('refreshSettings'), showSettings);
async function showWorkstations() {
    const current = generation;
    const devices = await api('management/workstations');
    const stores = await api('stores');
    if (current !== generation) return;
    const selector = element('workstationStore');
    selector.replaceChildren();
    for (const store of stores) {
        if (store.manage === false) continue;
        const option = document.createElement('option');
        option.value = store.store;
        option.textContent = 'Store ' + store.store;
        selector.append(option);
    }
    const content = element('workstationList');
    content.replaceChildren();
    if (!devices.length) content.textContent = 'No enrolled workstations.';
    for (const device of devices) {
        const row = document.createElement('p');
        row.append(document.createTextNode(device.name + ' · Stores ' + device.stores.join(', ') + ' · ' + device.status +
            (device.activityState ? ' · ' + device.activityState : '') +
            (device.lastActiveAt ? ' · Last observed activity ' + new Date(device.lastActiveAt).toLocaleString() : ' · No drive activity observed yet') + ' '));
        if (device.status !== 'revoked') {
            row.append(button('Revoke access', async () => {
                const result = await api('management/workstations/revoke', { id: device.id });
                element('notice').textContent = result.message;
                await showWorkstations();
            }));
        }
        content.append(row);
    }
}
element('loadWorkstations').onclick = action(element('loadWorkstations'), showWorkstations);
const enrollmentForm = element('enrollWorkstation');
enrollmentForm.onsubmit = action(enrollmentForm.querySelector('button'), async () => {
    const current = generation;
    const result = await api('management/workstations/enroll', {
        name: element('workstationName').value, stores: [element('workstationStore').value]
    });
    if (current !== generation) return;
    element('setupCode').textContent = 'One-time setup code · ' + result.code +
        ' · Expires ' + new Date(result.expiresAt).toLocaleTimeString();
});
async function showScannerControls() {
    const current = generation;
    const scanners = await api('management/scanners');
    const stores = await api('stores');
    if (current !== generation) return;
    const selector = element('scannerStore');
    const selected = selector.value;selector.replaceChildren();
    for (const store of stores) {
        if (store.manage === false) continue;
        const option = document.createElement('option');option.value = store.store;option.textContent = 'Store ' + store.store;
        selector.append(option);
    }
    if ([...selector.options].some(option => option.value === selected)) selector.value = selected;
    const content = element('scannerControls');
    content.replaceChildren();
    if (!scanners.length) content.textContent = 'No scanners are available.';
    for (const scanner of scanners) {
        const row = document.createElement('p');
        if (scanner.enrollmentPending) {
            row.textContent = scanner.id + ' · Setup requires administrator review. Check the server account journal and folders before attempting recovery.';
            content.append(row);continue;
        }
        row.append(document.createTextNode((scanner.setup?.displayName ?? scanner.id) + ' · ' + (scanner.paused ? 'Processing paused ' : 'Processing enabled ')));
        row.append(button(scanner.paused ? 'Resume processing' : 'Pause processing', async () => {
            const result = await api('management/scanner-processing', { id: scanner.id, paused: !scanner.paused });
            element('notice').textContent = result.message;
            await showScannerControls();
        }));
        if (scanner.credentialState) {
            row.append(document.createTextNode(' · Credential: ' + scanner.credentialState + ' '));
            if (scanner.disabled || scanner.credentialState === 'requires-review') row.append(button('Enable copier access', async () => {
                await api('management/scanner-enable', { id: scanner.id });
                element('notice').textContent = 'Copier access enabled. Its current password is unchanged.';
                await showScannerControls();
            }));
            row.append(button('Disable copier access', async () => {
                await api('management/scanner-disable', { id: scanner.id });
                element('notice').textContent = 'Copier credential disabled and existing connections closed.';
                await showScannerControls();
            }));
            row.append(button('Rotate password', async () => {
                if (!confirm('Replace this copier’s password? Update the copier before it can send more scans.')) return;
                const current = generation;
                const result = await api('management/scanner-rotate', { id: scanner.id });
                if (current !== generation) return;
                element('scannerCredentialAccount').textContent = result.account + (result.disabled ? ' · Access remains disabled' : '');
                element('scannerPassword').type = 'password';element('scannerPassword').value = result.password;
                element('revealScannerPassword').textContent = 'Show password';element('scannerCredential').showModal();
                await showScannerControls();
            }));
        }
        content.append(row);
        if (scanner.setup) {
            const details = document.createElement('details');
            const summary = document.createElement('summary');summary.textContent = 'Setup and test instructions';details.append(summary);
            for (const [label, value] of [['Machine ID', scanner.id], ['Model', scanner.setup.model ?? 'Not recorded'],
                ['Reserved IP', scanner.setup.reservedAddress ?? 'Not recorded'], ['Protocol', scanner.setup.protocol],
                ['Server', scanner.setup.server], ['Share', scanner.setup.share], ['Folder', scanner.setup.folder],
                ['Username', scanner.setup.username], ['Destination', scanner.setup.destination], ['Published scans', scanner.setup.publishedFolder]]) {
                const line = document.createElement('p');line.textContent = label + ': ' + value;details.append(line);
            }
            const steps = document.createElement('ol');
            for (const instruction of scanner.setup.steps) { const step = document.createElement('li');step.textContent = instruction;steps.append(step); }
            details.append(steps);content.append(details);
        }
    }
}

const scannerEnrollmentForm = element('enrollScanner');
scannerEnrollmentForm.onsubmit = action(scannerEnrollmentForm.querySelector('button'), async () => {
    const current = generation;
    const result = await api('management/scanner-enroll', { id: element('scannerId').value,
        displayName: element('scannerName').value, storeId: element('scannerStore').value,
        ...(element('scannerModel').value.trim() ? { model: element('scannerModel').value.trim() } : {}),
        ...(element('scannerAddress').value.trim() ? { reservedAddress: element('scannerAddress').value.trim() } : {}) });
    if (current !== generation) return;
    scannerEnrollmentForm.reset();
    element('scannerCredentialAccount').textContent = result.setup.username;
    element('scannerPassword').type = 'password';element('scannerPassword').value = result.password;
    element('revealScannerPassword').textContent = 'Show password';element('scannerCredential').showModal();
    element('notice').textContent = 'Copier added. Save its password and follow the setup instructions below. Send a test PDF from the copier.';
    await showScannerControls();
});
element('revealScannerPassword').onclick = () => {
    const field = element('scannerPassword');field.type = field.type === 'password' ? 'text' : 'password';
    element('revealScannerPassword').textContent = field.type === 'password' ? 'Show password' : 'Hide password';
};
element('scannerCredential').onclose = () => { element('scannerPassword').value = '';element('scannerCredentialAccount').textContent = ''; };
element('closeScannerCredential').onclick = () => element('scannerCredential').close();
element('loadScannerControls').onclick = action(element('loadScannerControls'), showScannerControls);
element('loadActivity').onclick = action(element('loadActivity'), async () => {
    const current = generation;
    const events = await api('management/activity');
    if (current !== generation) return;
    const content = element('activity');
    content.replaceChildren();
    if (!events.length) content.textContent = 'No management activity for your stores.';
    const labels = { RETRY_BACKUPS: 'Retry backup uploads', SELECT_BACKUP_FOLDER: 'Change backup folder',
        RESTORE_BACKUP: 'Restore backup', ENROLL_SCANNER: 'Add copier', SCANNER_PROCESSING: 'Change scan processing', SCANNER_DISABLE: 'Disable copier access', SCANNER_ENABLE: 'Enable copier access', SCANNER_ROTATE: 'Rotate copier password' };
    for (const event of events) {
        const row = document.createElement('p');
        row.textContent = new Date(event.at).toLocaleString() + ' · Employee ' + event.userId + ' · ' +
            (labels[event.action] ?? event.action) + ' · ' + event.result;
        content.append(row);
    }
});
for (const [id, enabled] of [['protectFolder', true], ['unprotectFolder', false]]) {
    element(id).onclick = action(element(id), async () => {
        const folder = folders.at(-1);
        if (!folder) return;
        const result = await api('management/select', { id: folder.id, enabled });
        element('notice').textContent = result.message;
    });
}
element('loadSelections').onclick = action(element('loadSelections'), async () => {
    const current = generation;
    const selections = await api('management/selections');
    if (current !== generation) return;
    const content = element('selectedFolders');
    content.replaceChildren();
    if (!selections.length) content.textContent = 'No folders selected for backup.';
    for (const selection of selections) {
        const row = document.createElement('p');
        row.textContent = 'Store ' + selection.store + ' / ' + (selection.parts.join(' / ') || 'All folders');
        content.append(row);
    }
});
element('retryBackups').onclick = action(element('retryBackups'), async () => {
    const result = await api('management/retry', {});
    element('notice').textContent = result.message;
});
element('loadVersions').onclick = action(element('loadVersions'), async () => {
    const current = generation;
    const versions = await api('management/versions');
    if (current !== generation) return;
    const content = element('backupVersions');
    content.replaceChildren();
    if (!versions.length) content.textContent = 'No backed-up versions are available for your stores.';
    for (const version of versions) {
        const row = document.createElement('p');
        row.append(document.createTextNode(version.name + ' · ' + version.createdAt + ' · ' + version.status + ' '));
        if (version.status === 'verified') {
            row.append(button('Restore to Recovery', async () => {
                const result = await api('management/restore', { id: version.id });
                element('notice').textContent = result.message + ' Original file: ' + result.originalPath;
            }));
        }
        content.append(row);
    }
});

element('activate').onclick = action(element('activate'), async () => {
    await api('account/activate', { token: activation });
    account();
});
const login = element('login');
login.onsubmit = action(login.querySelector('button'), async () => {
    const password = element('password').value;
    element('password').value = '';
    const session = await api('account/auth/login', { identifier: element('identifier').value.trim(), password });
    if (!session.mfaRequired) return enter(session);
    preauth = session.preauth;
    login.hidden = true;
    element('verify').hidden = false;
    element('qr').hidden = !session.qrCode;
    if (session.qrCode) element('qr').src = session.qrCode;
    element('secret').textContent = session.secret ?? '';
    element('code').focus();
});
const verify = element('verify');
verify.onsubmit = action(verify.querySelector('button'), async () => {
    const code = element('code').value;
    element('code').value = '';
    try { await enter(await api('account/auth/verify', { preauth, code })); }
    catch (error) { account(false, true); throw error; }
    finally {
        preauth = '';
        element('qr').removeAttribute('src');
        element('secret').textContent = '';
    }
});
element('logout').onclick = action(element('logout'), async () => {
    try { await api('account/auth/logout', {}); }
    finally { account(); }
});

try {
    const brand = await api('branding');
    document.title = brand.name;
    element('brand').textContent = brand.name;
    element('welcome').textContent = brand.name;
    if (activation) account(true);
    else await enter(await api('account/auth/session', {}));
} catch (error) {
    account(Boolean(activation));
    if (error.message !== 'Sign in to continue.' && error.message !== 'Sign in again.') {
        element('notice').textContent = error.message;
    }
}
