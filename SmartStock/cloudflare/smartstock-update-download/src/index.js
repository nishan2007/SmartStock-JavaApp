const MAX_FUTURE_SECONDS = 10 * 60;
const ALLOWED_CLOCK_SKEW_SECONDS = 30;

export default {
  async fetch(request, env) {
    if (request.method !== "GET") {
      return new Response("Method not allowed", {
        status: 405,
        headers: { allow: "GET" }
      });
    }

    const url = new URL(request.url);
    const encodedPath = url.pathname.slice(1);
    const expiresText = url.searchParams.get("expires") || "";
    const signature = url.searchParams.get("signature") || "";
    const expires = Number(expiresText);
    const now = Math.floor(Date.now() / 1000);

    if (!validEncodedPath(encodedPath)
        || !Number.isSafeInteger(expires)
        || expires < now - ALLOWED_CLOCK_SKEW_SECONDS
        || expires > now + MAX_FUTURE_SECONDS + ALLOWED_CLOCK_SKEW_SECONDS
        || !/^[a-f0-9]{64}$/.test(signature)) {
      return privateText("Invalid or expired update link", 403);
    }

    const canonical = canonicalRequest(encodedPath, expires);
    if (!await validSignature(env.SMARTSTOCK_UPDATE_SIGNING_SECRET, canonical, signature)) {
      return privateText("Invalid or expired update link", 403);
    }

    let objectKey;
    try {
      objectKey = encodedPath.split("/").map(decodeURIComponent).join("/");
    } catch {
      return privateText("Invalid update path", 400);
    }
    const object = await env.UPDATE_BUCKET.get(objectKey);
    if (!object) return privateText("Update not found", 404);

    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set("etag", object.httpEtag);
    headers.set("content-length", String(object.size));
    headers.set("cache-control", "private, no-store");
    headers.set("x-content-type-options", "nosniff");
    if (!headers.has("content-type")) {
      headers.set("content-type", "application/zip");
    }
    return new Response(object.body, { status: 200, headers });
  }
};

export function canonicalRequest(encodedPath, expires) {
  return `GET\n/${encodedPath}\n${expires}`;
}

export function validEncodedPath(encodedPath) {
  if (!encodedPath || encodedPath.includes("\\") || encodedPath.startsWith("/")) return false;
  try {
    const parts = encodedPath.split("/").map(decodeURIComponent);
    return !parts.some((part) => !part || part === "." || part === "..");
  } catch {
    return false;
  }
}

export async function validSignature(secret, canonical, expectedHex) {
  if (typeof secret !== "string" || secret.length < 32) return false;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const actual = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(canonical)
  );
  const expected = hexToBytes(expectedHex);
  return expected !== null && constantTimeEqual(new Uint8Array(actual), expected);
}

function hexToBytes(value) {
  if (!/^[a-f0-9]{64}$/.test(value)) return null;
  return Uint8Array.from(value.match(/../g), (byte) => Number.parseInt(byte, 16));
}

function constantTimeEqual(left, right) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left[index] ^ right[index];
  }
  return difference === 0;
}

function privateText(message, status) {
  return new Response(message, {
    status,
    headers: {
      "content-type": "text/plain; charset=utf-8",
      "cache-control": "private, no-store",
      "x-content-type-options": "nosniff"
    }
  });
}
