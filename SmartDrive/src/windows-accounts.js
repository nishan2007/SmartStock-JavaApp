import { connect } from 'node:net';

export function windowsAccounts() {
    if (process.platform !== 'win32') throw new Error('Workstation accounts require the Windows guest.');
    return request => new Promise((resolve, reject) => {
        const body = Buffer.from(JSON.stringify(request));
        if (body.length > 4096) return reject(new Error('Invalid workstation request.'));
        const length = Buffer.alloc(4);
        length.writeInt32LE(body.length);
        const socket = connect('\\\\.\\pipe\\SmartDriveAccounts.v1');
        let received = '';
        socket.setTimeout(request.action === 'health' ? 3000 : request.action === 'scanner-create' ? 60000 : 15000, () => socket.destroy(new Error('Account service timed out.')));
        socket.on('error', reject);
        socket.on('connect', () => socket.write(Buffer.concat([length, body])));
        socket.on('data', bytes => {
            received += bytes.toString('utf8');
            if (received.length > (request.action === 'workstation-activity' ? 65536 : 1024)) return socket.destroy(new Error('Invalid account service response.'));
            if (!received.includes('\n')) return;
            socket.end();
            try {
                const result = JSON.parse(received.trim());
                if (result.ok !== true) throw new Error('Account service rejected the request.');
                resolve(result.data ?? undefined);
            } catch (error) { reject(error); }
        });
        socket.on('end', () => { if (!received.includes('\n')) reject(new Error('Account service disconnected.')); });
    });
}
