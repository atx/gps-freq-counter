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

set (CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -march=rv32e -mabi=ilp32e")
set (CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Os -ggdb -march=rv32e -mabi=ilp32e -ffunction-sections")
set (CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -nostartfiles -T ${CMAKE_SOURCE_DIR}/linker.ld -Wl,--gc-sections")

set (CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Wno-main-return-type -Wno-main -Wno-unused-parameter")

add_executable (gfc main.c reset.c reset.s linker.ld)
add_executable (test_fib test_fib.c reset.c reset.s linker.ld)
add_executable (test_spi test_spi.c reset.c spi.c reset.s linker.ld)

get_property (_targets DIRECTORY PROPERTY BUILDSYSTEM_TARGETS)
foreach (target_name ${_targets})
	add_custom_command (
		TARGET ${target_name} POST_BUILD
		COMMAND ${CMAKE_OBJCOPY} -O binary $<TARGET_FILE:${target_name}> $<TARGET_FILE:${target_name}>.bin
		WORKING_DIRECTORY ${CMAKE_BINARY_DIR}
	)
endforeach ()
