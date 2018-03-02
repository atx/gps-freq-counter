#! /usr/bin/env fish

if not count $argv >/dev/null
	echo "Nope"
	exit
end

cd test_run_dir
echo "Watching $argv[1]"

while true;
	set recent (ls . --sort=time | grep $argv[1] | head -n1)
	echo $recent
	cp -vfs $recent/*.vcd vcd.vcd
	sleep 1
end
