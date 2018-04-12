
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
		uint64_t value;
	} pps;
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
	}
};


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
	render_menu_bar();
}


static void menu_confirm()
{
	if (ui_state.menu.open) {
		// TODO
		ui_state.menu.open = false;
		ui_state.menu.choices[ui_state.menu.selected] = ui_state.menu.prechoice;
	} else {
		ui_state.menu.open = true;
		ui_state.menu.prechoice = ui_state.menu.choices[ui_state.menu.selected];
	}
	render_menu_bar();
}


void ui_init()
{
	render_menu_bar();
	render_status_key(KEY_STATE_NONE);
	render_status_gps();
}


void ui_on_key_down()
{
	ui_state.key_down_time = time_ms();
	render_status_key(KEY_STATE_SHORT);
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
	// TODO: Longer integration times
	ui_state.pps.value = count;

	unsigned int y = 20;
	unsigned int x = 80;
	for (unsigned int i = 0; i < 8; i++) {
		int digit = count % 10;
		count /= 10;
		blit_font(&font_large_digits, digit, x, y, OLED_BLIT_NORMAL);
		x -= font_large_digits.width - 1;
	}
}


void ui_on_frame()
{
	render_status_key(current_key_state());
	render_status_gps();
	render_status_pps();
}


void ui_on_gps_status(bool has_fix)
{
	ui_state.gps.has_fix = has_fix;
}

void ui_on_gps_svinfo(unsigned int n_sats)
{
	ui_state.gps.n_sats = n_sats;
	ui_state.gps.last_update = time_ms();
}
