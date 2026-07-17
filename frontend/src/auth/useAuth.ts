import { useContext } from "react";
import { AuthContext, type AuthContextValue } from "@/auth/AuthContext";

/** Access to login/logout and the authentication flag. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
