import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="explorer-error">
      <div className="explorer-error-code">404</div>
      <h1>Page not found</h1>
      <p>That account, firm, transaction, or page doesn&rsquo;t exist or has moved.</p>
      <Link href="/" className="btn btn-primary" prefetch={false}>Back to overview</Link>
    </div>
  );
}
