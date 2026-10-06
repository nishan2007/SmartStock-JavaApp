export const instanceId=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
export const originSecrets=JSON.stringify({[instanceId(1)]:'one-test-secret-'.repeat(3),[instanceId(2)]:'two-test-secret-'.repeat(3)});
