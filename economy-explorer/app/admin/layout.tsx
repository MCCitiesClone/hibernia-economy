import { getViewer } from '@/lib/auth/viewer';
import { PrivacyGate } from '@/components/PrivacyGate';
import { AdminNav } from '@/components/AdminNav';

// Wraps every /admin/* page: gates the whole area to admins (so non-admins never
// even see the tool list) and renders the shared admin sub-navbar above the page.
// Individual pages keep their own gate as defence-in-depth.
export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const viewer = await getViewer();
  if (viewer.anon) return <PrivacyGate kind="login" title="Admin" hint="Sign in with an admin account to use the admin tools." />;
  if (viewer.role !== 'admin') return <PrivacyGate kind="private" title="Admin only" />;
  return (
    <>
      <AdminNav />
      {children}
    </>
  );
}
