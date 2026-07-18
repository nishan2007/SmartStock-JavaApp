type EmailRequest = {
  outbox_id?: number; from_email: string; from_name?: string | null; to_email: string;
  bcc_email?: string | null; subject: string; body_text?: string | null; body_html?: string | null;
  attachment_name?: string | null; attachment_content_type?: string | null; attachment_body?: string | null;
};

// Supabase verifies the caller JWT before this handler runs. The optional key
// is defense in depth for installed store servers.
Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST required" }, 405);
  const sharedKey = Deno.env.get("SMARTSTOCK_EMAIL_FUNCTION_KEY") ?? "";
  if (sharedKey && req.headers.get("x-smartstock-email-key") !== sharedKey) return json({ error: "Unauthorized" }, 401);
  let email: EmailRequest;
  try { email = await req.json(); } catch { return json({ error: "Invalid JSON payload" }, 400); }
  if (!validEmail(email?.from_email) || !validEmail(email?.to_email) || !email.subject?.trim()) return json({ error: "Valid sender, recipient, and subject are required." }, 400);
  if (email.bcc_email && !validEmail(email.bcc_email)) return json({ error: "bcc_email is invalid." }, 400);
  try {
    const response = await fetch(`https://gmail.googleapis.com/gmail/v1/users/${encodeURIComponent(email.from_email)}/messages/send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${await gmailAccessToken(email.from_email)}`, "Content-Type": "application/json" },
      body: JSON.stringify({ raw: mime(email) }),
    });
    const text = await response.text();
    if (!response.ok) return json({ error: "Gmail send failed", status: response.status, detail: text.slice(0, 500) }, 502);
    return json({ ok: true, outbox_id: email.outbox_id ?? null, gmail: JSON.parse(text) });
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : "Gmail sender failed" }, 500);
  }
});

async function gmailAccessToken(fromEmail: string): Promise<string> {
  const tokens = JSON.parse(env("GMAIL_REFRESH_TOKENS_JSON")) as Record<string, string>;
  const refresh = tokens[fromEmail.toLowerCase()] ?? tokens[fromEmail];
  if (!refresh) throw new Error(`No Gmail refresh token configured for sender ${fromEmail}.`);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ client_id: env("GMAIL_CLIENT_ID"), client_secret: env("GMAIL_CLIENT_SECRET"), refresh_token: refresh, grant_type: "refresh_token" }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || typeof body.access_token !== "string") throw new Error("Gmail token refresh failed.");
  return body.access_token;
}

function mime(email: EmailRequest): string {
  const boundary = `smartstock_${crypto.randomUUID().replaceAll("-", "")}`;
  const from = email.from_name?.trim() ? `${header(email.from_name)} <${email.from_email}>` : email.from_email;
  const headers = [`From: ${from}`, `To: ${email.to_email}`, email.bcc_email ? `Bcc: ${email.bcc_email}` : "", `Subject: ${header(email.subject)}`, "MIME-Version: 1.0", `Content-Type: multipart/mixed; boundary="${boundary}"`].filter(Boolean).join("\r\n");
  const alt = `${boundary}_alt`;
  const parts = [`--${boundary}`, `Content-Type: multipart/alternative; boundary="${alt}"`, "", `--${alt}`, 'Content-Type: text/plain; charset="UTF-8"', "Content-Transfer-Encoding: base64", "", b64(email.body_text ?? ""), `--${alt}`, 'Content-Type: text/html; charset="UTF-8"', "Content-Transfer-Encoding: base64", "", b64(email.body_html || `<pre>${html(email.body_text ?? "")}</pre>`), `--${alt}--`];
  if (email.attachment_name && email.attachment_body) {
    const name = email.attachment_name.replaceAll("\r", "").replaceAll("\n", "").replaceAll("\\", "").replaceAll('"', "");
    parts.push(`--${boundary}`, `Content-Type: ${email.attachment_content_type || "text/plain; charset=utf-8"}; name="${name}"`, "Content-Transfer-Encoding: base64", `Content-Disposition: attachment; filename="${name}"`, "", b64(email.attachment_body));
  }
  parts.push(`--${boundary}--`, "");
  return b64(`${headers}\r\n\r\n${parts.join("\r\n")}`).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function validEmail(value: unknown): value is string { return typeof value === "string" && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value); }
function env(name: string): string { const value = Deno.env.get(name); if (!value) throw new Error(`${name} is not configured.`); return value; }
function header(value: string): string { const clean = value.replaceAll("\r", "").replaceAll("\n", ""); return /^[\x20-\x7E]*$/.test(clean) ? clean : `=?UTF-8?B?${b64(clean)}?=`; }
function html(value: string): string { return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;"); }
function b64(value: string): string { const bytes = new TextEncoder().encode(value); let binary = ""; for (const byte of bytes) binary += String.fromCharCode(byte); return btoa(binary); }
function json(body: unknown, status = 200): Response { return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }); }
