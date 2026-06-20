// Discord webhook detection — mirrors the dispatcher's DiscordWebhook.java so the
// UI can flag a target the backend will deliver as a rich embed. Pure (no
// server-only deps) so both the RSC table and the client create-form can use it.

const HOSTS = new Set(['discord.com', 'discordapp.com', 'canary.discord.com', 'ptb.discord.com']);
const PATH = /^\/api(\/v\d+)?\/webhooks\/\d+\/.+/;

/** True if the URL is a Discord channel webhook execute URL. */
export function isDiscordWebhookUrl(raw: string): boolean {
  let u: URL;
  try {
    u = new URL(raw.trim());
  } catch {
    return false;
  }
  return HOSTS.has(u.hostname.toLowerCase()) && PATH.test(u.pathname);
}
