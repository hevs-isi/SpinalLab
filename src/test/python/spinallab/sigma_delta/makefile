VHDL_SOURCES += $(shell pwd)/../../../../../SigmaDelta.vhd
TOPLEVEL=sigmadelta
MODULE=Tests

export PYTHONPATH:=../../../
SIM=ghdl
SIM_ARGS += --ieee-asserts=disable
SIM_ARGS +=--vcd=vhdl.vcd

export COCOTB_REDUCED_LOG_FMT=1

include $(COCOTB)/makefiles/Makefile.inc
include $(COCOTB)/makefiles/Makefile.sim