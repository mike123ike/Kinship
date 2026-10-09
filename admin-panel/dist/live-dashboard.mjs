import { icon } from './icons.mjs';
import {
  escapeHtml as h,
  severityLevel as level,
  severityText,
  formatDate,
  relativeTime,
  eventErrorMessage,
} from './event-data.mjs';
import { createEventMap } from './map-client.mjs';
import { createEventDetails } from './event-details.mjs';
import { lookupAddress } from './address-client.mjs';

export function mountDashboard(
  root,
  { client, onSignOut, onAccessLost, preview = false, addressLookup } = {},
) {
  let records = null,
    selected,
    category = 'all',
    view = 'ACTIVE',
    map,
    disposed = false,
    unsubscribe = () => {};
  let connected = false,
    pending = false,
    detailId,
    confirming = false,
    writing = false,
    failed = false;
  let noticeIsError = false;
  const addressRequests = new Map(),
    addressController = new AbortController();
  let selectionAddressKey,
    selectionAddress = '',
    selectionAddressLoading = false;
  function sharedAddressLookup(latitude, longitude) {
    const key = `${latitude},${longitude}`;
    if (!addressRequests.has(key)) {
      const request = Promise.resolve().then(() =>
        (addressLookup || lookupAddress)(latitude, longitude, { signal: addressController.signal }),
      );
      addressRequests.set(key, request);
      request.then(
        (result) => {
          if (!result) addressRequests.delete(key);
        },
        () => addressRequests.delete(key),
      );
    }
    return addressRequests.get(key);
  }
  root.innerHTML = /* HTML */ `
    <div class="dashboard-shell">
      <header class="dashboard-header">
        <a class="dashboard-brand" href="./">
          ${icon('shield')}
          <span>
            Kinship
            <strong>Admin</strong>
          </span>
        </a>
        <div class="header-right">
          <button id="dashboard-signout" class="header-signout">
            ${icon('exit')}
            <span>${preview ? 'Back to sign in' : 'Sign out'}</span>
          </button>
        </div>
      </header>
      <div class="dashboard-content">
        <div class="overview-heading">
          <div class="overview-title">
            <h1 id="dashboard-title" tabindex="-1">Event overview</h1>
            <button
              id="toggle-summary"
              class="summary-toggle"
              type="button"
              aria-controls="event-summary"
              aria-expanded="true"
              aria-label="Hide summary cards"
            >
              ${icon('chevron')}
            </button>
          </div>
          <div class="dashboard-actions">
            <span class="connection-state" role="status">Connecting…</span>
            <button id="refresh-events" class="secondary-button">Refresh</button>
          </div>
        </div>
        <div class="dashboard-notice" role="status" hidden>
          <span class="notice-message"></span>
          <button
            id="dismiss-notice"
            class="icon-button"
            type="button"
            aria-label="Dismiss message"
          >
            ${icon('close')}
          </button>
        </div>
        <section id="event-summary" class="summary-grid" aria-label="Event summary">
          <div class="summary-card">
            <span class="summary-icon">${icon('pin')}</span>
            <div>
              <p>Active events</p>
              <strong id="active-count">—</strong>
              <span>Awaiting resolution</span>
            </div>
          </div>
          <div class="summary-card">
            <span class="summary-icon severity-icon">${icon('alert')}</span>
            <div>
              <p>High severity</p>
              <strong id="high-count">—</strong>
              <span>Active · severity 8.0 and above</span>
            </div>
          </div>
          <div class="summary-card">
            <span class="summary-icon">${icon('check')}</span>
            <div>
              <p>Fixed events</p>
              <strong id="fixed-count">—</strong>
              <span>Resolved in total</span>
            </div>
          </div>
        </section>
        <div class="event-view-tabs" aria-label="Event status">
          <button data-status="ACTIVE" aria-pressed="true">Active events</button>
          <button data-status="FIXED" aria-pressed="false">Fixed history</button>
        </div>
        <div class="workspace">
          <section class="map-panel" aria-label="Event map">
            <header class="panel-heading">
              <div>
                <h2>Community map</h2>
                <p>Select a marker to view an event</p>
              </div>
              <span class="active-key">
                <i></i>
                <span id="map-status-label">Active events</span>
              </span>
            </header>
            <div class="map-canvas">
              <div id="event-map" aria-label="Map of event locations"></div>
              <div class="map-tools">
                <button id="zoom-in" aria-label="Zoom in">+</button>
                <button id="zoom-out" aria-label="Zoom out">−</button>
              </div>
              <button id="reset-map" class="reset-map">${icon('pin')}Fit events</button>
              <div class="map-legend">
                <span>
                  <i class="high"></i>
                  High
                </span>
                <span>
                  <i class="moderate"></i>
                  Moderate
                </span>
                <span>
                  <i class="low"></i>
                  Low severity
                </span>
              </div>
              <p class="map-unavailable" role="status" hidden></p>
            </div>
            <div class="selected-event" aria-live="polite"><p>Loading events…</p></div>
          </section>
          <section class="queue-panel" aria-label="Events queue">
            <header class="queue-heading">
              <h2>
                <span id="queue-title">Active events</span>
                <span class="queue-total">—</span>
              </h2>
            </header>
            <div class="queue-controls">
              <label class="search-field">
                ${icon('search')}
                <input
                  id="event-search"
                  type="search"
                  placeholder="Search category or event ID"
                  aria-label="Search category or event ID"
                />
              </label>
              <div class="queue-filters">
                <label class="category-control">
                  <span class="sr-only">Filter events by category</span>
                  <select id="event-category">
                    <option value="all">All events</option>
                    <option value="Road issue">Road issues</option>
                    <option value="Lighting">Lighting</option>
                    <option value="Hazard">Hazards</option>
                    <option value="Vandalism">Vandalism</option>
                    <option value="Safety">Safety</option>
                    <option value="Other issue">Other issues</option>
                  </select>
                </label>
                <label class="sort-control">
                  <span class="sr-only">Sort events</span>
                  <select id="event-sort">
                    <option value="severity">Highest severity</option>
                    <option value="newest">Newest first</option>
                    <option value="reports">Most reports</option>
                    <option value="resolved">Recently fixed</option>
                  </select>
                </label>
              </div>
            </div>
            <div class="event-list">
              <p class="queue-loading" role="status">Loading events from Firestore…</p>
            </div>
            <div class="queue-footer" role="status">Waiting for event data</div>
          </section>
        </div>
        <section
          id="inline-event-details"
          class="inline-event-details"
          aria-labelledby="inline-detail-title"
          hidden
        ></section>
        <footer class="dashboard-footer"><span>© ${new Date().getFullYear()} Kinship</span></footer>
      </div>
    </div>
    <dialog class="event-dialog" aria-labelledby="confirmation-title">
      <div class="dialog-heading">
        <span class="eyebrow">CONFIRM RESOLUTION</span>
        <button class="icon-button" id="close-confirmation" aria-label="Close confirmation">
          ${icon('close')}
        </button>
      </div>
      <div id="confirmation-content"></div>
    </dialog>
  `;
  const $ = (selector) => root.querySelector(selector);
  const buttons = (selector) => root.querySelectorAll(selector);
  const metric = (value) => (value === null ? '—' : h(value));
  const details = createEventDetails($('#inline-event-details'), {
    addressLookup: sharedAddressLookup,
    preview,
    watchComments: (...args) => client.watchComments(...args),
    onClose: () => {
      detailId = undefined;
      details.close();
    },
    onFix: confirmFix,
    onShowMap: (id) => {
      map?.focus(id);
      $('.map-panel').scrollIntoView({ block: 'start', behavior: 'smooth' });
    },
  });
  function notice(message = '', error = false) {
    noticeIsError = error;
    const box = $('.dashboard-notice');
    $('.notice-message').textContent = message;
    box.hidden = !message;
    box.classList.toggle('notice-error', error);
    box.setAttribute('role', error ? 'alert' : 'status');
  }
  function renderSelectedAddress(event) {
    const key = event
      ? JSON.stringify([event.id, event.latitude, event.longitude, event.address])
      : undefined;
    if (key !== selectionAddressKey) {
      selectionAddressKey = key;
      selectionAddress = event?.address || '';
      selectionAddressLoading = !!event && !selectionAddress && event.latitude !== null && !preview;
      if (selectionAddressLoading)
        sharedAddressLookup(event.latitude, event.longitude).then(
          (address) => {
            if (disposed || selectionAddressKey !== key) return;
            selectionAddress = address || '';
            selectionAddressLoading = false;
            renderSelectedAddress(event);
          },
          () => {
            if (disposed || selectionAddressKey !== key) return;
            selectionAddressLoading = false;
            renderSelectedAddress(event);
          },
        );
    }
    const label = $('.selected-event-address');
    if (label)
      label.textContent =
        selectionAddress ||
        (selectionAddressLoading ? 'Looking up address...' : 'Address unavailable');
    const attribution = $('.selected-address-attribution');
    if (attribution) attribution.hidden = !selectionAddress || !!event?.address;
  }
  function visibleEvents() {
    const term = $('#event-search').value.trim().toLowerCase();
    return (records || [])
      .filter(
        (e) =>
          e.status === view &&
          (category === 'all' || e.category === category) &&
          `${e.category} ${e.id}`.toLowerCase().includes(term),
      )
      .sort((a, b) => {
        const sort = $('#event-sort').value;
        return sort === 'newest'
          ? (b.createdAt ?? 0) - (a.createdAt ?? 0)
          : sort === 'resolved'
            ? (b.resolvedAt ?? 0) - (a.resolvedAt ?? 0)
            : sort === 'reports'
              ? (b.reports ?? -1) - (a.reports ?? -1)
              : (b.severity ?? -1) - (a.severity ?? -1);
      });
  }
  function render() {
    if (disposed) return;
    $('.connection-state').textContent = preview
      ? 'Design preview · sample data'
      : failed
        ? 'Connection unavailable'
        : connected
          ? 'Connected'
          : records
            ? 'Offline · last fetched'
            : 'Connecting…';
    $('.connection-state').classList.toggle('offline', !connected);
    if (records === null) {
      $('.event-list').innerHTML = /* HTML */ `
        <p class="queue-loading" role="status">
          ${failed ? 'Events could not be loaded. Use Refresh to try again.' : 'Loading events from Firestore…'}
        </p>
      `;
      $('.selected-event').innerHTML = '<p>Event data is not available yet.</p>';
      return;
    }
    $('#active-count').textContent = records.filter((e) => e.status === 'ACTIVE').length;
    $('#high-count').textContent = records.filter(
      (e) => e.status === 'ACTIVE' && e.severity >= 8,
    ).length;
    $('#fixed-count').textContent = records.filter((e) => e.status === 'FIXED').length;
    const visible = visibleEvents();
    if (!visible.some((e) => e.id === selected)) selected = visible[0]?.id;
    $('.queue-total').textContent = (records || []).filter((e) => e.status === view).length;
    $('#queue-title').textContent = view === 'ACTIVE' ? 'Active events' : 'Fixed history';
    $('#map-status-label').textContent = view === 'ACTIVE' ? 'Active events' : 'Fixed events';
    $('.event-list').innerHTML = visible.length
      ? visible
          .map(
            (e) => /* HTML */ `
              <article class="event-card ${e.id === selected ? 'selected' : ''}">
                <button
                  class="event-select"
                  data-event="${h(e.id)}"
                  aria-pressed="${e.id === selected}"
                  aria-label="Select ${h(e.title)}"
                >
                  <div class="event-card-top">
                    <span class="category-icon">${icon(e.icon)}</span>
                    <span class="event-category">${h(e.title)}</span>
                    <span class="severity-score ${level(e)}">
                      ${severityText(e)}
                      <span class="sr-only">
                        ${e.severity === null ? '' : ' out of 10, ' + level(e) + ' severity'}
                      </span>
                    </span>
                  </div>
                  <p class="event-location">
                    ${e.approximate ? 'Approximate location &middot; ' : ''}${h(e.location)}
                  </p>
                </button>
                <div class="event-meta">
                  <span>
                    ${metric(e.reports)} reports
                    <i aria-hidden="true">&middot;</i>
                    ${view === 'FIXED' ? 'Fixed ' + h(relativeTime(e.resolvedAt)) : h(relativeTime(e.createdAt))}
                  </span>
                  <button class="event-view" data-detail="${h(e.id)}">
                    View details ${icon('arrow')}
                  </button>
                </div>
                ${
                  view === 'FIXED'
                    ? /* HTML */ `
                        <p class="fixed-card-audit">Resolved ${h(formatDate(e.resolvedAt))}</p>
                      `
                    : ''
                }
              </article>
            `,
          )
          .join('')
      : /* HTML */ `
          <div class="empty-queue">
            <strong>
              ${(records || []).some((e) => e.status === view) ? 'No matching events' : view === 'ACTIVE' ? 'No active events' : 'No fixed events yet'}
            </strong>
            <p>
              ${(records || []).some((e) => e.status === view) ? 'Try another search or choose All events.' : view === 'ACTIVE' ? 'New active community events will appear here.' : 'Resolved events will be preserved here.'}
            </p>
            ${$('#event-search').value || category !== 'all' ? '<button id="clear-search" class="secondary-button">Clear filters</button>' : ''}
          </div>
        `;
    $('.queue-footer').textContent =
      `Showing ${visible.length} of ${records.filter((e) => e.status === view).length} ${view === 'ACTIVE' ? 'active' : 'fixed'} events${visible.some((e) => e.latitude === null) ? ' · Some locations unavailable' : ''}`;
    const e = records.find((e) => e.id === selected);
    $('.selected-event').innerHTML = e
      ? /* HTML */ `
          <span class="selection-icon">${icon(e.icon)}</span>
          <div>
            <span class="eyebrow">SELECTED EVENT</span>
            <h3>${h(e.category)}</h3>
            <p class="selected-event-address"></p>
            <p class="selected-address-attribution address-attribution" hidden>
              Address data &copy;
              <a
                href="https://www.openstreetmap.org/copyright"
                target="_blank"
                rel="noopener noreferrer"
              >
                OpenStreetMap contributors
              </a>
            </p>
          </div>
          <button class="secondary-button" data-detail="${h(e.id)}">
            View details ${icon('arrow')}
          </button>
        `
      : '<p>No events to display with the current filters.</p>';
    renderSelectedAddress(e);
    map?.update(visible, selected);
    if (detailId && !writing) renderDetails();
  }
  function setView(status) {
    if (writing || confirming) return;
    detailId = undefined;
    details.close();
    view = status;
    category = 'all';
    selected = undefined;
    $('#event-search').value = '';
    buttons('[data-status]').forEach((b) =>
      b.setAttribute('aria-pressed', String(b.dataset.status === view)),
    );
    $('#event-category').value = 'all';
    $('#event-sort').value = view === 'FIXED' ? 'resolved' : 'severity';
    render();
    map?.reset();
  }
  function selectEvent(id, focus = true) {
    if (writing || confirming) return;
    if (detailId && detailId !== id) {
      detailId = undefined;
      details.close();
    }
    selected = id;
    render();
    if (focus) map?.focus(id);
  }
  function renderDetails() {
    const e = records?.find((e) => e.id === detailId);
    if (!e) {
      detailId = undefined;
      details.close();
      return;
    }
    details.update(e, { connected, pending });
  }
  function openDetails(id, scroll = true) {
    if (writing) return;
    selectEvent(id);
    detailId = id;
    renderDetails();
    if (scroll) {
      $('#inline-event-details').scrollIntoView({
        block: 'start',
        behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      });
      $('#inline-detail-title')?.focus({ preventScroll: true });
    }
  }
  function confirmFix() {
    const e = records?.find((e) => e.id === detailId);
    if (!e || e.status !== 'ACTIVE' || !connected || pending) {
      renderDetails();
      return;
    }
    confirming = true;
    $('#confirmation-content').innerHTML = /* HTML */ `
      <h2 id="confirmation-title">Mark this event as fixed?</h2>
      <p class="detail-description">
        Confirm that the reported
        <strong>${h(e.category.toLowerCase())}</strong>
        has been resolved.
      </p>
      <div class="confirmation-context">
        <strong>${h(e.category)}</strong>
        <p>${h(e.location)}</p>
        <span>${h(e.id)}</span>
      </div>
      <p class="detail-note">
        The event will leave the active queue and remain in fixed history. Your administrator ID and
        the resolution time will be recorded. This action cannot be undone here.
      </p>
      <div id="resolution-error" class="error-message" role="alert" hidden></div>
      <div class="dialog-actions">
        <button id="cancel-fix" class="secondary-button">Keep active</button>
        <button id="confirm-fix" class="primary-button">Confirm fixed</button>
      </div>
    `;
    $('.event-dialog').showModal();
    $('#cancel-fix').focus();
  }
  async function resolve() {
    if (writing || !confirming) return;
    if (!connected || pending) {
      const b = $('#resolution-error');
      b.textContent = 'Reconnect before marking this event as fixed.';
      b.hidden = false;
      return;
    }
    writing = true;
    const eventId = detailId;
    buttons('#confirm-fix, #cancel-fix, #close-confirmation, #dashboard-signout').forEach(
      (b) => (b.disabled = true),
    );
    $('#confirm-fix').textContent = 'Saving…';
    $('#resolution-error').hidden = true;
    try {
      await client.markEventFixed(eventId);
      if (disposed) return;
      // The transaction has been acknowledged; never show success for queued writes.
      confirming = false;
      $('.event-dialog').close();
      notice('Event marked as fixed. It is preserved in fixed history.');
    } catch (error) {
      if (disposed) return;
      const box = $('#resolution-error');
      box.textContent = eventErrorMessage(error);
      box.hidden = false;
      if (
        error.code === 'event/already-fixed' ||
        error.code === 'event/not-active' ||
        error.code === 'event/not-found'
      ) {
        confirming = false;
        $('.event-dialog').close();
        renderDetails();
        startListening();
        notice(eventErrorMessage(error));
      } else if (error.code === 'admin/access-denied') onAccessLost?.();
    } finally {
      writing = false;
      if (!disposed) {
        buttons('#confirm-fix, #cancel-fix, #close-confirmation, #dashboard-signout').forEach(
          (b) => (b.disabled = false),
        );
        if ($('#confirm-fix')) $('#confirm-fix').textContent = 'Confirm fixed';
        render();
      }
    }
  }
  function startListening() {
    unsubscribe();
    failed = false;
    connected = false;
    notice();
    render();
    unsubscribe = client.watchEvents(
      (data, metadata) => {
        if (disposed) return;
        connected = !metadata.fromCache;
        pending = metadata.pending;
        if (!metadata.fromCache && !metadata.pending) records = data;
        if (connected && noticeIsError) notice();
        failed = false;
        render();
      },
      (error) => {
        if (disposed) return;
        failed = true;
        connected = false;
        notice(eventErrorMessage(error), true);
        render();
      },
      () => {
        if (!disposed) onAccessLost?.();
      },
    );
  }
  function handleClick(event) {
    if (event.target.closest('#dismiss-notice')) {
      notice();
      $('#dashboard-title').focus({ preventScroll: true });
    }
    const summaryToggle = event.target.closest('#toggle-summary');
    if (summaryToggle) {
      const summary = $('#event-summary');
      summary.hidden = !summary.hidden;
      summaryToggle.setAttribute('aria-expanded', String(!summary.hidden));
      summaryToggle.setAttribute(
        'aria-label',
        summary.hidden ? 'Show summary cards' : 'Hide summary cards',
      );
    }
    const card = event.target.closest('[data-event]'),
      status = event.target.closest('[data-status]'),
      detail = event.target.closest('[data-detail]');
    if (card) selectEvent(card.dataset.event);
    if (detail) openDetails(detail.dataset.detail);
    if (status) setView(status.dataset.status);
    if (event.target.closest('#clear-search')) {
      category = 'all';
      $('#event-search').value = '';
      $('#event-category').value = 'all';
      render();
      map?.reset();
    }
    if (event.target.closest('#cancel-fix')) {
      confirming = false;
      $('.event-dialog').close();
      $('#start-fix')?.focus();
    }
    if (event.target.closest('#confirm-fix')) resolve();
    if (!writing && event.target.closest('#close-confirmation')) {
      $('.event-dialog').close();
      confirming = false;
    }
  }
  root.addEventListener('click', handleClick);
  $('.event-dialog').addEventListener('cancel', (event) => {
    if (writing) event.preventDefault();
  });
  $('.event-dialog').addEventListener('close', () => {
    if (!writing) confirming = false;
  });
  $('#event-category').addEventListener('change', () => {
    category = $('#event-category').value;
    render();
    map?.reset();
  });
  $('#event-search').addEventListener('input', () => {
    render();
    map?.reset();
  });
  $('#event-sort').addEventListener('change', render);
  $('#refresh-events').onclick = () => {
    if (!writing) startListening();
  };
  $('#reset-map').onclick = () => map?.reset();
  $('#zoom-in').onclick = () => map?.zoomIn();
  $('#zoom-out').onclick = () => map?.zoomOut();
  $('#dashboard-signout').onclick = async () => {
    if (writing) return;
    const b = $('#dashboard-signout');
    b.disabled = true;
    try {
      await onSignOut();
    } catch {
      if (!disposed) {
        b.disabled = false;
        notice('Could not sign out. Please try again.', true);
      }
    }
  };
  createEventMap($('#event-map'), {
    icon,
    onSelect: (id) => {
      selectEvent(id, false);
      const card = [...buttons('[data-event]')].find((b) => b.dataset.event === id);
      if (card) {
        const list = $('.event-list');
        list.scrollTop = card.offsetTop - list.offsetTop;
      }
    },
    onTileError: () => {
      if (!disposed) {
        $('.map-unavailable').textContent =
          'Map tiles unavailable. Event coordinates are still available in the queue.';
        $('.map-unavailable').hidden = false;
      }
    },
  })
    .then((instance) => {
      if (disposed) {
        instance.destroy();
        return;
      }
      map = instance;
      map.update(visibleEvents(), selected);
    })
    .catch(() => {
      if (!disposed) {
        $('.map-unavailable').textContent =
          'Could not load the map. You can still review events in the queue.';
        $('.map-unavailable').hidden = false;
      }
    });
  startListening();
  return () => {
    if (disposed) return;
    disposed = true;
    addressController.abort();
    unsubscribe();
    map?.destroy();
    details.destroy();
    root.removeEventListener('click', handleClick);
    $('.event-dialog')?.close();
    root.innerHTML = '';
  };
}
