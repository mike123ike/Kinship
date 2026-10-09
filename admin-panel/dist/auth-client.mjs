import { initializeApp } from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-app.js';
import {
  getAuth,
  setPersistence,
  browserSessionPersistence,
  signInWithEmailAndPassword,
  signOut,
} from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-auth.js';
import {
  getFirestore,
  doc,
  getDocFromServer,
  collection,
  query,
  where,
  orderBy,
  onSnapshot,
  runTransaction,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-firestore.js';
import {
  normalizeEvent,
  normalizeComment,
  resolutionFields,
  commitResolution,
} from './event-data.mjs';
import { firebaseConfig } from './firebase-config.mjs';
import { adminLogin } from './admin-login-config.mjs';
import { verifyAdministrator, signInAdministrator, AccessDeniedError } from './session.mjs';

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const ready = (async () => {
  await setPersistence(auth, browserSessionPersistence);
  await auth.authStateReady();
})();

async function readProfile(uid) {
  // Server-only verification avoids authorizing from stale offline role data.
  const snapshot = await getDocFromServer(doc(db, 'users', uid));
  return snapshot.exists() ? snapshot.data() : null;
}

export async function restoreSession() {
  await ready;
  if (!auth.currentUser) return null;
  return verifyAdministrator(auth.currentUser, readProfile, () => signOut(auth));
}

export async function signIn(username, password) {
  await ready;
  return signInAdministrator(
    username,
    password,
    adminLogin,
    (email, credential) => signInWithEmailAndPassword(auth, email, credential),
    readProfile,
    () => signOut(auth),
  );
}

export async function endSession() {
  await ready;
  await signOut(auth);
}

// Called only after the sign-in flow has verified the protected admin profile.
export function watchEvents(onData, onError, onAccessLost) {
  let stopped = false,
    stopEvents = () => {},
    stopProfile = () => {};
  const stop = () => {
    stopped = true;
    stopEvents();
    stopProfile();
  };
  (async () => {
    await ready;
    const user = auth.currentUser;
    if (!user) throw Object.assign(new Error('Sign in required'), { code: 'admin/access-denied' });
    await verifyAdministrator(user, readProfile, () => signOut(auth));
    if (stopped || auth.currentUser?.uid !== user.uid) return;
    stopProfile = onSnapshot(
      doc(db, 'users', user.uid),
      { includeMetadataChanges: true },
      (snapshot) => {
        if (stopped || snapshot.metadata.fromCache) return;
        const profile = snapshot.data();
        if (!snapshot.exists() || profile.admin !== true || profile.deleted === true) {
          stop();
          onAccessLost?.();
        }
      },
      () => {
        if (!stopped) {
          stop();
          onAccessLost?.();
        }
      },
    );
    stopEvents = onSnapshot(
      query(collection(db, 'events'), where('status', 'in', ['ACTIVE', 'FIXED'])),
      { includeMetadataChanges: true },
      (snapshot) => {
        if (!stopped)
          onData(
            snapshot.docs.map((s) => normalizeEvent(s.id, s.data())),
            { fromCache: snapshot.metadata.fromCache, pending: snapshot.metadata.hasPendingWrites },
          );
      },
      (error) => {
        if (!stopped) onError(error);
      },
    );
  })().catch((error) => {
    if (!stopped) {
      if (
        error instanceof AccessDeniedError ||
        error?.code === 'admin/access-denied' ||
        !auth.currentUser
      )
        onAccessLost?.(error);
      else onError(error);
    }
  });
  return stop;
}

export async function markEventFixed(eventId) {
  await ready;
  const user = auth.currentUser;
  if (!user || typeof eventId !== 'string' || !eventId || eventId.includes('/'))
    throw Object.assign(new Error('Sign in required'), { code: 'admin/access-denied' });
  const eventRef = doc(db, 'events', eventId);
  await commitResolution(
    () =>
      runTransaction(db, async (transaction) => {
        const profile = await transaction.get(doc(db, 'users', user.uid));
        const event = await transaction.get(eventRef);
        if (auth.currentUser?.uid !== user.uid)
          throw Object.assign(new Error('Session changed'), { code: 'admin/access-denied' });
        const fields = resolutionFields(
          event.exists() ? event.data() : null,
          profile.data(),
          user.uid,
          serverTimestamp,
        );
        transaction.update(eventRef, fields);
      }),
    async () => {
      const snapshot = await getDocFromServer(eventRef);
      return snapshot.exists() ? snapshot.data() : null;
    },
  );
}

export function watchComments(eventId, onData, onError) {
  let stopped = false,
    unsubscribe = () => {};
  (async () => {
    await ready;
    if (!auth.currentUser || typeof eventId !== 'string' || !eventId || eventId.includes('/'))
      throw Object.assign(new Error('Sign in required'), { code: 'admin/access-denied' });
    if (stopped) return;
    // A single-field query avoids a composite-index requirement. Exclude hidden
    // and removed comments using their existing lifecycle field before rendering.
    unsubscribe = onSnapshot(
      query(collection(db, 'events', eventId, 'comments'), orderBy('createdAt', 'desc')),
      { includeMetadataChanges: true },
      (snapshot) => {
        if (!stopped)
          onData(
            snapshot.docs
              .map((s) => normalizeComment(s.id, s.data()))
              .filter((c) => c.status === 'active'),
            { fromCache: snapshot.metadata.fromCache },
          );
      },
      (error) => {
        if (!stopped) onError(error);
      },
    );
  })().catch((error) => {
    if (!stopped) onError(error);
  });
  return () => {
    stopped = true;
    unsubscribe();
  };
}
