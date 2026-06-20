import 'next-auth';

declare module 'next-auth' {
  interface Session {
    keycloakSub?: string;
    minecraftUuid?: string;
    minecraftName?: string;
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    minecraftUuid?: string;
    minecraftName?: string;
  }
}
