// `import 'server-only'` throws when bundled for the client; under Vitest there
// is no RSC bundler, so we alias it to this no-op so server modules import.
export {};
