

## SoC

```sh
cd ~/spinal/openocd_riscv
src/openocd -f tcl/interface/jtag_tcp.cfg -c 'set MURAX_CPU0_YAML ../SpinalLab/cpu0.yaml' -f tcl/target/murax.cfg

cd ~/spinal/openocd_riscv
sudo src/openocd -f tcl/interface/ftdi/ft2232h_breakout.cfg -c 'set MURAX_CPU0_YAML ../SpinalLab/cpu0.yaml' -f tcl/target/murax.cfg

=> 

Open On-Chip Debugger 0.10.0+dev-01200-g9e90242 (2019-01-07-15:08)
Licensed under GNU GPL v2
For bug reports, read
	http://openocd.org/doc/doxygen/bugs.html
../SpinalLab/cpu0.yaml
adapter speed: 800 kHz
adapter_nsrst_delay: 260
Info : auto-selecting first available session transport "jtag". To override use 'transport select <transport>'.
jtag_ntrst_delay: 250
Info : set servers polling period to 50ms
Info : clock speed 800 kHz
Info : JTAG tap: fpga_spinal.bridge tap/device found: 0x10001fff (mfg: 0x7ff (<invalid>), part: 0x0001, ver: 0x1)
Info : Listening on port 3333 for gdb connections
requesting target halt and executing a soft reset
Info : Listening on port 6666 for tcl connections
Info : Listening on port 4444 for telnet connections



ls -la /dev/ttyUSB1
sudo chmod 666 /dev/ttyUSB1
stty -F /dev/ttyUSB1 9600 cs8 -cstopb -parenb
cu -l /dev/ttyUSB1

```