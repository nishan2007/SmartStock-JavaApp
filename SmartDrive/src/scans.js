import { randomUUID } from 'node:crypto';
import { link, lstat, mkdir, readFile, readdir, rename, unlink, writeFile } from 'node:fs/promises';
import { basename, extname, isAbsolute, join, relative, sep } from 'node:path';

const types = new Set(['.pdf', '.jpg', '.jpeg', '.tif', '.tiff']);
const signature = info => [info.dev, info.ino, info.size, info.mtimeMs, info.birthtimeMs].join(':');
const inside = (root, target) => {
    const path = relative(root, target);
    return path === '' || (!isAbsolute(path) && path !== '..' && !path.startsWith('..' + sep));
};
const plainDirectory = async path => {
    const info = await lstat(path);
    if (!info.isDirectory() || info.isSymbolicLink()) throw new Error('Invalid scanner directory.');
    return info;
};

function validContent(bytes, extension) {
    if (extension === '.pdf') {
        return bytes.subarray(0, 5).toString() === '%PDF-' &&
            /%%EOF\s*$/u.test(bytes.subarray(-1024).toString('latin1'));
    }
    if (extension === '.jpg' || extension === '.jpeg') {
        return bytes[0] === 255 && bytes[1] === 216 && bytes.at(-2) === 255 && bytes.at(-1) === 217;
    }
    return ['49492a00', '4d4d002a'].includes(bytes.subarray(0, 4).toString('hex'));
}

export function scanIntake({ scanners, storeRoots, stateRoot, copyClosed, malwareCheck, resetAcl,
    isEnabled = async () => true, maxBytes = 50 * 1024 * 1024 }) {
    if (!isAbsolute(stateRoot) || Object.values(storeRoots).some(root => inside(root, stateRoot))) {
        throw new Error('Scanner state must be an absolute directory outside staff storage.');
    }
    function configuredScanners() {
        const ids = new Set();
        const intakes = [];
        return scanners.map(scanner => {
            const { id, storeId, intake } = scanner;
            if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$/u.test(id) || ids.has(id.toLowerCase()) ||
                !Object.hasOwn(storeRoots, storeId) || !isAbsolute(intake) ||
                Object.values(storeRoots).some(root => inside(root, intake) || inside(intake, root)) ||
                inside(stateRoot, intake) || inside(intake, stateRoot) ||
                intakes.some(other => inside(other, intake) || inside(intake, other))) {
                throw new Error('Each scanner needs a unique ID, assigned store and separate private intake.');
            }
            ids.add(id.toLowerCase());
            intakes.push(intake);
            return { ...scanner, root: storeRoots[storeId], work: join(stateRoot, id) };
        });
    }
    configuredScanners();
    let previous = new Map();
    let running = false;

    async function handle(scanner, job) {
        const source = join(scanner.work, job);
        const original = await lstat(source);
        if (!original.isFile() || original.isSymbolicLink() || original.nlink !== 1) {
            throw new Error('Invalid scanner upload.');
        }
        const destination = join(scanner.root, 'Scans', scanner.id);
        const final = join(destination, job);
        const staging = source + '.pending';
        try {
            // A successful publish can precede a restart or failure to remove the claimed input.
            const published = await lstat(final).catch(error => {
                if (error.code !== 'ENOENT') throw error;
            });
            if (published) {
                if (!published.isFile() || published.isSymbolicLink()) throw new Error('Invalid scan destination.');
                await resetAcl(final);
                await unlink(source);
                return;
            }
            await unlink(staging).catch(error => { if (error.code !== 'ENOENT') throw error; });
            let rejected = original.size < 4 || original.size > maxBytes;
            if (!rejected) {
                await copyClosed(source, staging);
                const bytes = await readFile(staging);
                rejected = bytes.length > maxBytes || !validContent(bytes, extname(job));
                if (!rejected) rejected = !(await malwareCheck(staging));
            }
            if (rejected) {
                const quarantine = join(scanner.work, 'quarantine');
                await mkdir(quarantine, { recursive: true });
                await plainDirectory(quarantine);
                await rename(source, join(quarantine, job));
            } else {
                await link(staging, final);
                await resetAcl(final);
                await unlink(source);
            }
            await writeFile(join(scanner.work, 'status.json'),
                JSON.stringify({ lastResult: rejected ? 'quarantined' : 'published', at: new Date().toISOString(), file: job }));
        } finally {
            await unlink(staging).catch(error => { if (error.code !== 'ENOENT') throw error; });
        }
    }

    async function scan(scanner, seen) {
        const sourceVolume = await plainDirectory(scanner.intake);
        await mkdir(scanner.work, { recursive: true });
        const workVolume = await plainDirectory(scanner.work);
        await plainDirectory(scanner.root);
        const scans = join(scanner.root, 'Scans');
        const destination = join(scans, scanner.id);
        await mkdir(scans, { recursive: true });
        await plainDirectory(scans);
        await mkdir(destination, { recursive: true });
        const destinationVolume = await plainDirectory(destination);
        if (sourceVolume.dev !== workVolume.dev || workVolume.dev !== destinationVolume.dev) {
            throw new Error('Scanner intake, state and publishing folders must share a volume.');
        }
        for (const name of await readdir(scanner.work)) {
            if (/^scan-[0-9T-]+-[a-f0-9-]+\.(?:pdf|jpe?g|tiff?)$/u.test(name)) await handle(scanner, name);
        }
        for (const entry of await readdir(scanner.intake, { withFileTypes: true })) {
            const extension = extname(entry.name).toLowerCase();
            if (!entry.isFile() || !types.has(extension) || entry.name.startsWith('.')) continue;
            const path = join(scanner.intake, basename(entry.name));
            const info = await lstat(path);
            if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1) continue;
            if (previous.get(path) !== signature(info)) {
                seen.set(path, signature(info));
                continue;
            }
            const timestamp = new Date().toISOString().slice(0, 19).replaceAll(':', '-');
            const name = 'scan-' + timestamp + '-' + randomUUID() + extension;
            try {
                await rename(path, join(scanner.work, name));
            } catch (error) {
                if (['EBUSY', 'EPERM', 'EACCES', 'ENOENT'].includes(error.code)) continue;
                throw error;
            }
            await handle(scanner, name);
        }
    }

    return async () => {
        if (running) return [];
        running = true;
        const seen = new Map();
        try {
            return await Promise.allSettled(configuredScanners().filter(scanner => scanner.enabled !== false)
                .map(async scanner => { if (await isEnabled(scanner.id)) await scan(scanner, seen); }));
        } finally {
            previous = seen;
            running = false;
        }
    };
}
