'use server';
import { signIn } from '@/lib/auth/authjs';

// Extracted from the (now client) header so the login button — a server action —
// can live inside a client component. Single IdP: straight to Keycloak.
export async function loginAction() {
  await signIn('keycloak', { redirectTo: '/' });
}
