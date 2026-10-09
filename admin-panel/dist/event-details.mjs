import { lookupAddress } from './address-client.mjs';
import { icon } from './icons.mjs';
import { escapeHtml as h, severityLevel, severityText, formatDate } from './event-data.mjs';

export function createEventDetails(
  container,
  { watchComments, onClose, onFix, onShowMap, preview = false, addressLookup = lookupAddress },
) {
  let event,
    options = {},
    photoIndex = 0,
    comments = null,
    commentError = '',
    commentsOffline = false,
    stopComments = () => {},
    disposed = false,
    signature;
  let address = '',
    addressLoading = false,
    addressFailed = false,
    addressController,
    addressSignature;
  const $ = (selector) => container.querySelector(selector);
  const metric = (value) => (value == null ? '—' : h(value));
  function renderAddress() {
    if (!$('.detail-address')) return;
    $('.detail-address').textContent =
      address || (addressLoading ? 'Looking up address...' : 'Address unavailable');
    $('#retry-address').hidden = !addressFailed;
    $('#address-attribution').hidden = !address || !!event.address;
  }
  async function startAddress(force = false) {
    const key = JSON.stringify([event.id, event.latitude, event.longitude, event.address]);
    if (!force && key === addressSignature) {
      renderAddress();
      return;
    }
    addressSignature = key;
    addressController?.abort();
    addressController = new AbortController();
    const controller = addressController;
    address = event.address || '';
    addressFailed = false;
    addressLoading = !address && !preview && event.latitude !== null;
    renderAddress();
    if (!addressLoading) return;
    try {
      const result = await addressLookup(event.latitude, event.longitude, {
        signal: controller.signal,
      });
      if (controller.signal.aborted || disposed) return;
      address = result || '';
    } catch (error) {
      if (controller.signal.aborted || disposed) return;
      addressFailed = true;
    }
    addressLoading = false;
    renderAddress();
  }
  function commentAvatar(comment) {
    const initial = h(comment.author.slice(0, 1).toUpperCase());
    return /* HTML */ `
      <span class="comment-avatar" aria-hidden="true">
        ${
          comment.pfpUrl
            ? /* HTML */ `
                <img
                  src="${h(comment.pfpUrl)}"
                  alt=""
                  loading="lazy"
                  referrerpolicy="no-referrer"
                />
                <span hidden>${initial}</span>
              `
            : /* HTML */ `
                <span>${initial}</span>
              `
        }
      </span>
    `;
  }
  function photo(url, index, large = false) {
    return /* HTML */ `
      <img
        src="${h(url)}"
        alt="Photo ${index + 1} of ${h(event.title)}"
        ${large ? '' : 'loading="lazy"'}
        referrerpolicy="no-referrer"
      />
      <span class="photo-fallback" hidden>Photo unavailable</span>
    `;
  }
  function wireImages(parent) {
    parent.querySelectorAll('img').forEach((img) => {
      img.addEventListener(
        'error',
        () => {
          img.hidden = true;
          img.nextElementSibling.hidden = false;
        },
        { once: true },
      );
      if (img.complete && !img.naturalWidth) {
        img.hidden = true;
        img.nextElementSibling.hidden = false;
      }
    });
  }
  function renderPhoto() {
    if (!event || !$('.photo-carousel')) return;
    const photos = event.photos;
    photoIndex = photos.length ? Math.min(photoIndex, photos.length - 1) : 0;
    $('.photo-carousel').innerHTML = photos.length
      ? /* HTML */ `
          <button
            class="carousel-image"
            data-gallery="${photoIndex}"
            aria-label="Open photo ${photoIndex + 1} in gallery"
          >
            ${photo(photos[photoIndex], photoIndex, true)}
          </button>
          <div class="carousel-controls">
            <button
              class="icon-button"
              data-photo-step="-1"
              aria-label="Previous photo"
              ${photos.length < 2 ? 'disabled' : ''}
            >
              ${icon('arrow')}
            </button>
            <span role="status" aria-live="polite">${photoIndex + 1} / ${photos.length}</span>
            <button
              class="icon-button"
              data-photo-step="1"
              aria-label="Next photo"
              ${photos.length < 2 ? 'disabled' : ''}
            >
              ${icon('arrow')}
            </button>
          </div>
        `
      : /* HTML */ `
          <div class="no-photos">
            ${icon(event.icon)}
            <span>No photos available for this event.</span>
          </div>
        `;
    wireImages($('.photo-carousel'));
    $('#view-all-photos').disabled = !photos.length;
    $('#photo-count').textContent = photos.length;
  }
  function renderComments() {
    if (!$('#comments-list')) return;
    $('#loaded-comments').textContent = comments?.length ?? '—';
    $('#comments-state').textContent = commentsOffline ? 'Offline · last fetched comments' : '';
    $('#comments-list').innerHTML = commentError
      ? /* HTML */ `
          <div class="comments-empty" role="alert">
            <p>${h(commentError)}</p>
            <button class="secondary-button" id="retry-comments">Retry comments</button>
          </div>
        `
      : comments === null
        ? '<p class="comments-empty" role="status">Loading comments…</p>'
        : comments.length
          ? comments
              .map(
                (c) => /* HTML */ `
                  <article class="community-comment">
                    ${commentAvatar(c)}
                    <div>
                      <header>
                        <strong>${h(c.author)}</strong>
                        <span>${h(formatDate(c.createdAt))}${c.edited ? ' · edited' : ''}</span>
                      </header>
                      <p>${h(c.text)}</p>
                      ${
                        c.hearts !== null
                          ? /* HTML */ `
                              <span class="comment-hearts">
                                ${h(c.hearts)} ${c.hearts === 1 ? 'like' : 'likes'}
                              </span>
                            `
                          : ''
                      }
                    </div>
                  </article>
                `,
              )
              .join('')
          : '<p class="comments-empty">No comments yet.</p>';
    wireImages($('#comments-list'));
  }
  function startComments() {
    stopComments();
    comments = null;
    commentError = '';
    commentsOffline = false;
    renderComments();
    const id = event.id;
    stopComments = watchComments(
      id,
      (data, metadata = {}) => {
        if (disposed || event?.id !== id) return;
        if (metadata.fromCache && comments === null) {
          commentsOffline = true;
          $('#comments-state').textContent = 'Waiting for a connection…';
          return;
        }
        comments = data;
        commentsOffline = !!metadata.fromCache;
        commentError = '';
        renderComments();
      },
      () => {
        if (disposed || event?.id !== id) return;
        commentError = 'Could not load comments. Check your connection and try again.';
        renderComments();
      },
    );
  }
  function render() {
    container.hidden = false;
    container.innerHTML = /* HTML */ `
      <header class="inline-detail-heading">
        <div>
          <p class="eyebrow">EVENT DETAILS</p>
          <div class="detail-title-row">
            <h2 id="inline-detail-title" tabindex="-1">${h(event.title)}</h2>
            <span class="status-badge">${event.status === 'ACTIVE' ? 'Active' : 'Fixed'}</span>
          </div>
          <p class="detail-id">${h(event.id)}</p>
        </div>
        <button id="close-event-details" class="icon-button" aria-label="Close event details">
          ${icon('close')}
        </button>
      </header>
      <div class="inline-detail-grid">
        <div class="detail-information">
          <dl class="event-facts-grid">
            <div>
              <dt>Category</dt>
              <dd>${h(event.category)}</dd>
            </div>
            <div>
              <dt>Average severity</dt>
              <dd class="${severityLevel(event)}">
                ${severityText(event)}${event.severity === null ? '' : ' <small>/ 10</small>'}
              </dd>
            </div>
            <div>
              <dt>Reports</dt>
              <dd>${metric(event.reports)}</dd>
            </div>
            <div>
              <dt>Unique reporters</dt>
              <dd>${metric(event.uniqueReports)}</dd>
            </div>
            <div>
              <dt>Vote score</dt>
              <dd>${metric(event.votes)}</dd>
            </div>
            <div>
              <dt>Recorded comments</dt>
              <dd>${metric(event.comments)}</dd>
            </div>
          </dl>
          ${
            event.description
              ? /* HTML */ `
                  <div class="detail-section">
                    <h3>Reported issue</h3>
                    <p class="detail-description">${h(event.description)}</p>
                  </div>
                `
              : ''
          }
          <div class="detail-section">
            <div class="section-title">
              <h3>Location</h3>
              ${event.latitude !== null ? '<button class="text-button" id="show-detail-map">Show on map</button>' : ''}
            </div>
            <p class="detail-location">${icon('pin')}${h(event.location)}</p>
            <p class="detail-address" aria-live="polite"></p>
            <button id="retry-address" class="text-button" hidden>Retry address lookup</button>
            <p id="address-attribution" class="address-attribution" hidden>
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
          <dl class="detail-record">
            <div>
              <dt>First reported</dt>
              <dd>${h(formatDate(event.createdAt))}</dd>
            </div>
            <div>
              <dt>Last updated</dt>
              <dd>${h(formatDate(event.updatedAt))}</dd>
            </div>
          </dl>
        </div>
        <section class="detail-media" aria-label="Event photos">
          <div class="section-title">
            <h3>
              Photos
              <span id="photo-count"></span>
            </h3>
            <button id="view-all-photos" class="secondary-button">View all</button>
          </div>
          <div
            class="photo-carousel"
            aria-roledescription="carousel"
            aria-label="Event photos"
          ></div>
        </section>
      </div>
      <section class="detail-comments" aria-labelledby="comments-title">
        <div class="section-title">
          <h3 id="comments-title">
            Comments
            <span id="loaded-comments"></span>
          </h3>
          <span id="comments-state" role="status"></span>
        </div>
        <div id="comments-list" aria-live="polite"></div>
      </section>
      <div class="inline-resolution">
        ${
          event.status === 'FIXED'
            ? /* HTML */ `
                <div class="resolution-audit">
                  <h3>Resolution record</h3>
                  <p>
                    Fixed on
                    <strong>${h(formatDate(event.resolvedAt))}</strong>
                  </p>
                  <p>
                    Resolved by administrator UID
                    <strong>${h(event.resolvedBy)}</strong>
                  </p>
                  <span>This record is read-only.</span>
                </div>
              `
            : preview
              ? '<p class="detail-note">Sample event · resolution is available after signing in to the live dashboard.</p>'
              : /* HTML */ `
                  <div>
                    <h3>Resolve this event</h3>
                    <p class="detail-note">
                      Confirm the issue has been resolved before moving it to fixed history.
                    </p>
                    ${!options.connected ? '<p class="detail-note">Reconnect before marking this event as fixed.</p>' : ''}
                  </div>
                  <button
                    class="primary-button"
                    id="start-fix"
                    ${!options.connected || options.pending ? 'disabled' : ''}
                  >
                    Mark as Fixed
                  </button>
                `
        }
      </div>
      <dialog class="photo-gallery-dialog" aria-labelledby="gallery-title">
        <header class="gallery-heading">
          <div>
            <h2 id="gallery-title">Event photos</h2>
            <p>${h(event.title)}</p>
          </div>
          <button class="icon-button" id="close-photo-gallery" aria-label="Close photo gallery">
            ${icon('close')}
          </button>
        </header>
        <div id="gallery-content"></div>
      </dialog>
    `;
    renderPhoto();
    renderComments();
    startAddress();
    $('.photo-gallery-dialog').addEventListener('click', (e) => {
      if (e.target === $('.photo-gallery-dialog')) {
        const r = e.target.getBoundingClientRect();
        if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom)
          e.target.close();
      }
    });
  }
  function openGallery(index = photoIndex) {
    if (!event.photos.length) return;
    photoIndex = index;
    $('#gallery-content').innerHTML = /* HTML */ `
      <div class="gallery-featured">${photo(event.photos[index], index, true)}</div>
      <div class="gallery-nav">
        <button
          class="secondary-button"
          data-gallery-step="-1"
          aria-label="Previous gallery photo"
          ${event.photos.length < 2 ? 'disabled' : ''}
        >
          Previous
        </button>
        <span role="status">${index + 1} of ${event.photos.length}</span>
        <button
          class="secondary-button"
          data-gallery-step="1"
          aria-label="Next gallery photo"
          ${event.photos.length < 2 ? 'disabled' : ''}
        >
          Next
        </button>
      </div>
      <div class="gallery-thumbnails">
        ${event.photos
          .map(
            (url, i) => /* HTML */ `
              <button
                data-gallery="${i}"
                aria-label="View photo ${i + 1}"
                aria-pressed="${i === index}"
              >
                ${photo(url, i)}
              </button>
            `,
          )
          .join('')}
      </div>
    `;
    wireImages($('#gallery-content'));
    if (!$('.photo-gallery-dialog').open) $('.photo-gallery-dialog').showModal();
  }
  function step(amount) {
    photoIndex = (photoIndex + amount + event.photos.length) % event.photos.length;
  }
  function handleClick(e) {
    const photoStep = e.target.closest('[data-photo-step]'),
      galleryStep = e.target.closest('[data-gallery-step]'),
      galleryPhoto = e.target.closest('[data-gallery]');
    if (photoStep) {
      step(Number(photoStep.dataset.photoStep));
      renderPhoto();
    }
    if (galleryStep) {
      step(Number(galleryStep.dataset.galleryStep));
      openGallery(photoIndex);
      renderPhoto();
    }
    if (galleryPhoto) {
      openGallery(Number(galleryPhoto.dataset.gallery));
      renderPhoto();
    }
    if (e.target.closest('#view-all-photos')) openGallery();
    if (e.target.closest('#close-photo-gallery')) $('.photo-gallery-dialog').close();
    if (e.target.closest('#close-event-details')) onClose();
    if (e.target.closest('#start-fix')) onFix();
    if (e.target.closest('#show-detail-map')) onShowMap(event.id);
    if (e.target.closest('#retry-comments')) startComments();
    if (e.target.closest('#retry-address')) startAddress(true);
  }
  container.addEventListener('click', handleClick);
  return {
    update(next, nextOptions = {}) {
      const changedId = event?.id !== next.id;
      const nextSignature = JSON.stringify([next, nextOptions]);
      if (!changedId && signature === nextSignature) return;
      const galleryOpen = $('.photo-gallery-dialog')?.open;
      $('.photo-gallery-dialog')?.close();
      if (changedId) {
        photoIndex = 0;
        comments = null;
        commentError = '';
        stopComments();
      }
      event = next;
      options = nextOptions;
      signature = nextSignature;
      render();
      if (changedId) startComments();
      else if (galleryOpen && event.photos.length) openGallery();
    },
    close() {
      addressController?.abort();
      addressSignature = undefined;
      stopComments();
      event = undefined;
      signature = undefined;
      $('.photo-gallery-dialog')?.close();
      container.innerHTML = '';
      container.hidden = true;
    },
    destroy() {
      disposed = true;
      addressController?.abort();
      stopComments();
      $('.photo-gallery-dialog')?.close();
      container.removeEventListener('click', handleClick);
      container.innerHTML = '';
    },
  };
}
