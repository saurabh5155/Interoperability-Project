import { apiClient } from './apiClient';

export interface SubscriptionPlan {
  id: string;
  planCode: string;
  planName: string;
  maxTargets: number;
  allowedResources: string[];
  rateLimitRpm: number;
  active: boolean;
}

export interface RoutingRule {
  id: string;
  sourceEhrCode: string;
  targetEhrCode: string;
  resourceType: string;
  targetOperation: string;
  transformMode: 'SYNC' | 'ASYNC';
  active: boolean;
  priority: number;
  createdAt: string;
}

export interface CreateRoutingRuleRequest {
  sourceEhrCode: string;
  sourceEhrId?: string;
  targetEhrCode: string;
  targetEhrId?: string;
  resourceType: string;
  targetOperation: string;
  transformMode: 'SYNC' | 'ASYNC';
  priority: number;
}

export const subscriptionApi = {
  listPlans: () => apiClient.get<SubscriptionPlan[]>('/api/v1/admin/plans'),
  createPlan: (data: Partial<SubscriptionPlan>) => apiClient.post<SubscriptionPlan>('/api/v1/admin/plans', data),
  assignSubscription: (data: { sourceEhrCode: string; planCode: string; expiresAt?: string }) =>
    apiClient.post('/api/v1/admin/subscriptions', data),
  listRoutingRules: (sourceEhrCode: string) =>
    apiClient.get<RoutingRule[]>(`/api/v1/admin/routing-rules?sourceEhrCode=${sourceEhrCode}`),
  createRoutingRule: (data: CreateRoutingRuleRequest) =>
    apiClient.post<RoutingRule>('/api/v1/admin/routing-rules', data),
  toggleRoutingRule: (id: string, active: boolean) =>
    apiClient.put(`/api/v1/admin/routing-rules/${id}/toggle?active=${active}`),
};
