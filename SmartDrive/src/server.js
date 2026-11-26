import { createServer } from 'node:http';
import { createServer as createHttpsServer } from 'node:https';
import { readFile } from 'node:fs/promises';
import { pipeline } from 'node:stream/promises';
import { FileUnavailable } from './files.js';
import { timingSafeEqual } from 'node:crypto';
import { backupOverview } from './management-overview.js';

const assets = new Map([
    ['/', ['index.html', 'text/html']],
    ['/app.js', ['app.js', 'application/javascript']],
    ['/navigation.js', ['navigation.js', 'application/javascript']],
    ['/app.css', ['app.css', 'text/css']],
    ['/storage-icon.png', ['storage-icon.png', 'image/png']]
]);

export function driveServer({ library, employeeSession, auth, branding = { displayName: 'SmartDrive' }, status, backups, selections, audit, scannerControl,
    workstations, publicOrigin, studioBridge, scannerCredentialControl, scannerEnrollmentControl, settings, tls, originPeer }) {
    if (tls && typeof originPeer !== 'function') throw new Error('HTTPS origin requires explicit peer authorization.');
    if (studioBridge && (!/^[a-f0-9]{64}$/u.test(studioBridge.token) ||
        !/^[A-Za-z][A-Za-z0-9-]{0,14}$/u.test(studioBridge.server) ||
        !/^[A-Za-z0-9][A-Za-z0-9 _-]{0,79}$/u.test(studioBridge.share))) throw new Error('Invalid Studio bridge configuration.');
    const makeServer = tls ? listener => createHttpsServer({ ...tls, minVersion: 'TLSv1.2' }, listener) : createServer;
    return makeServer(async (request, response) => {
        response.setHeader('Cache-Control', 'private, no-store');
        response.setHeader('X-Content-Type-Options', 'nosniff');
        response.setHeader('Referrer-Policy', 'no-referrer');
        response.setHeader('Content-Security-Policy', "default-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        const json = (status, body) => {
            response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
            response.end(JSON.stringify(body));
        };
        try {
            if (originPeer && !originPeer(request.socket.remoteAddress)) return json(403, { error: 'Origin client unavailable.' });
            const url = new URL(request.url, 'http://localhost');
            if (['/internal/studio/catalog', '/internal/studio/template'].includes(url.pathname)) {
                const token = request.headers['x-smartdrive-service'];
                if (!studioBridge || typeof token !== 'string' || !/^[a-f0-9]{64}$/u.test(token) ||
                    !timingSafeEqual(Buffer.from(token), Buffer.from(studioBridge.token))) return json(404, { error: 'Not found.' });
                if (request.method !== 'POST') return json(405, { error: 'POST is required.' });
                const chunks = [];
                let size = 0;
                for await (const chunk of request) {
                    size += chunk.length;
                    if (size > 8192) return json(413, { error: 'Request is too large.' });
                    chunks.push(chunk);
                }
                let body;
                try { body = JSON.parse(Buffer.concat(chunks)); }
                catch { return json(400, { error: 'Invalid bridge request.' }); }
                if (!body || !Array.isArray(body.storeIds) || body.storeIds.length > 1000 ||
                    !body.storeIds.every(id => typeof id === 'string' && /^[1-9]\d*$/u.test(id))) {
                    return json(400, { error: 'Invalid employee store scope.' });
                }
                if (url.pathname === '/internal/studio/template') {
                    if (typeof body.store !== 'string' || !/^[1-9]\d*$/u.test(body.store) ||
                        typeof body.id !== 'string' || !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/u.test(body.id)) {
                        return json(400, { error: 'Invalid template identity.' });
                    }
                    return json(200, body.linkAuthorized === true ? await library.linkedTemplate(body.store, body.id) :
                        await library.templateHead({ storeIds: body.storeIds }, body.store, body.id));
                }
                const templates = await library.desktopTemplates({ storeIds: body.storeIds }, body.kind);
                return json(200, { networkRoot: '\\\\' + studioBridge.server + '\\' + studioBridge.share,
                    templates: templates.map(({ parts, ...record }) => ({ ...record,
                    networkPath: '\\\\' + studioBridge.server + '\\' + studioBridge.share + '\\' +
                        record.ownerStoreId + '\\' + parts.join('\\') })) });
            }
            const prefix = '/employees/drive';
            const route = url.pathname.startsWith(prefix) ? url.pathname.slice(prefix.length) : '';
            if (request.method === 'GET' && url.pathname === prefix) {
                response.writeHead(308, { Location: prefix + '/' });
                return response.end();
            }
            if (request.method === 'GET' && assets.has(route)) {
                const [name, type] = assets.get(route);
                const bytes = await readFile(new URL('../web/' + name, import.meta.url));
                response.writeHead(200, { 'Content-Type': type + (type === 'image/png' ? '' : '; charset=utf-8') });
                return response.end(bytes);
            }
            if (request.method === 'GET' && route === '/api/branding') {
                return json(200, { name: branding.displayName });
            }
            if (request.method === 'POST' && route.startsWith('/api/account/') && auth) {
                const chunks = [];
                let length = 0;
                for await (const chunk of request) {
                    length += chunk.length;
                    if (length > 8192) return json(413, { error: { message: 'Request is too large.' } });
                    chunks.push(chunk);
                }
                const result = await auth(request, route.slice('/api/account/'.length), Buffer.concat(chunks));
                const cookies = result.headers.getSetCookie();
                if (cookies.length) response.setHeader('Set-Cookie', cookies);
                return json(result.status, await result.json());
            }
            const workstationWrite = ['/api/management/workstations/enroll', '/api/management/workstations/revoke'].includes(route);
            if (route === '/api/enroll' && request.method === 'POST') {
                if (!workstations) return json(503, { error: 'Workstation enrollment is unavailable.' });
                if (!publicOrigin || request.headers.origin !== publicOrigin) return json(403, { error: 'Enrollment origin is not allowed.' });
                const chunks = [];
                let size = 0;
                for await (const chunk of request) {
                    size += chunk.length;
                    if (size > 1024) return json(413, { error: 'Request is too large.' });
                    chunks.push(chunk);
                }
                try {
                    const code = JSON.parse(Buffer.concat(chunks)).code;
                    return json(200, await workstations.redeem(code));
                } catch { return json(400, { error: 'Enrollment failed. Ask a manager to check the workstation and issue a new code.' }); }
            }
            const scannerCredentialWrite = ['/api/management/scanner-disable', '/api/management/scanner-enable', '/api/management/scanner-rotate'].includes(route);
            const backupWrite = scannerCredentialWrite || workstationWrite || ['/api/management/retry', '/api/management/restore', '/api/management/select',
                '/api/management/scanner-processing', '/api/management/scanner-enroll'].includes(route);
            if (!['/api/stores', '/api/files', '/api/template', '/api/preview', '/api/session', '/api/management/status',
                '/api/management/versions', '/api/management/selections', '/api/management/activity',
                '/api/management/scanners', '/api/management/workstations', '/api/management/settings'].includes(route) && !backupWrite) {
                return json(404, { error: 'Not found.' });
            }
            const method = backupWrite ? 'POST' : 'GET';
            if (request.method !== method) {
                response.setHeader('Allow', method);
                return json(405, { error: method + ' is required.' });
            }
            const identity = await employeeSession(request, backupWrite);
            if (!identity) return json(401, { error: 'Sign in to continue.' });
            const canManage = identity.permissions?.includes('MANAGE_SMARTDRIVE') === true;
            if (route === '/api/template') return json(200, await library.template(identity,
                url.searchParams.get('kind'), url.searchParams.get('store'), url.searchParams.get('template')));
            if (route.startsWith('/api/management/')) {
                if (!canManage) return json(403, { error: 'Manage SmartDrive permission is required.' });
                if (route === '/api/management/settings') {
                    if (!settings) return json(503, { error: 'Management settings are unavailable.' });
                    return json(200, await settings(identity));
                }
                if (route === '/api/management/workstations') {
                    return json(200, workstations ? await workstations.list(identity) : []);
                }
                if (workstationWrite) {
                    if (!workstations || !audit) return json(503, { error: 'Workstation management is unavailable.' });
                    const chunks = [];
                    let size = 0;
                    for await (const chunk of request) {
                        size += chunk.length;
                        if (size > 2048) return json(413, { error: 'Request is too large.' });
                        chunks.push(chunk);
                    }
                    let body;
                    try { body = JSON.parse(Buffer.concat(chunks)); }
                    catch { return json(400, { error: 'Check the workstation details.' }); }
                    if (route.endsWith('/enroll')) {
                        if (!Array.isArray(body?.stores) || !body.stores.length ||
                            !body.stores.every(store => typeof store === 'string' && identity.storeIds.includes(store))) {
                            return json(400, { error: 'Choose assigned stores.' });
                        }
                        const issued = await audit.action(identity, 'ENROLL_WORKSTATION', body.stores, {},
                            () => workstations.issue(identity, body.name, body.stores));
                        return json(200, issued);
                    }
                    const device = (await workstations.list(identity)).find(item => item.id === body?.id);
                    if (!device) return json(404, { error: 'Workstation unavailable.' });
                    await audit.action(identity, 'REVOKE_WORKSTATION', device.stores, { id: device.id },
                        () => workstations.revoke(identity, device.id));
                    return json(200, { message: 'Workstation access revoked.' });
                }
                if (route === '/api/management/scanners') {
                    const devices = scannerControl ? await scannerControl.list(identity) : [];
                    const credentials = scannerCredentialControl ? await scannerCredentialControl.list(identity) : [];
                    return json(200, [...devices.map(device => ({ ...device, ...credentials.find(item => item.id === device.id) })),
                        ...(scannerEnrollmentControl?.pending(identity) ?? [])]);
                }
                if (route === '/api/management/scanner-enroll') {
                    if (!scannerEnrollmentControl || !audit) return json(503, { error: 'Scanner enrollment is unavailable.' });
                    const chunks = [];let size = 0;
                    for await (const chunk of request) { size += chunk.length;if (size > 2048) return json(413, { error: 'Request is too large.' });chunks.push(chunk); }
                    let body;try { body = JSON.parse(Buffer.concat(chunks)); } catch { return json(400, { error: 'Check the scanner details.' }); }
                    if (!identity.storeIds.includes(body?.storeId)) return json(400, { error: 'Choose an assigned store.' });
                    return json(200, await audit.action(identity, 'ENROLL_SCANNER', [body.storeId], {},
                        () => scannerEnrollmentControl.enroll(identity, body)));
                }
                if (scannerCredentialWrite) {
                    if (!scannerCredentialControl || !audit) return json(503, { error: 'Scanner credentials are unavailable.' });
                    const chunks = [];let size = 0;
                    for await (const chunk of request) { size += chunk.length;if (size > 1024) return json(413, { error: 'Request is too large.' });chunks.push(chunk); }
                    let body;try { body = JSON.parse(Buffer.concat(chunks)); } catch { return json(400, { error: 'Choose a scanner.' }); }
                    const scanner = (await scannerCredentialControl.list(identity)).find(item => item.id === body?.id);
                    if (!scanner) return json(404, { error: 'Scanner unavailable.' });
                    const rotate = route === '/api/management/scanner-rotate';
                    const enable = route === '/api/management/scanner-enable';
                    const result = await audit.action(identity, rotate ? 'SCANNER_ROTATE' : enable ? 'SCANNER_ENABLE' : 'SCANNER_DISABLE', [scanner.store], { id: scanner.id },
                        () => rotate ? scannerCredentialControl.rotate(identity, scanner.id) : enable ? scannerCredentialControl.enable(identity, scanner.id) : scannerCredentialControl.disable(identity, scanner.id));
                    return json(200, result);
                }
                if (route === '/api/management/scanner-processing') {
                    if (!scannerControl || !audit) return json(503, { error: 'Scanner controls are unavailable.' });
                    const chunks = [];
                    let size = 0;
                    for await (const chunk of request) {
                        size += chunk.length;
                        if (size > 1024) return json(413, { error: 'Request is too large.' });
                        chunks.push(chunk);
                    }
                    let body;
                    try { body = JSON.parse(Buffer.concat(chunks)); }
                    catch { return json(400, { error: 'Choose a scanner and processing setting.' }); }
                    const scanner = (await scannerControl.list(identity)).find(item => item.id === body?.id);
                    if (!scanner || typeof body.paused !== 'boolean') return json(400, { error: 'Scanner unavailable.' });
                    await audit.action(identity, 'SCANNER_PROCESSING', [scanner.store], { id: scanner.id, paused: body.paused },
                        () => scannerControl.set(identity, scanner.id, body.paused));
                    return json(200, { message: body.paused ? 'Scan processing paused. Received files remain in intake.' : 'Scan processing resumed.' });
                }
                if (route === '/api/management/activity') {
                    if (!audit) return json(503, { error: 'Management activity is unavailable.' });
                    return json(200, await audit.list(identity));
                }
                if (backupWrite && !audit) return json(503, { error: 'Configure private audit storage before management changes.' });
                if (route !== '/api/management/status') {
                    if (!backups) return json(503, { error: 'Configure company OneDrive backup to use recovery.' });
                    if (route === '/api/management/selections') {
                        if (!selections) return json(503, { error: 'Folder selection is unavailable.' });
                        return json(200, (await selections.read()).filter(item => identity.storeIds.includes(item.store)));
                    }
                    if (route === '/api/management/versions') {
                        const versions = (await backups.versions()).filter(item => identity.storeIds.includes(item.store))
                            .sort((a,b)=>(b.sequence??0)-(a.sequence??0)||b.createdAt.localeCompare(a.createdAt));
                        return json(200, versions.map(({ id, store, parts, size, createdAt, status }) =>
                            ({ id, store, name: parts.join('/'), size, createdAt, status })));
                    }
                    if (route === '/api/management/retry') {
                        await audit.action(identity, 'RETRY_BACKUPS', identity.storeIds, {},
                            () => backups.retry(identity.storeIds));
                        return json(200, { message: 'Pending uploads retried.' });
                    }
                    const chunks = [];
                    let length = 0;
                    for await (const chunk of request) {
                        length += chunk.length;
                        if (length > 10000) return json(413, { error: 'Request is too large.' });
                        chunks.push(chunk);
                    }
                    let body;
                    try { body = JSON.parse(Buffer.concat(chunks)); }
                    catch { return json(400, { error: 'Choose a backup version.' }); }
                    const id = body?.id;
                    if (route === '/api/management/select') {
                        if (!selections || typeof body.enabled !== 'boolean') return json(400, { error: 'Choose a folder and backup setting.' });
                        const folder = await library.folder(identity, id);
                        await audit.action(identity, 'SELECT_BACKUP_FOLDER', [folder.store],
                            { parts: folder.parts, enabled: body.enabled }, () => selections.set(folder, body.enabled));
                        return json(200, { message: body.enabled ? 'Folder selected for backup.' : 'Folder removed from future backups.' });
                    }
                    if (typeof id !== 'string' || !/^[a-f0-9-]{36}$/u.test(id)) {
                        return json(400, { error: 'Choose a backup version.' });
                    }
                    const version = (await backups.versions()).find(item => item.id === id && identity.storeIds.includes(item.store));
                    if (!version) return json(404, { error: 'Backup version is unavailable.' });
                    const result = await audit.action(identity, 'RESTORE_BACKUP', [version.store], { versionId: id },
                        () => backups.restore(id, identity.storeIds));
                    return json(200, { message: 'Version restored to Recovery.', originalPath: result.originalPath });
                }
            }
            if (route === '/api/session') return json(200, { canManage });
            if (route === '/api/management/status') {
                if (!canManage) return json(403, { error: 'Manage SmartDrive permission is required.' });
                if (!status) return json(503, { error: 'Management status is unavailable.' });
                return json(200, { ...await status(identity), backups: await backupOverview(identity, backups, selections) });
            }
            if (route === '/api/stores') return json(200, library.stores(identity));
            const id = url.searchParams.get('id');
            if (route === '/api/files') return json(200, await library.list(identity, id));

            const preview = await library.preview(identity, id);
            try {
                response.writeHead(200, {
                    'Content-Type': preview.contentType,
                    'Content-Length': preview.size,
                    'Content-Security-Policy': "sandbox; default-src 'none'"
                });
                if (preview.bytes) response.end(preview.bytes);
                else await pipeline(preview.handle.createReadStream({ autoClose: false }), response);
            } finally {
                await preview.handle.close();
            }
        } catch (error) {
            if (response.headersSent) return response.destroy();
            if (error instanceof FileUnavailable || ['ENOENT', 'ENOTDIR', 'EACCES', 'EPERM'].includes(error.code)) {
                return json(404, { error: 'File unavailable.' });
            }
            json(503, { error: branding.displayName + ' is temporarily unavailable.' });
        }
    });
}
