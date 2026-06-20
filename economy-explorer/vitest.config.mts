import { defineConfig } from 'vitest/config';
import tsconfigPaths from 'vite-tsconfig-paths';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  plugins: [tsconfigPaths()],
  resolve: {
    alias: {
      // `import 'server-only'` throws outside an RSC bundle; stub it so server
      // modules (lib/auth, lib/sql) can be imported under Vitest.
      'server-only': fileURLToPath(new URL('./test/stubs/server-only.ts', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['test/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'text-summary', 'html', 'lcov'],
      include: ['lib/**/*.ts'],
      exclude: ['lib/**/*.d.ts', 'lib/db.ts', 'lib/auth/authjs.ts'],
    },
  },
});
