let library;
async function loadLibrary() {
  if (!library)
    library = (async () => {
      const css = document.createElement('link');
      css.rel = 'stylesheet';
      css.href = './vendor/leaflet/leaflet.css';
      document.head.append(css);
      await import('./vendor/leaflet/leaflet.mjs');
      return window.L;
    })().catch((error) => {
      library = undefined;
      throw error;
    });
  return library;
}
export async function createEventMap(container, { onSelect, icon, onTileError }) {
  const L = await loadLibrary();
  const map = L.map(container, { zoomControl: false, scrollWheelZoom: true }).setView([20, 0], 2);
  const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors',
  }).addTo(map);
  tiles.on('tileerror', () => onTileError?.());
  const group = L.layerGroup().addTo(map);
  let events = [],
    selected,
    firstFit = true;
  function fit() {
    const coordinates = events
      .filter((e) => e.latitude !== null)
      .map((e) => [e.latitude, e.longitude]);
    if (coordinates.length)
      map.fitBounds(L.latLngBounds(coordinates), {
        padding: [45, 45],
        maxZoom: 15,
        animate: false,
      });
  }
  function update(next, selectedId) {
    events = next;
    selected = selectedId;
    group.clearLayers();
    for (const e of events) {
      if (e.latitude === null) continue;
      const severity =
        e.severity === null
          ? 'unknown'
          : e.severity >= 8
            ? 'high'
            : e.severity >= 5
              ? 'moderate'
              : 'low';
      const marker = L.marker([e.latitude, e.longitude], {
        icon: L.divIcon({
          className: `live-pin ${severity} ${e.id === selected ? 'selected' : ''}`,
          html: icon(e.icon),
          iconSize: [36, 36],
          iconAnchor: [18, 18],
        }),
        title: `${e.category}, ${e.location}`,
        keyboard: true,
        riseOnHover: true,
      }).addTo(group);
      marker.on('click', () => onSelect(e.id));
      const element = marker.getElement();
      element?.setAttribute('aria-label', `${e.category}, ${e.location}`);
      element?.setAttribute('aria-pressed', String(e.id === selected));
      if (e.id === selected && e.approximate && e.radius > 0)
        L.circle([e.latitude, e.longitude], {
          radius: e.radius,
          color: '#608452',
          weight: 1,
          fillOpacity: 0.1,
          interactive: false,
        }).addTo(group);
    }
    if (firstFit && events.some((e) => e.latitude !== null)) {
      fit();
      firstFit = false;
    }
  }
  function focus(id) {
    const e = events.find((e) => e.id === id);
    if (e?.latitude !== null && e)
      map.setView([e.latitude, e.longitude], Math.max(map.getZoom(), 14), { animate: false });
  }
  const observer = new ResizeObserver(() => map.invalidateSize());
  observer.observe(container);
  return {
    update,
    focus,
    reset: fit,
    zoomIn: () => map.zoomIn(),
    zoomOut: () => map.zoomOut(),
    destroy: () => {
      observer.disconnect();
      map.remove();
    },
  };
}
