const crypto = require("crypto");

const TOKEN_PREFIX = "diya_agent_v1";
const DEFAULT_TTL_SECONDS = 24 * 60 * 60; // 24 hours

function base64UrlEncode(input) {
  return Buffer.from(input).toString("base64url");
}

function base64UrlDecode(input) {
  return Buffer.from(input, "base64url").toString("utf8");
}

function hashToken(token) {
  return crypto.createHash("sha256").update(String(token)).digest("hex");
}

function timingSafeHexEqual(aHex, bHex) {
  if (typeof aHex !== "string" || typeof bHex !== "string") return false;
  if (aHex.length !== bHex.length || aHex.length === 0) return false;
  const aBuf = Buffer.from(aHex, "hex");
  const bBuf = Buffer.from(bHex, "hex");
  if (aBuf.length !== bBuf.length) return false;
  return crypto.timingSafeEqual(aBuf, bBuf);
}

function createSignedAgentToken({ uid, deviceId, secret, ttlSeconds = DEFAULT_TTL_SECONDS }) {
  const safeTtl = Math.max(60, Math.min(Number(ttlSeconds) || DEFAULT_TTL_SECONDS, 7 * 24 * 60 * 60));
  const iat = Date.now();
  const exp = iat + safeTtl * 1000;
  const payload = {
    typ: TOKEN_PREFIX,
    uid,
    deviceId,
    jti: crypto.randomBytes(16).toString("hex"),
    iat,
    exp,
  };
  const encodedPayload = base64UrlEncode(JSON.stringify(payload));
  const signature = crypto
    .createHmac("sha256", secret)
    .update(`${TOKEN_PREFIX}.${encodedPayload}`)
    .digest("base64url");
  const token = `${TOKEN_PREFIX}.${encodedPayload}.${signature}`;
  return {
    token,
    tokenHash: hashToken(token),
    iat,
    exp,
    expiresAt: new Date(exp).toISOString(),
  };
}

function verifySignedAgentToken(token, secret) {
  if (typeof token !== "string" || !token.startsWith(`${TOKEN_PREFIX}.`)) {
    return null;
  }
  const parts = token.split(".");
  if (parts.length !== 3) return null;

  const [prefix, encodedPayload, providedSig] = parts;
  if (prefix !== TOKEN_PREFIX || !encodedPayload || !providedSig) return null;

  const expectedSig = crypto
    .createHmac("sha256", secret)
    .update(`${prefix}.${encodedPayload}`)
    .digest("base64url");

  const sigBufA = Buffer.from(providedSig);
  const sigBufB = Buffer.from(expectedSig);
  if (sigBufA.length !== sigBufB.length || !crypto.timingSafeEqual(sigBufA, sigBufB)) {
    return null;
  }

  try {
    const payload = JSON.parse(base64UrlDecode(encodedPayload));
    if (
      payload?.typ !== TOKEN_PREFIX ||
      typeof payload.uid !== "string" ||
      typeof payload.deviceId !== "string" ||
      typeof payload.exp !== "number"
    ) {
      return null;
    }
    if (Date.now() >= payload.exp) {
      return { expired: true, payload };
    }
    return { expired: false, payload, tokenHash: hashToken(token) };
  } catch {
    return null;
  }
}

module.exports = {
  TOKEN_PREFIX,
  DEFAULT_TTL_SECONDS,
  hashToken,
  timingSafeHexEqual,
  createSignedAgentToken,
  verifySignedAgentToken,
};
