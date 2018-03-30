

#include <assert.h>
#include <string.h>

#include "spi.h"
#include "regs.h"

#include "oled.h"


// References:
// https://github.com/adafruit/Adafruit_SSD1306/blob/master/Adafruit_SSD1306.cpp
// https://cdn-shop.adafruit.com/datasheets/SSD1306.pdf
// https://github.com/adafruit/Adafruit_SSD1306/blob/master/Adafruit_SSD1306.h


#define OLED_SETCONTRAST 0x81
#define OLED_DISPLAYALLON_RESUME 0xA4
#define OLED_DISPLAYALLON 0xA5
#define OLED_NORMALDISPLAY 0xA6
#define OLED_INVERTDISPLAY 0xA7
#define OLED_DISPLAYOFF 0xAE
#define OLED_DISPLAYON 0xAF

#define OLED_SETDISPLAYOFFSET 0xD3
#define OLED_SETCOMPINS 0xDA

#define OLED_SETVCOMDETECT 0xDB

#define OLED_SETDISPLAYCLOCKDIV 0xD5
#define OLED_SETPRECHARGE 0xD9

#define OLED_SETMULTIPLEX 0xA8

#define OLED_SETLOWCOLUMN 0x00
#define OLED_SETHIGHCOLUMN 0x10

#define OLED_SETSTARTLINE 0x40

#define OLED_MEMORYMODE 0x20
#define OLED_COLUMNADDR 0x21
#define OLED_PAGEADDR   0x22

#define OLED_COMSCANINC 0xC0
#define OLED_COMSCANDEC 0xC8

#define OLED_SEGREMAP 0xA0

#define OLED_CHARGEPUMP 0x8D

#define OLED_EXTERNALVCC 0x1
#define OLED_SWITCHCAPVCC 0x2

// Scrolling #defines
#define OLED_ACTIVATE_SCROLL 0x2F
#define OLED_DEACTIVATE_SCROLL 0x2E
#define OLED_SET_VERTICAL_SCROLL_AREA 0xA3
#define OLED_RIGHT_HORIZONTAL_SCROLL 0x26
#define OLED_LEFT_HORIZONTAL_SCROLL 0x27
#define OLED_VERTICAL_AND_RIGHT_HORIZONTAL_SCROLL 0x29
#define OLED_VERTICAL_AND_LEFT_HORIZONTAL_SCROLL 0x2A

#define CMDBUFF_OFFSET		0
#define CMDBUFF_LEN			4
#define FRAMEBUFF_OFFSET	CMDBUFF_LEN
#define FRAMEBUFF_LEN		((OLED_HEIGHT * OLED_WIDTH) / 8)

static_assert(SPI_BUFFER_LENGTH >= CMDBUFF_LEN + FRAMEBUFF_LEN, "SPI buffer too small!");

static volatile uint8_t *cmdbuff;
static volatile uint8_t *framebuff;


#define COMMANDS(cmds) { \
	for (unsigned int i = 0; i < ARRAY_SIZE(cmds); i++) { \
		oled_command(cmds[i]); \
		nop_loop(10); \
	} \
}


void oled_init()
{
	cmdbuff = spi_get_buffer_pointer(0);
	framebuff = spi_get_buffer_pointer(4);

	// TODO: Get a timer or something?
	output_high(OUTPUT_OLED_RST);
	nop_loop(10000);
	output_low(OUTPUT_OLED_RST);
	nop_loop(100000);
	output_high(OUTPUT_OLED_RST);

	COMMANDS(((uint8_t []){
		OLED_DISPLAYOFF,
		OLED_SETDISPLAYCLOCKDIV, 0x80,
		OLED_SETMULTIPLEX, OLED_HEIGHT - 1,
		OLED_SETDISPLAYOFFSET, 0x00,
		OLED_SETSTARTLINE | 0x00,
		OLED_CHARGEPUMP, 0x14,
		OLED_MEMORYMODE, 0x00,
		OLED_SEGREMAP | 0x1,
		OLED_COMSCANDEC,
		OLED_SETCOMPINS, 0x12,
		OLED_SETCONTRAST, 0xcf,
		OLED_SETPRECHARGE, 0xf1,
		OLED_SETVCOMDETECT, 0x40,
		OLED_DISPLAYALLON_RESUME,
		OLED_NORMALDISPLAY,
		OLED_DEACTIVATE_SCROLL,
		OLED_DISPLAYON
	}));

	oled_clear();
}


void oled_command(uint8_t cmd)
{
	output_low(OUTPUT_OLED_DC);
	cmdbuff[0] = cmd;
	spi_start(CMDBUFF_OFFSET, 1);
}


void oled_draw_pixel(unsigned int x, unsigned int y, bool on)
{
	volatile uint8_t *p = &framebuff[x + (y/8)*OLED_WIDTH];
	uint8_t mask = 1 << (y % 8);
	if (on) {
		*p |=  mask;
	} else {
		*p &= ~mask;
	}
}


void oled_clear()
{
	memset((uint8_t *)framebuff, 0x00, FRAMEBUFF_LEN);
}


void oled_flush()
{
	COMMANDS(((uint8_t []){
		OLED_COLUMNADDR, 0, OLED_WIDTH - 1,
		OLED_PAGEADDR, 0, 7
	}));

	output_high(OUTPUT_OLED_DC);
	spi_start(FRAMEBUFF_OFFSET, FRAMEBUFF_LEN);
}
