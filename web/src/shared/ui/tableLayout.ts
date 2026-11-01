// How every table of the app sits on the screen (web.md section 8).
const PHONE_WIDTH = 768;
const ALWAYS_CARDS = 100_000;

// The kit compares this with the width of the table's own container, and a table in a dialog or a card is narrow on any
// screen. Cards are for a phone, so the question is asked of the window: there every table is cards, elsewhere none is.
export const cardBreakpoint = () => (window.innerWidth < PHONE_WIDTH ? ALWAYS_CARDS : 0);

// The header, the page's title and the pager take this much of the window; the rows take the rest.
const AROUND_THE_TABLE = 340;
const SHORTEST = 400;

export const tableHeight = () => Math.max(SHORTEST, window.innerHeight - AROUND_THE_TABLE);
