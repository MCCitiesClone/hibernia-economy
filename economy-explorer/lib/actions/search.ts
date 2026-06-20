'use server';
import { searchGlobal, type SearchResult } from '@/lib/sql/search';

/**
 * Lightweight server action driving the header typeahead. Returns at most
 * `limit` per kind so the popover lists a few accounts / firms / players
 * without scrolling. Backed by the same SQL as the /search page.
 */
export async function searchPreview(q: string, limit = 4): Promise<SearchResult[]> {
  const term = q.trim();
  if (term.length < 2) return [];
  return await searchGlobal(term, limit);
}
