import { signInErrorMessage } from './session.mjs';
import { mountDashboard } from './live-dashboard.mjs';

const form = document.querySelector('#login-form');
const username = document.querySelector('#username');
const password = document.querySelector('#password');
const toggle = document.querySelector('#password-toggle');
const submit = document.querySelector('#submit-button');
const label = document.querySelector('#submit-label');
const spinner = submit.querySelector('.spinner');
const errorBox = document.querySelector('#error-message');
const status = document.querySelector('#session-status');
let clientPromise;
let busy = false;
let unmountDashboard;

function clearDashboard() {
  const cleanup = unmountDashboard;
  unmountDashboard = undefined;
  cleanup?.();
}

function getClient() {
  if (!clientPromise) {
    clientPromise = import('./auth-client.mjs').catch((error) => {
      clientPromise = undefined;
      throw error;
    });
  }
  return clientPromise;
}

function setBusy(value, text = 'Signing in…') {
  busy = value;
  form.setAttribute('aria-busy', String(value));
  [submit, username, password, toggle].forEach((control) => {
    control.disabled = value;
  });
  spinner.hidden = !value;
  label.textContent = value ? text : 'Sign in';
}

function showError(message) {
  errorBox.textContent = message;
  errorBox.hidden = !message;
}

function showAccount(account, moveFocus = true, client) {
  clearDashboard();
  password.value = '';
  password.type = 'password';
  toggle.setAttribute('aria-label', 'Show password');
  toggle.setAttribute('aria-pressed', 'false');
  showError('');
  const root = document.querySelector('#dashboard-root');
  root.hidden = false;
  document.body.classList.add('dashboard-active');
  const returnToLogin = () => {
    clearDashboard();
    root.hidden = true;
    document.body.classList.remove('dashboard-active');
    username.value = '';
    document.title = 'Sign in · Kinship Admin';
    username.focus();
  };
  unmountDashboard = mountDashboard(root, {
    client,
    onSignOut: async () => {
      await client.endSession();
      returnToLogin();
    },
    onAccessLost: async (error) => {
      returnToLogin();
      showError(
        error
          ? signInErrorMessage(error)
          : 'Your administrator access has changed. Sign in again to continue.',
      );
      try {
        await client.endSession();
      } catch {
        /* The dashboard is already closed. */
      }
    },
  });
  document.title = 'Dashboard · Kinship Admin';
  if (moveFocus) document.querySelector('#dashboard-title').focus();
}

toggle.addEventListener('click', () => {
  const visible = password.type === 'password';
  password.type = visible ? 'text' : 'password';
  toggle.setAttribute('aria-label', visible ? 'Hide password' : 'Show password');
  toggle.setAttribute('aria-pressed', String(visible));
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy || !form.reportValidity()) return;
  const enteredUsername = username.value.trim();
  const enteredPassword = password.value;
  showError('');
  setBusy(true);
  status.textContent = 'Checking your administrator account…';
  try {
    const client = await getClient();
    const account = await client.signIn(enteredUsername, enteredPassword);
    showAccount(account, true, client);
  } catch (error) {
    password.value = '';
    showError(signInErrorMessage(error));
  } finally {
    setBusy(false);
    status.textContent = 'Your session ends when you close this browser tab.';
  }
});

document.querySelector('#year').textContent = String(new Date().getFullYear());

async function restore() {
  setBusy(true, 'Checking session…');
  status.textContent = 'Checking your session…';
  try {
    const client = await getClient();
    const account = await client.restoreSession();
    if (account) showAccount(account, false, client);
  } catch (error) {
    showError(
      error instanceof TypeError
        ? 'Could not connect to sign-in. Check your connection and try again.'
        : signInErrorMessage(error),
    );
  } finally {
    setBusy(false);
    status.textContent = 'Your session ends when you close this browser tab.';
  }
}

// A fresh visit is immediately usable. Load the SDK for a submitted login or
// when Firebase has a session to restore, rather than blocking the first form
// on a third-party CDN request. This storage check grants no authorization.
try {
  if (Object.keys(sessionStorage).some((key) => key.startsWith('firebase:authUser:'))) {
    restore();
  }
} catch {
  // Restricted browser storage must not prevent rendering the login form.
}
