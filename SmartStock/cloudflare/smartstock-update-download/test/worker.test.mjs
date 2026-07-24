import test from "node:test";
import assert from "node:assert/strict";
import { canonicalRequest, validEncodedPath, validSignature } from "../src/index.js";

const SECRET = "0123456789abcdef0123456789abcdef";

test("canonical request matches the SmartStock server", () => {
  assert.equal(
    canonicalRequest("mac/1.0.27/SmartStock%20Update.zip", 1600),
    "GET\n/mac/1.0.27/SmartStock%20Update.zip\n1600"
  );
});

test("accepts safe encoded object paths", () => {
  assert.equal(validEncodedPath("mac/1.0.27/SmartStock%20Update.zip"), true);
  assert.equal(validEncodedPath("mac/../release.zip"), false);
  assert.equal(validEncodedPath("mac/%2e%2e/release.zip"), false);
  assert.equal(validEncodedPath("mac//release.zip"), false);
});

test("validates the Java-generated HMAC signature", async () => {
  assert.equal(await validSignature(
    SECRET,
    "GET\n/mac/1.0.27/SmartStock%20Update.zip\n1600",
    "bb273053a23ef7523d35c73b9e7f865538134c7cb600cdc198f9b2fafe86a06a"
  ), true);
  assert.equal(await validSignature(
    SECRET,
    "GET\n/mac/1.0.27/SmartStock%20Update.zip\n1600",
    "03f798c84505a0a8db7d9a32fbbf352f164d986f034af05506630546caa29a62"
  ), false);
});
