export interface AuthUser {
  email: string;
  displayName: string;
  role: 'RESPONSABLE' | 'TRESORIER' | 'ADMIN';
}

export interface CsrfTokenResponse {
  token: string;
  headerName: string;
  parameterName: string;
}

export interface AcceptInvitationRequest {
  token: string;
  email: string;
  displayName: string;
  password: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface PasswordResetRequest {
  email: string;
}

export interface CompletePasswordResetRequest {
  token: string;
  password: string;
}

export interface AuthMessage {
  message: string;
}
