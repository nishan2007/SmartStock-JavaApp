import { open, stat } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

function nextOffset(status, size) {
    const ranges = status.nextExpectedRanges;
    if (!Array.isArray(ranges) || ranges.length !== 1 || !/^\d+-$/u.test(ranges[0])) {
        throw new Error('OneDrive returned an unsupported upload range.');
    }
    const offset = Number(ranges[0].slice(0, -1));
    if (!Number.isSafeInteger(offset) || offset < 0 || offset >= size || offset % 327680 !== 0) {
        throw new Error('OneDrive returned an invalid upload offset.');
    }
    return offset;
}

export function oneDriveBackup({ tenantId, clientId, clientSecret, driveId, folderId, uploadState }, request = fetch) {
    if (![tenantId, clientId, clientSecret, driveId, folderId].every(value => typeof value === 'string' && value)) {
        throw new Error('Configure the company OneDrive application and dedicated backup folder.');
    }
    const base = 'https://graph.microsoft.com/v1.0/drives/' + encodeURIComponent(driveId) +
        '/items/' + encodeURIComponent(folderId);
    let token = '';
    let expires = 0;
    async function graph(url, options = {}) {
        if (Date.now() >= expires) {
            const response = await request('https://login.microsoftonline.com/' + encodeURIComponent(tenantId) + '/oauth2/v2.0/token', {
                method: 'POST', redirect: 'error', signal: AbortSignal.timeout(30000),
                body: new URLSearchParams({ grant_type: 'client_credentials', client_id: clientId,
                    client_secret: clientSecret, scope: 'https://graph.microsoft.com/.default' })
            });
            if (!response.ok) throw new Error('Company OneDrive authentication failed.');
            const result = await response.json();
            if (typeof result.access_token !== 'string' || !Number.isFinite(result.expires_in)) {
                throw new Error('Company OneDrive returned an invalid token.');
            }
            token = result.access_token;
            expires = Date.now() + Math.max(0, result.expires_in - 60) * 1000;
        }
        const response = await request(url, { ...options, redirect: 'manual',
            signal: AbortSignal.timeout(120000), headers: { ...options.headers, Authorization: 'Bearer ' + token } });
        if (response.status === 401) expires = 0;
        return response;
    }
    function objectUrl(id, suffix) {
        if (!/^[a-f0-9-]{36}$/u.test(id)) throw new Error('Invalid backup version ID.');
        return base + ':/' + id + suffix + ':';
    }
    function transferUrl(value) {
        const url = new URL(value);
        if (url.protocol !== 'https:' || url.username || url.password) throw new Error('Invalid OneDrive transfer URL.');
        return url;
    }
    async function boundedJson(response, maximum) {
        if (!response.ok || !response.body) throw new Error('OneDrive recovery manifest is unavailable.');
        const chunks = [];
        let length = 0;
        for await (const chunk of response.body) {
            length += chunk.length;
            if (length > maximum) throw new Error('OneDrive recovery response exceeds its size limit.');
            chunks.push(chunk);
        }
        return JSON.parse(Buffer.concat(chunks).toString('utf8'));
    }
    return {
        async *recoveryManifests() {
            let next = base + '/children?$select=id,name,size,file&$top=200';
            const pages = new Set();
            let count = 0;
            while (next) {
                const url = new URL(next);
                if (url.origin !== 'https://graph.microsoft.com' || url.pathname !== new URL(base + '/children').pathname || pages.has(url.href)) {
                    throw new Error('Invalid OneDrive recovery pagination.');
                }
                pages.add(url.href);
                if (pages.size > 1000) throw new Error('OneDrive recovery listing exceeds its page limit.');
                const page = await boundedJson(await graph(url.href), 2 * 1024 * 1024);
                if (!Array.isArray(page.value)) throw new Error('Invalid OneDrive recovery listing.');
                for (const item of page.value) {
                    if (!/^[a-f0-9-]{36}\.json$/u.test(item.name) || !item.file) continue;
                    if (++count > 100000 || !Number.isSafeInteger(item.size) || item.size < 1 || item.size > 65536) {
                        throw new Error('Invalid OneDrive recovery manifest size or count.');
                    }
                    const id = item.name.slice(0, -5);
                    let response = await graph(objectUrl(id, '.json') + '/content');
                    if (response.status === 302) response = await request(transferUrl(response.headers.get('Location')), {
                        redirect: 'error', signal: AbortSignal.timeout(30000)
                    });
                    const record = await boundedJson(response, 65536);
                    if (record.id !== id) throw new Error('OneDrive recovery identity mismatch.');
                    yield record;
                }
                next = page['@odata.nextLink'];
            }
        },
        async upload(id, path, record) {
            const size = (await stat(path)).size;
            const endpoint = objectUrl(id, '.bin');
            if (size === 0) {
                const result = await graph(endpoint + '/content', { method: 'PUT', body: Buffer.alloc(0) });
                if (!result.ok) throw new Error('OneDrive could not save an empty file.');
                const metadata = await graph(objectUrl(id, '.json') + '/content', {
                    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(record)
                });
                if (!metadata.ok) throw new Error('OneDrive could not save the recovery manifest.');
                return;
            }
            let session = await uploadState?.read(id);
            if (session && (session.endpoint !== endpoint || session.size !== size || session.sha256 !== record.sha256)) {
                throw new Error('Upload state does not match the immutable backup snapshot.');
            }
            let offset = 0;
            if (session && !session.complete) {
                const status = await request(transferUrl(session.uploadUrl), {
                    method: 'GET', redirect: 'error', signal: AbortSignal.timeout(30000)
                });
                if (status.status === 404 || status.status === 410) session = null;
                else {
                    if (!status.ok) throw new Error('OneDrive upload status is unavailable.');
                    offset = nextOffset(await status.json(), size);
                }
            }
            if (!session) {
                const opened = await graph(endpoint + '/createUploadSession', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ item: { '@microsoft.graph.conflictBehavior': 'replace' } })
                });
                if (!opened.ok) throw new Error('OneDrive could not start a backup upload.');
                const created = await opened.json();
                session = { endpoint, size, sha256: record.sha256, uploadUrl: transferUrl(created.uploadUrl).href };
                await uploadState?.write(id, session);
            }
            const uploadUrl = transferUrl(session.uploadUrl);
            const handle = await open(path, 'r');
            try {
                while (!session.complete && offset < size) {
                    const length = Math.min(327680 * 16, size - offset);
                    const bytes = Buffer.alloc(length);
                    const read = await handle.read(bytes, 0, length, offset);
                    if (read.bytesRead !== length) throw new Error('Snapshot changed during upload.');
                    const response = await request(uploadUrl, { method: 'PUT', redirect: 'error',
                        signal: AbortSignal.timeout(120000), body: bytes,
                        headers: { 'Content-Range': 'bytes ' + offset + '-' + (offset + length - 1) + '/' + size } });
                    if (!response.ok) throw new Error('OneDrive backup upload was interrupted.');
                    offset += length;
                    if (offset === size && response.status !== 202) {
                        const item = await response.json();
                        if (item.size !== size) throw new Error('OneDrive reported a different backup size.');
                        session.complete = true;
                        await uploadState?.write(id, session);
                    } else {
                        const expected = nextOffset(await response.json(), size);
                        if (expected !== offset) throw new Error('OneDrive acknowledged an unexpected upload range.');
                    }
                }
            } finally { await handle.close(); }
            const metadata = await graph(objectUrl(id, '.json') + '/content', {
                method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(record)
            });
            if (!metadata.ok) throw new Error('OneDrive could not save the recovery manifest.');
            await uploadState?.remove(id);
        },
        async download(id, destination) {
            let response = await graph(objectUrl(id, '.bin') + '/content');
            if (response.status === 302) {
                response = await request(transferUrl(response.headers.get('Location')), {
                    redirect: 'error', signal: AbortSignal.timeout(120000)
                });
            }
            if (!response.ok || !response.body) throw new Error('OneDrive backup download failed.');
            await pipeline(Readable.fromWeb(response.body), createWriteStream(destination, { flags: 'wx' }));
        }
    };
}
