/**
 * Per-tenant currency symbol. Read once at module-load from env so SSR can
 * format amounts without a per-request lookup. DemocracyCraft = "$",
 * StateCraft = "£"; falls back to "$" when unset.
 */
export const CURRENCY_SYMBOL: string =
  process.env.CURRENCY_SYMBOL && process.env.CURRENCY_SYMBOL.trim().length > 0
    ? process.env.CURRENCY_SYMBOL.trim()
    : '$';
