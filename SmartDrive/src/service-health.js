// Read-only dependency probes: bounded, coalesced and stripped of exception text.
export function serviceHealth(checks, { timeoutMs = 3500, cacheMs = 5000 } = {}) {
    if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || !Number.isInteger(cacheMs) || cacheMs < 0 ||
        Object.entries(checks).some(([name, check]) => !/^[a-z][a-z0-9-]{0,39}$/u.test(name) || typeof check !== 'function')) throw new Error('Invalid service health configuration.');
    let pending, cached, expires = 0;
    return async () => {
        if (cached && Date.now() < expires) return structuredClone(cached);
        if (!pending) {
            pending = Promise.all(Object.entries(checks).map(async ([name, check]) => {
                let timer;
                try {
                    await Promise.race([Promise.resolve().then(check), new Promise((_, reject) => {
                        timer = setTimeout(() => reject(new Error('Health check timeout.')), timeoutMs);
                    })]);
                    return [name, { state: 'ready', checkedAt: new Date().toISOString() }];
                } catch { return [name, { state: 'unavailable', checkedAt: new Date().toISOString() }]; }
                finally { clearTimeout(timer); }
            })).then(entries => { cached = Object.fromEntries(entries);expires = Date.now() + cacheMs;return cached; })
                .finally(() => { pending = undefined; });
        }
        return structuredClone(await pending);
    };
}
