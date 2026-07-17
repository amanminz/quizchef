import { apiClient } from "@/api/axios";
import type {
  CurrentUserResponse,
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
  }
};
