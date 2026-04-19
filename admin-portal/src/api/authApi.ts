import { apiClient } from './apiClient';
import { tokenStore } from './tokenStore';

export interface LoginResponse {
  token: string;
  role: string;
  expiresInHours: number;
}

export async function adminLogin(username: string, password: string): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/api/v1/auth/admin/login', {
    username,
    password,
  });
  tokenStore.set(data.token);
  return data;
}

export function adminLogout(): void {
  tokenStore.clear();
}
