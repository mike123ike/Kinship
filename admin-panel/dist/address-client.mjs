// This owner-only panel uses one administrator browser. Web Locks serialize
// its tabs; shared/multi-device use requires a proxy with an app-wide limiter.
const cacheKey = 'kinship-address-cache-v1',
  rateKey = 'kinship-address-request-v1';
const ttl = 30 * 86400000;
const memory = new Map();
let lastRequest = 0;
function readCache(key) {
  try {
    const saved = JSON.parse(localStorage.getItem(cacheKey) || '{}');
    Object.entries(saved).forEach(([k, v]) => memory.set(k, v));
  } catch {}
  const value = memory.get(key);
  return value && Date.now() - value.at < ttl ? value.address : null;
}
function saveCache(key, address) {
  memory.set(key, { address, at: Date.now() });
  const entries = [...memory].filter(([, v]) => Date.now() - v.at < ttl).slice(-250);
  try {
    localStorage.setItem(cacheKey, JSON.stringify(Object.fromEntries(entries)));
  } catch {}
}
export async function lookupAddress(latitude, longitude, { signal } = {}) {
  if (
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude) ||
    Math.abs(latitude) > 90 ||
    Math.abs(longitude) > 180
  )
    return null;
  const key = `${latitude},${longitude}`;
  const cached = readCache(key);
  if (cached) return cached;
  if (!navigator.locks)
    throw new Error('Address lookup requires a browser with Web Locks support.');
  return navigator.locks.request('kinship-address-lookup', { signal }, async () => {
    signal?.throwIfAborted();
    const existing = readCache(key);
    if (existing) return existing;
    const requestSignal = AbortSignal.any([
      signal || new AbortController().signal,
      AbortSignal.timeout(15000),
    ]);
    const configResponse = await fetch('./geocoding-config.json', {
      cache: 'no-store',
      signal: requestSignal,
    });
    if (!configResponse.ok) throw new Error('Address lookup configuration unavailable.');
    const config = await configResponse.json();
    if (!config.enabled) return null;
    const url = new URL(config.endpoint);
    if (url.protocol !== 'https:' || url.username || url.password)
      throw new Error('Invalid address lookup endpoint.');
    lastRequest = Math.max(lastRequest, Number(localStorage.getItem(rateKey)) || 0);
    const wait = Math.max(0, 1200 - (Date.now() - lastRequest));
    if (wait) await new Promise((resolve) => setTimeout(resolve, wait));
    signal?.throwIfAborted();
    lastRequest = Date.now();
    // Fail closed if shared storage is blocked: tabs must share the rate limit.
    localStorage.setItem(rateKey, String(lastRequest));
    url.search = new URLSearchParams({
      format: 'jsonv2',
      lat: String(latitude),
      lon: String(longitude),
      zoom: '18',
      addressdetails: '1',
      layer: 'address',
    });
    // Browser Referer identifies this application; no Firebase IDs or tokens.
    const response = await fetch(url, {
      signal: requestSignal,
      credentials: 'omit',
      referrerPolicy: 'strict-origin-when-cross-origin',
    });
    if (!response.ok) throw new Error('Address lookup failed.');
    const data = await response.json();
    const address = typeof data.display_name === 'string' ? data.display_name.trim() : '';
    if (address) saveCache(key, address);
    return address || null;
  });
}
