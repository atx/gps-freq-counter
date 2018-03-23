
/* At this point, we are not yet using the UFM, so copying from "flash" to RAM
 * is somewhat dumb, but whatever...
 * */
MEMORY
{
	flash (RX)	: org = 0x00000000, len = 12k
	ram (RWX)	: org = 0x20000000, len = 12k
}

ENTRY(_stub_start)

SECTIONS
{
	.stubs ORIGIN(flash) :
	{
		*(.stub._stub_start)
		. = ORIGIN(flash) + 0x10;
		*(.stub._stub_irq)
	} > flash

	.text :
	{
		_text_start = .;
		*(.text*)
		. = ALIGN(4);
		*(.rodata*)
		*(.gcc*)
		. = ALIGN(4);
		_text_end = .;
	} > flash

	.data :
	{
		_data_start = .;
		*(.sdata*)
		*(.data*)
		. = ALIGN(4);
		_data_end = .;
	} > ram AT > flash
	_data_load_start = LOADADDR(.data);
	_data_load_end = LOADADDR(.data) + SIZEOF(.data);

	.bss :
	{
		_bss_start = .;
		*(.sbss*)
		*(.bss*)
		*(COMMON)
		. = ALIGN(4);
		_bss_end = .;
	} > ram
}
