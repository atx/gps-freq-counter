
/* At this point, we are not yet using the UFM, so let's just stick everything
 * to one memory mapped RAM segment.
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
		*(.text)
		*(.text.*)
		*(.rodata)
		*(.rodata.*)
		*(.gcc*)
		_text_end = .;
	} > flash

	.data :
	{
		_data_start = .;
		*(.data)
		*(.data.*)
		_data_end = .;
	} > ram AT > flash

	.bss :
	{
		*(.bss)
		*(.bss.*)
	} > ram
}
