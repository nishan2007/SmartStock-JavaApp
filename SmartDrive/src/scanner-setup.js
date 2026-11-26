import { isIP } from 'node:net';

export function scannerMetadata(scanner) {
    const text = (value, fallback, maximum) => {
        const result = value ?? fallback;
        if (typeof result !== 'string' || !result.trim() || result.length > maximum || /[\x00-\x1f\x7f]/u.test(result)) throw new Error('Invalid scanner display metadata.');
        return result.trim();
    };
    const displayName = text(scanner.displayName, scanner.id, 80);
    const model = scanner.model == null ? null : text(scanner.model, '', 120);
    const reservedAddress = scanner.reservedAddress ?? null;
    if (reservedAddress !== null && (typeof reservedAddress !== 'string' || !isIP(reservedAddress))) throw new Error('Use a valid reserved scanner IP address.');
    return { displayName, model, reservedAddress };
}

export function scannerSetup(scanner, { serverName, shareName }) {
    if (typeof serverName !== 'string' || typeof shareName !== 'string' || typeof scanner.id !== 'string' || typeof scanner.account !== 'string' ||
        !/^[A-Za-z][A-Za-z0-9-]{0,14}$/u.test(serverName) || !/^[A-Za-z][A-Za-z0-9_-]{0,39}$/u.test(shareName) ||
        !/^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$/u.test(scanner.id) || !/^sdscan_[a-z0-9_]{1,12}$/u.test(scanner.account) ||
        typeof scanner.storeId !== 'string' || !/^[1-9]\d{0,8}$/u.test(scanner.storeId)) throw new Error('Invalid scanner connection configuration.');
    return { ...scannerMetadata(scanner), protocol: 'SMB2/3', server: serverName, share: 'ScanIntake', folder: scanner.id,
        username: serverName + '\\' + scanner.account,
        destination: '\\\\' + serverName + '\\ScanIntake\\' + scanner.id,
        publishedFolder: '\\\\' + serverName + '\\' + shareName + '\\' + scanner.storeId + '\\Scans\\' + scanner.id,
        steps: [
            'Reserve this copier’s actual IP address in DHCP or configure its approved static address. This page does not change DHCP or firewall rules.',
            'In the copier’s Scan and Send address book, create a file destination using SMB2/3. Enter the server, share, folder and username shown here, plus the copier’s current password.',
            'Send a one-page test PDF from the copier. A server-side connection check alone cannot verify scanning.',
            'Refresh scanner status, then check the published folder and the file preview in the web app. Malware or format failures go to quarantine.',
            'If no file arrives, check copier credentials, name resolution and the approved network/firewall addresses. If SMB2/3 is unavailable, keep access disabled and confirm supported firmware or the isolated intake gateway before proceeding. Do not enable SMB1 on this server.'
        ] };
}
