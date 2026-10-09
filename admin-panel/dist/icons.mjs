const paths = {
  chevron: '<path d="m6 9 6 6 6-6"/>',
  shield: '<path d="m12 3-8 3v6c0 5 3 8 8 10 5-2 8-5 8-10V6l-8-3Z"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  arrow: '<path d="M5 12h14m-5-5 5 5-5 5"/>',
  exit: '<path d="M10 4H4v16h6m5-13 5 5-5 5m-6-5h11"/>',
  pin: '<path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2"/>',
  alert: '<path d="m12 3 10 18H2L12 3Z"/><path d="M12 9v5m0 3v.1"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  tree: '<path d="m12 3-6 7h3l-5 7h7v4h2v-4h7l-5-7h3l-6-7Z"/>',
  road: '<path d="m7 3-3 18M17 3l3 18M12 3v4m0 3v4m0 3v4"/>',
  light: '<path d="M9 18h6m-5 3h4M8 14a6 6 0 1 1 8 0l-1 2H9l-1-2Z"/>',
  water: '<path d="M12 3S5 11 5 15a7 7 0 0 0 14 0c0-4-7-12-7-12Z"/>',
  trash: '<path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
};
export const icon = (name) => /* HTML */ `
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    ${paths[name] || paths.pin}
  </svg>
`;
