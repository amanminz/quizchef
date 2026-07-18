import { apiClient } from "@/api/axios";
import type {
  CurrentUserResponse,
  HostAccessResponse,
  LoginRequest,
  LoginResponse,
  RegisterIdentityRequest,
  RegisterIdentityResponse
} from "@/types/api";

/** Identity endpoints (RFC-002). */
export const identityApi = {
  async login(request: LoginRequest): Promise<LoginResponse> {
    const { data } = await apiClient.post<LoginResponse>("/api/v1/auth/login", request);
    return data;
  },

  async register(request: RegisterIdentityRequest): Promise<RegisterIdentityResponse> {
    const { data } = await apiClient.post<RegisterIdentityResponse>(
      "/api/v1/auth/register",
      request
    );
    return data;
  },

  async currentUser(): Promise<CurrentUserResponse> {
    const { data } = await apiClient.get<CurrentUserResponse>("/api/v1/users/me");
    return data;
  },

  /**
   * Self-service host onboarding: grants QUIZ_MASTER durably and
   * idempotently (RFC-002's product rule — automatic promotion). Takes
   * effect on the very next request with the same token, because the
   * backend authorizes from persisted roles, not the token claim.
   */
  async requestHostAccess(): Promise<HostAccessResponse> {
    const { data } = await apiClient.post<HostAccessResponse>("/api/v1/users/me/host-access");
    return data;
  }
};
