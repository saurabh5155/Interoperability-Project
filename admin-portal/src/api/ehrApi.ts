import { apiClient } from './apiClient';

export interface EhrRegistration {
  id: string;
  ehrCode: string;
  displayName: string;
  orgName: string;
  contactEmail: string;
  baseUrl: string;
  authType: string;
  status: string;
  fhirVersion: string;
  createdAt: string;
}

export interface EhrEndpoint {
  id: string;
  operationType: string;
  httpMethod: string;
  pathTemplate: string;
  timeoutMs: number;
  retryCount: number;
  active: boolean;
}

export const ehrApi = {
  listAll: () => apiClient.get<EhrRegistration[]>('/api/v1/ehr'),
  getByCode: (ehrCode: string) => apiClient.get<EhrRegistration>(`/api/v1/ehr/${ehrCode}`),
  testConnection: (ehrCode: string) => apiClient.post(`/api/v1/ehr/${ehrCode}/test`),
  suspend: (ehrCode: string) => apiClient.post(`/api/v1/ehr/${ehrCode}/suspend`),
  activate: (ehrCode: string) => apiClient.post(`/api/v1/ehr/${ehrCode}/activate`),
  rotateKey: (ehrCode: string) => apiClient.post(`/api/v1/ehr/${ehrCode}/rotate-key`),
  listEndpoints: (ehrCode: string) => apiClient.get<EhrEndpoint[]>(`/api/v1/ehr/${ehrCode}/endpoints`),
};
