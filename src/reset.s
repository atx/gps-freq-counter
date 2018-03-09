
.section .stub._stub_start
.globl _stub_start

_stub_start:
	li a0, 10
	j reset_handler

.section .stub._stub_irq
.globl _stub_irq

_stub_irq:
	# TODO: Register setup here
	j irq_handler
