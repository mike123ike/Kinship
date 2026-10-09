export class AccessDeniedError extends Error {
  constructor() {
    super('This account does not have administrator access. Contact the project owner.');
    this.name = 'AccessDeniedError';
  }
}

// A username is only an alias for Firebase credentials. The protected
// Firestore role remains the source of administrator authorization.
export async function signInAdministrator(
  username,
  password,
  account,
  authenticate,
  readProfile,
  clearSession,
) {
  if (
    typeof username !== 'string' ||
    username.trim().toLowerCase() !== account.username.toLowerCase()
  ) {
    throw Object.assign(new Error('Invalid credentials'), { code: 'auth/invalid-credential' });
  }
  const { user } = await authenticate(account.email, password);
  return verifyAdministrator(user, readProfile, clearSession);
}

// This matches the protected role fields in the currently deployed rules.
// Migrate this check alongside the rules when adopting admins/{uid}.
export async function verifyAdministrator(user, readProfile, clearSession) {
  try {
    const profile = await readProfile(user.uid);
    if (!profile || profile.admin !== true || profile.deleted === true) {
      throw new AccessDeniedError();
    }
    return { uid: user.uid, email: user.email || '' };
  } catch (error) {
    // The caller never renders authorized UI on a failed check, including
    // when clearing an invalid session fails because the network is down.
    await clearSession().catch(() => {});
    throw error;
  }
}

export function signInErrorMessage(error) {
  if (error instanceof AccessDeniedError) return error.message;
  switch (error?.code) {
    case 'auth/invalid-credential':
    case 'auth/invalid-login-credentials':
    case 'auth/user-not-found':
    case 'auth/wrong-password':
    case 'auth/invalid-email':
    case 'auth/user-disabled':
      return 'Unable to sign in with those credentials. Check your username and password.';
    case 'auth/too-many-requests':
      return 'Too many sign-in attempts. Wait a moment and try again.';
    case 'auth/network-request-failed':
    case 'unavailable':
    case 'deadline-exceeded':
      return 'Could not connect. Check your internet connection and try again.';
    case 'permission-denied':
      return 'Administrator access could not be verified. Contact the project owner.';
    case 'auth/operation-not-allowed':
      return 'Email and password sign-in is not available yet. Contact the project owner.';
    case 'auth/api-key-not-valid.-please-pass-a-valid-api-key.':
    case 'auth/invalid-api-key':
    case 'auth/app-not-authorized':
      return 'Sign-in is not configured for this website yet. Contact the project owner.';
    default:
      return 'Unable to complete sign-in. Please try again.';
  }
}
