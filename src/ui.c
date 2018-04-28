
#include <stdio.h>
#include <stdarg.h>

#include "oled.h"
#include "regs.h"

#include "ui.h"


struct sprite {
	uint16_t width;
	uint16_t height;
	uint8_t data[];
};

struct font {
	unsigned int width;
	unsigned int height;
	unsigned int stride;
	uint8_t entries[];
};

#include "sprites_data.h"


static inline void blit_sprite(const struct sprite *sprite,
							   unsigned int x, unsigned int y,
							   enum oled_blit_mode mode)
{
	oled_blit(sprite->data, sprite->width, sprite->height, x, y, mode);
}


static inline void blit_font(const struct font *font, unsigned int idx,
							 unsigned int x, unsigned int y, enum oled_blit_mode mode)
{
	oled_blit(&font->entries[idx*font->stride], font->width, font->height, x, y, mode);
}


static inline void blit_string(const struct font *font, const char *str,
							   unsigned int x, unsigned int y, enum oled_blit_mode mode,
							   unsigned int clearlen)
{
	char c;
	while (true) {
		if (*str != '\0') {
			c = *str++;
		} else if (clearlen > 0) {
			c = ' ';
		} else {
			break;
		}

		if (c >= '!' && c <= '~') {
			blit_font(font, c - '!', x, y, mode);
		} else {  // Space (or invalid character, meh)
			oled_fill(x, y, font->width, font->height, OLED_BLIT_NORMAL);
		}
		x += font->width;
		if (x > OLED_WIDTH) {
			y += font->height;
		}
		if (clearlen > 0) {
			clearlen--;
		}
	}
}


enum key_state {
	KEY_STATE_NONE,
	KEY_STATE_SHORT,
	KEY_STATE_LONG
};


enum menu_entry {
	MENU_INTIME = 0,
	MENU_SOURCE = 1,
	MENU_HELP = 2
};
#define MENU_ENTRY_COUNT 3

enum menu_choice_digits {
	DIGITS_1 = 0,
	DIGITS_10 = 1,
	DIGITS_100 = 2
};

const unsigned int menu_choice_digits_to_intime[] = {
	[DIGITS_1] = 1,
	[DIGITS_10] = 10,
	[DIGITS_100] = 100
};


struct ui_state {
	uint32_t key_down_time;
	struct {
		enum menu_entry selected;
		bool open;
		unsigned int choices[MENU_ENTRY_COUNT];  // The actually currently choosen values
		unsigned int prechoice;  // Choice in a currently open menu
	} menu;
	struct {
		bool has_fix;
		unsigned int n_sats;
		uint32_t last_update;
	} gps;
	struct {
		bool flip;
		uint32_t last_value;
		uint32_t last_update;

		struct {
			uint16_t counter;
			uint64_t value;
			uint64_t output;
		} i;
	} pps;
	struct {
		bool menu : 1;
		bool pps : 1;
		bool gps : 1;
		bool value : 1;
	} dirty;
};



static struct ui_state ui_state = {
	.key_down_time = 0,
	.menu = {
		.selected = MENU_INTIME,
		.open = false,
		.choices = {
			0, 0, 0
		},
		.prechoice = 0
	},
	.gps = {
		.has_fix = false,
		.n_sats = 0,
		.last_update = 0
	},
	.pps = {
		.flip = false,
		.last_value = 0,
		.last_update = 0,
		.i = {
			.counter = 0,
			.value = 0,
			.output = 0
		},
	},
	.dirty = {
		.menu = true,
		.pps = true,
		.gps = true,
		.value = true
	}
};

#define DIRTY(_name) { ui_state.dirty._name = true; }


enum key_state current_key_state()
{
	if (ui_state.key_down_time == 0) {
		return KEY_STATE_NONE;
	}
	uint32_t length = time_ms() - ui_state.key_down_time;
	if (length < 1000) {
		return KEY_STATE_SHORT;
	}
	if (length < 7000) {
		return KEY_STATE_LONG;
	}
	return KEY_STATE_NONE;
}


struct menu_entry_descriptor {
	const char *choices[4];
};


const struct menu_entry_descriptor menu_entries[] = {
	[MENU_INTIME] = {
		.choices = {
			"^1",
			"^2",
			"^3",
			NULL
		}
	},
	[MENU_SOURCE] = {
		.choices = {
			"In",
			"Ex",
			NULL
		}
	},
	[MENU_HELP] = {
		.choices = {
			"-?",
			NULL
		}
	}
};


static void render_menu_button(const char *str, unsigned int x, unsigned int y, bool selected)
{
	blit_sprite(selected ? &sprite_menu_button_selected : &sprite_menu_button,
				x, y, OLED_BLIT_NORMAL);
	blit_string(&font_small_ascii, str,
				x + 5, y + 2, selected ? OLED_BLIT_NORMAL : OLED_BLIT_INVERT, 0);
}


static void render_menu_bar()
{
	const unsigned int y = OLED_HEIGHT - sprite_menu_button.height;
	const unsigned int button_width = sprite_menu_button.width + 1;

	// Render the root menu in the left-bottom corner
	unsigned int lx = 0;
	for (unsigned int i = 0; i < ARRAY_SIZE(menu_entries); i++) {
		bool selected = ui_state.menu.selected == i;
		const char *str = menu_entries[i].choices[ui_state.menu.choices[i]];
		render_menu_button(str, lx, y, selected);
		lx += button_width;
	}

	// Render the secondary menu in the right-bottom corner
	unsigned int rx = OLED_WIDTH;
	if (ui_state.menu.open) {
		const struct menu_entry_descriptor *desc = &menu_entries[ui_state.menu.selected];
		rx = OLED_WIDTH - sprite_menu_button.width;
		for (unsigned int i = 0; desc->choices[i] != NULL; i++) {
			bool selected = ui_state.menu.prechoice == i;
			render_menu_button(desc->choices[i], rx, y, selected);
			rx -= button_width;
		}
		rx += button_width;
	}
	oled_fill(lx, y, rx - lx, sprite_menu_button.height, OLED_BLIT_NORMAL);
}


static void render_status_key(enum key_state state)
{
	const struct sprite *s =
		state == KEY_STATE_NONE ? &sprite_status_key_1 :
		(state == KEY_STATE_SHORT ? &sprite_status_key_2 : &sprite_status_key_3);
	blit_sprite(s, 0, 0, OLED_BLIT_NORMAL);
}


static void render_status_gps()
{
	const unsigned int x = 14;
	const unsigned int y = 0;
	const struct sprite *s = ui_state.gps.has_fix ? &sprite_status_gps_fix : &sprite_status_gps_nofix;
	blit_sprite(s, x, y, OLED_BLIT_NORMAL);
	unsigned int idx = ui_state.gps.n_sats;
	if (time_ms() - ui_state.gps.last_update > 4000) {
		idx = 11;  // The '?' character
	} else if (idx > 10) {
		idx = 10;  // This is the '+' chracter
	}
	blit_font(&font_tiny_digits, idx, x + 2, y + 5, OLED_BLIT_NORMAL);
}


static void render_status_pps()
{
	const unsigned int x = 28;
	const unsigned int y = 0;
	const struct sprite *s = ui_state.pps.flip ? &sprite_status_pps_1 : &sprite_status_pps_2;

	blit_sprite(s, x, y, OLED_BLIT_NORMAL);
}

static void render_status_integration()
{
	const unsigned int x = 42 + 1;
	const unsigned int width = 8;
	const unsigned int y = 0;

	unsigned int incount = menu_choice_digits_to_intime[ui_state.menu.choices[MENU_INTIME]];
	unsigned int at = ui_state.pps.i.counter;

	unsigned int lines = 10;
	if (incount == 10) {
		lines = at;
	} if (incount == 100) {
		lines = at / 10;
	}

	oled_fill(x, y, width, lines, OLED_BLIT_INVERT);
	oled_fill(x, y + lines, width, 10 - lines, OLED_BLIT_NORMAL);

	// Make the progress bar a bit less dull
	for (unsigned int dy = 0; dy < 10; dy++) {
		if (dy % 4 < 2) {
			continue;
		}
		oled_draw_pixel(x, y + dy, false);
		oled_draw_pixel(x + width - 1, y + dy, false);
	}
}


static void render_status_signal()
{
	static unsigned int counter = 0;
	const unsigned int x = 28;
	const unsigned int y = 0;
	const unsigned int w = 10;
	const unsigned int h = 10;
	const unsigned int period = 6;

	bool prev = (counter/4) % period < period / 2;
	for (unsigned int dx = 0; dx < w; dx++) {
		bool high = ((counter/4) + dx + 1) % period < period / 2;

		for (unsigned int dy = 0; dy < h; dy++) {
			bool val;
			if (high != prev) {
				val = true;
			} else if (high) {
				val = dy == h - 1;
			} else {
				val = dy == 0;
			}
			oled_draw_pixel(x + dx, y + dy, val);
		}

		prev = high;
	}

	counter = (counter + 1);
}


static void reset_integration()
{
	ui_state.pps.i.counter = 0;
	ui_state.pps.i.value = 0;
	ui_state.pps.i.output = 0;
	DIRTY(value);
}


static void menu_next_entry()
{
	if (ui_state.menu.open) {
		ui_state.menu.prechoice++;
		if (menu_entries[ui_state.menu.selected].choices[ui_state.menu.prechoice] == NULL) {
			ui_state.menu.prechoice = 0;
		}
	} else {
		ui_state.menu.selected = (ui_state.menu.selected + 1) % MENU_ENTRY_COUNT;
	}
	DIRTY(menu);
}


static void menu_confirm()
{
	if (ui_state.menu.open) {
		// TODO
		ui_state.menu.open = false;
		unsigned int choice = ui_state.menu.prechoice;
		ui_state.menu.choices[ui_state.menu.selected] = choice;
		if (ui_state.menu.selected == MENU_INTIME) {
			reset_integration();
		}
	} else {
		ui_state.menu.open = true;
		ui_state.menu.prechoice = ui_state.menu.choices[ui_state.menu.selected];
	}
	DIRTY(menu);
}


void ui_init()
{
	// Nothing at this point
}


void ui_on_key_down()
{
	ui_state.key_down_time = time_ms();
}


void ui_on_key_up()
{
	enum key_state key = current_key_state();
	ui_state.key_down_time = 0;

	if (key == KEY_STATE_SHORT) {
		menu_next_entry();
	} else if (key == KEY_STATE_LONG) {
		menu_confirm();
	}
}


void ui_on_pps(uint32_t count)
{
	ui_state.pps.flip = !ui_state.pps.flip;

	uint32_t now = time_ms();
	if (now - ui_state.pps.last_update > 1100) {
		reset_integration();
	}

	ui_state.pps.last_value = count;
	ui_state.pps.last_update = time_ms();

	unsigned int incount = menu_choice_digits_to_intime[ui_state.menu.choices[MENU_INTIME]];

	if (incount == 0) {
		ui_state.pps.i.output = count;
		DIRTY(value);
	} else {
		ui_state.pps.i.counter++;
		ui_state.pps.i.value += count;
		if (ui_state.pps.i.counter == incount) {
			ui_state.pps.i.output = ui_state.pps.i.value;
			ui_state.pps.i.counter = 0;
			ui_state.pps.i.value = 0;
		}
		DIRTY(value);
	}

	DIRTY(pps);
}


static void render_value()
{
	const unsigned int group_sep_width = 4;

	uint32_t count = ui_state.pps.i.output;
	bool valid = count != 0;

	unsigned int extra_digits = ui_state.menu.choices[MENU_INTIME];

	unsigned int y = 20;
	unsigned int x = 90;
	oled_fill(0, y, OLED_WIDTH, font_large_digits.height, OLED_BLIT_NORMAL);
	if (extra_digits > 0) {
		x += extra_digits * font_large_digits.width + group_sep_width;
	}
	for (unsigned int i = 0; i < 8 + extra_digits; i++) {
		int digit = 10;  // The '-' character
		if (valid) {
			digit = count % 10;
			count /= 10;
		}
		blit_font(&font_large_digits, digit, x, y, OLED_BLIT_NORMAL);
		x -= font_large_digits.width;
		if ((i - extra_digits) % 3 == 2 || (extra_digits > 0 && i == (extra_digits - 1))) {
			x -= group_sep_width;
		}
	}
}



void ui_on_frame()
{
#define DIRTY_RUN(_name) \
	if (ui_state.dirty._name && !(ui_state.dirty._name = false))

	render_status_key(current_key_state());
	DIRTY_RUN(gps) {
		render_status_gps();
	}
	DIRTY_RUN(pps) {
		render_status_pps();
		render_status_integration();
	}
	DIRTY_RUN(value) {
		render_value();
	}
	DIRTY_RUN(menu) {
		render_menu_bar();
	}

#undef DIRTY_RUN
}


void ui_on_gps_status(bool has_fix)
{
	ui_state.gps.has_fix = has_fix;
	DIRTY(gps);
}

void ui_on_gps_svinfo(unsigned int n_sats)
{
	ui_state.gps.n_sats = n_sats;
	ui_state.gps.last_update = time_ms();
	DIRTY(gps);
}
