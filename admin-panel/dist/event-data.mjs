export const escapeHtml = (value) =>
  String(value ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  );
const number = (value) => (typeof value === 'number' && Number.isFinite(value) ? value : null);
const count = (value) => (Number.isInteger(value) && value >= 0 ? value : null);
const text = (value, fallback = '') =>
  typeof value === 'string' && value.trim() ? value.trim() : fallback;
export function dateMillis(value) {
  try {
    const n = value?.toMillis?.();
    return Number.isFinite(n) ? n : null;
  } catch {
    return null;
  }
}
export function safePhotoUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null;
  } catch {
    return null;
  }
}
const categories = {
  ROAD: 'Road issue',
  LIGHTING: 'Lighting',
  HAZARDS: 'Hazard',
  VANDALISM: 'Vandalism',
  SAFETY: 'Safety',
  OTHER: 'Other issue',
};
const icons = {
  ROAD: 'road',
  LIGHTING: 'light',
  HAZARDS: 'alert',
  VANDALISM: 'shield',
  SAFETY: 'shield',
  OTHER: 'pin',
};
export function normalizeEvent(id, data = {}) {
  const latitude = number(data.latitude),
    longitude = number(data.longitude);
  const hasLocation =
    latitude !== null &&
    longitude !== null &&
    Math.abs(latitude) <= 90 &&
    Math.abs(longitude) <= 180;
  const severity = number(data.averageSeverity);
  return {
    id,
    status: data.status,
    category: categories[data.category] || text(data.category, 'Uncategorized event'),
    icon: icons[data.category] || 'pin',
    title: text(
      data.title,
      categories[data.category] || text(data.category, 'Uncategorized event'),
    ),
    description: text(data.description),
    severity: severity !== null && severity >= 1 && severity <= 10 ? severity : null,
    reports: count(data.reportCount),
    uniqueReports: count(data.uniqueUserCount),
    votes: number(data.voteScore),
    comments: count(data.commentCount),
    latitude: hasLocation ? latitude : null,
    longitude: hasLocation ? longitude : null,
    location: hasLocation
      ? `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`
      : 'Location unavailable',
    address:
      text(data.fullAddress) ||
      text(data.address) ||
      text(data.streetAddress) ||
      text(data.locationName),
    approximate: data.locationAccuracy === 'general',
    radius: Math.max(0, number(data.radiusMeters) ?? 0),
    photos: Array.isArray(data.photoGallery)
      ? data.photoGallery.map(safePhotoUrl).filter(Boolean)
      : [],
    topComment: text(data.topCommentText),
    createdAt: dateMillis(data.createdAt),
    updatedAt: dateMillis(data.updatedAt),
    resolvedAt: dateMillis(data.resolvedAt),
    resolvedBy: text(data.resolvedBy, 'Unavailable'),
  };
}
export function normalizeComment(id, data = {}) {
  return {
    id,
    status: data.status,
    text: text(data.text, 'Comment text unavailable'),
    author: data.isAnonymous === true ? 'Anonymous' : text(data.displayName, 'Community member'),
    pfpUrl: data.isAnonymous === true ? null : safePhotoUrl(data.pfpUrl),
    anonymous: data.isAnonymous === true,
    createdAt: dateMillis(data.createdAt),
    hearts: count(data.heartCount),
    edited: data.edited === true,
  };
}
export const severityLevel = (e) =>
  e.severity === null ? 'unknown' : e.severity >= 8 ? 'high' : e.severity >= 5 ? 'moderate' : 'low';
export const severityText = (e) => (e.severity === null ? 'Unavailable' : e.severity.toFixed(1));
export function formatDate(millis) {
  return millis === null
    ? 'Unavailable'
    : new Date(millis).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}
export function relativeTime(millis) {
  if (millis === null) return 'Time unavailable';
  const minutes = Math.max(0, Math.floor((Date.now() - millis) / 60000));
  return minutes < 1
    ? 'Just now'
    : minutes < 60
      ? `${minutes}m ago`
      : minutes < 1440
        ? `${Math.floor(minutes / 60)}h ago`
        : `${Math.floor(minutes / 1440)}d ago`;
}
export function resolutionFields(event, profile, uid, timestamp) {
  if (!uid || profile?.admin !== true || profile?.deleted === true)
    throw Object.assign(new Error('Administrator access is no longer available.'), {
      code: 'admin/access-denied',
    });
  if (!event)
    throw Object.assign(new Error('This event no longer exists.'), { code: 'event/not-found' });
  if (event.status !== 'ACTIVE')
    throw Object.assign(
      new Error(
        event.status === 'FIXED'
          ? 'This event was already fixed.'
          : 'Only active events can be marked as fixed.',
      ),
      { code: event.status === 'FIXED' ? 'event/already-fixed' : 'event/not-active' },
    );
  return { status: 'FIXED', resolvedBy: uid, resolvedAt: timestamp(), updatedAt: timestamp() };
}
export async function commitResolution(commit, readLatestEvent) {
  try {
    await commit();
  } catch (error) {
    // Rules may reject a racing transaction before its optimistic retry runs.
    // Read the server record to distinguish an already-resolved event from an
    // actual authorization failure; keep the original error if that read fails.
    if (['permission-denied', 'aborted', 'failed-precondition'].includes(error?.code)) {
      let current;
      try {
        current = await readLatestEvent();
      } catch {
        throw error;
      }
      if (current?.status === 'FIXED')
        throw Object.assign(new Error('This event was already fixed.'), {
          code: 'event/already-fixed',
        });
    }
    throw error;
  }
}
export function eventErrorMessage(error) {
  const code = error?.code || '';
  if (code === 'admin/access-denied')
    return 'Your administrator access has changed. Sign in again to continue.';
  if (code === 'permission-denied' || code === 'firestore/permission-denied')
    return 'Access was denied. Check your administrator profile and Firestore rules, then try again.';
  if (code.startsWith('event/')) return error.message;
  if (code === 'unavailable' || code === 'firestore/unavailable' || error instanceof TypeError)
    return 'Could not reach Firestore. Check your connection and try again.';
  return 'Could not complete the request. Please try again.';
}
