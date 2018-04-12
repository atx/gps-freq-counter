
create_clock -period 33.333 -name oscillator [get_ports io_oscillator]
derive_pll_clocks
derive_clock_uncertainty


# I honestly have no idea what am I doing, let's hope these magical commands
# don't fuck something up

set_max_delay  1.0 -from [all_inputs]
set_min_delay -1.0 -from [all_inputs]
set_false_path -from * -to [all_outputs]
