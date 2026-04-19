/**
 * In-memory JWT store for the admin portal.
 *
 * Keeping the token in a module-level variable instead of localStorage
 * prevents XSS scripts from reading it via document APIs. The trade-off
 * is that a full page refresh requires the user to log in again, which is
 * the correct security behaviour for an admin portal.
 *
 * For production deployments, prefer httpOnly cookies set by the backend
 * over this approach to also protect against token leakage in network logs.
 */

let _token: string | null = null;

export const tokenStore = {
  get(): string | null {
    return _token;
  },
  set(token: string): void {
    _token = token;
  },
  clear(): void {
    _token = null;
    // Also clear any legacy localStorage value left from a previous session.
    try { localStorage.removeItem('admin_jwt'); } catch (_) { /* ignore */ }
  },
  isAuthenticated(): boolean {
    return _token !== null;
  },
};
