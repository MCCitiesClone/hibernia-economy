import 'server-only';
import { SignJWT } from 'jose';

/**
 * Mints an in-game-format API JWT. Matches the format produced by
 * treasury-rest-api's AuthService.buildJwt:
 *
 * - header: { alg: 'HS256', kid: <key_id as string> }
 * - claims: sub (owner UUID), type (key_type), acc?, firm?, jti, iat, exp
 *
 * Requires JWT_SECRET in env (the same `api.jwt-secret` Spring uses); if
 * missing the function throws. Callers should gate on env presence so the
 * admin UI can show a clear "not configured" state instead of a 500.
 */
export async function signApiKeyJwt(args: {
  keyId: number;
  ownerUuid: string;
  keyType: string;
  accountId: number | null;
  firmId: number | null;
  jti: string;
  iat: Date;
  exp: Date;
}): Promise<string> {
  const secret = process.env.JWT_SECRET;
  if (!secret) {
    throw new Error('JWT_SECRET not configured');
  }
  const key = new TextEncoder().encode(secret);
  const payload: Record<string, string | number> = { type: args.keyType };
  if (args.accountId !== null) payload.acc = args.accountId;
  if (args.firmId !== null) payload.firm = args.firmId;

  return await new SignJWT(payload)
    .setProtectedHeader({ alg: 'HS256', kid: String(args.keyId) })
    .setSubject(args.ownerUuid)
    .setJti(args.jti)
    .setIssuedAt(Math.floor(args.iat.getTime() / 1000))
    .setExpirationTime(Math.floor(args.exp.getTime() / 1000))
    .sign(key);
}

export function jwtSigningConfigured(): boolean {
  return !!process.env.JWT_SECRET;
}
