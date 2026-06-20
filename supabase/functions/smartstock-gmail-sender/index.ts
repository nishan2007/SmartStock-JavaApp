const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type EmailRequest = {
  outbox_id?: number;
  from_email: string;
  from_name?: string | null;
  to_email: string;
  bcc_email?: string | null;
  subject: string;
  body_text?: string | null;
  body_html?: string | null;
  attachment_name?: string | null;
  attachment_content_type?: string | null;
  attachment_body?: string | null;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return json({ error: "POST required" }, 405);
  }

  const sharedKey = Deno.env.get("SMARTSTOCK_EMAIL_FUNCTION_KEY") ?? "";
  if (sharedKey) {
    const authorization = req.headers.get("authorization") ?? "";
    const apikey = req.headers.get("apikey") ?? "";
    if (authorization !== `Bearer ${sharedKey}` && apikey !== sharedKey) {
      return json({ error: "Unauthorized" }, 401);
    }
  }

  let payload: EmailRequest;
  try {
    payload = await req.json();
  } catch {
    return json({ error: "Invalid JSON payload" }, 400);
  }

  const validationError = validatePayload(payload);
  if (validationError) {
    return json({ error: validationError }, 400);
  }

  let gmailResponse: Response;
  try {
    const accessToken = await gmailAccessToken(payload.from_email);
    const raw = buildMimeMessage(payload);
    gmailResponse = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/${encodeURIComponent(payload.from_email)}/messages/send`,
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ raw }),
      },
    );
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : "Gmail sender failed" }, 500);
  }

  const responseText = await gmailResponse.text();
  if (!gmailResponse.ok) {
    return json({
      error: "Gmail send failed",
      status: gmailResponse.status,
      detail: safeDetail(responseText),
    }, 502);
  }

  let gmailBody: unknown = {};
  try {
    gmailBody = JSON.parse(responseText);
  } catch {
    gmailBody = { raw: responseText };
  }
  return json({ ok: true, outbox_id: payload.outbox_id ?? null, gmail: gmailBody });
});

async function gmailAccessToken(fromEmail: string): Promise<string> {
  const clientId = requireEnv("GMAIL_CLIENT_ID");
  const clientSecret = requireEnv("GMAIL_CLIENT_SECRET");
  const refreshToken = refreshTokenFor(fromEmail);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      refresh_token: refreshToken,
      grant_type: "refresh_token",
    }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || typeof body.access_token !== "string") {
    throw new Error(`Gmail token refresh failed for ${fromEmail}: ${JSON.stringify(body).slice(0, 300)}`);
  }
  return body.access_token;
}

function refreshTokenFor(fromEmail: string): string {
  const tokensJson = requireEnv("GMAIL_REFRESH_TOKENS_JSON");
  let tokens: Record<string, string>;
  try {
    tokens = JSON.parse(tokensJson);
  } catch {
    throw new Error("GMAIL_REFRESH_TOKENS_JSON must be a JSON object keyed by sender email.");
  }
  const token = tokens[fromEmail.toLowerCase()] ?? tokens[fromEmail];
  if (!token) {
    throw new Error(`No Gmail refresh token configured for sender ${fromEmail}.`);
  }
  return token;
}

function buildMimeMessage(payload: EmailRequest): string {
  const boundary = `smartstock_${crypto.randomUUID().replaceAll("-", "")}`;
  const from = formatAddress(payload.from_email, payload.from_name ?? "");
  const headers = [
    `From: ${from}`,
    `To: ${payload.to_email}`,
    payload.bcc_email ? `Bcc: ${payload.bcc_email}` : "",
    `Subject: ${mimeHeader(payload.subject)}`,
    "MIME-Version: 1.0",
    `Content-Type: multipart/mixed; boundary="${boundary}"`,
  ].filter(Boolean).join("\r\n");

  const alternativeBoundary = `${boundary}_alt`;
  const text = payload.body_text ?? "";
  const html = payload.body_html ?? "";
  const parts = [
    `--${boundary}`,
    `Content-Type: multipart/alternative; boundary="${alternativeBoundary}"`,
    "",
    `--${alternativeBoundary}`,
    'Content-Type: text/plain; charset="UTF-8"',
    "Content-Transfer-Encoding: base64",
    "",
    base64(text),
    `--${alternativeBoundary}`,
    'Content-Type: text/html; charset="UTF-8"',
    "Content-Transfer-Encoding: base64",
    "",
    base64(html || `<pre>${escapeHtml(text)}</pre>`),
    `--${alternativeBoundary}--`,
  ];

  if (payload.attachment_name && payload.attachment_body) {
    parts.push(
      `--${boundary}`,
      `Content-Type: ${payload.attachment_content_type || "text/plain; charset=utf-8"}; name="${escapeHeaderParam(payload.attachment_name)}"`,
      "Content-Transfer-Encoding: base64",
      `Content-Disposition: attachment; filename="${escapeHeaderParam(payload.attachment_name)}"`,
      "",
      base64(payload.attachment_body),
    );
  }
  parts.push(`--${boundary}--`, "");
  return base64Url(`${headers}\r\n\r\n${parts.join("\r\n")}`);
}

function validatePayload(payload: EmailRequest): string | null {
  if (!payload || typeof payload !== "object") return "Missing payload.";
  if (!looksLikeEmail(payload.from_email)) return "from_email is required.";
  if (!looksLikeEmail(payload.to_email)) return "to_email is required.";
  if (payload.bcc_email && !looksLikeEmail(payload.bcc_email)) return "bcc_email is invalid.";
  if (!payload.subject || payload.subject.trim().length === 0) return "subject is required.";
  return null;
}

function looksLikeEmail(value: unknown): value is string {
  return typeof value === "string" && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value);
}

function formatAddress(email: string, name: string): string {
  const cleanName = name.trim();
  return cleanName ? `${mimeHeader(cleanName)} <${email}>` : email;
}

function mimeHeader(value: string): string {
  if (/^[\x20-\x7E]*$/.test(value)) return value.replaceAll("\r", "").replaceAll("\n", "");
  return `=?UTF-8?B?${base64(value)}?=`;
}

function escapeHeaderParam(value: string): string {
  return value.replaceAll("\\", "\\\\").replaceAll('"', '\\"').replaceAll("\r", "").replaceAll("\n", "");
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function base64(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function base64Url(value: string): string {
  return base64(value).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function requireEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`${name} is not configured.`);
  return value;
}

function safeDetail(value: string): string {
  return value.replace(/\s+/g, " ").trim().slice(0, 500);
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
