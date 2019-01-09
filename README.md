

## SoC

```sh
cd ~/spinal/openocd_riscv
src/openocd -f tcl/interface/jtag_tcp.cfg -c 'set MURAX_CPU0_YAML ../SpinalLab/cpu0.yaml' -f tcl/target/murax.cfg

cd ~/spinal/openocd_riscv
sudo src/openocd -f tcl/interface/ftdi/ft2232h_breakout.cfg -c 'set MURAX_CPU0_YAML ../SpinalLab/cpu0.yaml' -f tcl/target/murax.cfg


ls -la /dev/ttyUSB1
sudo chmod 666 /dev/ttyUSB1
stty -F /dev/ttyUSB1 9600 cs8 -cstopb -parenb
cu -l /dev/ttyUSB1

```