cmake_minimum_required (VERSION 3.7)
project (gfc C ASM)

set (CMAKE_C_STANDARD 11)
set (CMAKE_EXPORT_COMPILE_COMMANDS ON)
set (CMAKE_SYSTEM_NAME "Generic")
set (CROSS_COMPILE riscv32-unknown-elf-)
set (CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

set (CMAKE_C_COMPILER   "${CROSS_COMPILE}gcc")
set (CMAKE_ASM_COMPILER	"${CROSS_COMPILE}as")
set (CMAKE_OBJCOPY		"${CROSS_COMPILE}objcopy")
set (CMAKE_SIZE			"${CROSS_COMPILE}size")

set (CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -march=rv32e -mabi=ilp32e")
set (CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Os -ggdb -march=rv32e -mabi=ilp32e -ffunction-sections -mstrict-align")
set (CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -specs=nano.specs -nostartfiles -T ${CMAKE_SOURCE_DIR}/linker.ld -Wl,--gc-sections")

set (CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Wno-main-return-type -Wno-main -Wno-unused-parameter")

add_custom_command (
	OUTPUT sprites_data.h
	COMMAND ${CMAKE_SOURCE_DIR}/generate_sprites.sh ${CMAKE_BINARY_DIR}/sprites_data.h
	WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
	DEPENDS ${CMAKE_SOURCE_DIR}/generate_sprites.sh
	COMMENT "Generating sprites"
)

include_directories (${CMAKE_BINARY_DIR})

set (COMMON_CSRCS reset.c reset.s linker.ld)

add_executable (gfc main.c sprites_data.h ublox.c ui.c oled.c spi.c usb.c ${COMMON_CSRCS})
add_executable (test_fib test_fib.c ${COMMON_CSRCS})
add_executable (test_spi test_spi.c spi.c ${COMMON_CSRCS})

get_property (_targets DIRECTORY PROPERTY BUILDSYSTEM_TARGETS)
foreach (target_name gfc test_fib test_spi)
	add_custom_target (${target_name}.bin ALL
		DEPENDS ${target_name}
		COMMAND ${CMAKE_OBJCOPY} -O binary $<TARGET_FILE:${target_name}> $<TARGET_FILE:${target_name}>.bin
		WORKING_DIRECTORY ${CMAKE_BINARY_DIR}
	)
	add_custom_target (${target_name}.memh ALL
		DEPENDS ${target_name}.bin
		COMMAND ${CMAKE_SOURCE_DIR}/bintoverilog.py $<TARGET_FILE:${target_name}>.bin $<TARGET_FILE:${target_name}>.memh
		WORKING_DIRECTORY ${CMAKE_BINARY_DIR}
	)
	add_custom_target (${target_name}.mif ALL
		DEPENDS ${target_name}.bin
		COMMAND ${CMAKE_SOURCE_DIR}/bintoverilog.py -f mif $<TARGET_FILE:${target_name}>.bin $<TARGET_FILE:${target_name}>.mif
		WORKING_DIRECTORY ${CMAKE_BINARY_DIR}
	)
endforeach ()

add_custom_command (
	TARGET gfc POST_BUILD
	COMMAND ${CMAKE_SIZE} $<TARGET_FILE:gfc>
)
