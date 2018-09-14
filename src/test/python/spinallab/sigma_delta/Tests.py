import cocotb
from cocotb.decorators import coroutine
from cocotblib.Apb3 import Apb3
from cocotblib.TriState import TriState
from cocotblib.misc import ClockDomainAsyncReset, simulationSpeedPrinter, SimulationTimeout
from cocotb.triggers import Timer, RisingEdge

@cocotb.coroutine
def debugFilter(dut):
    filter = 0.0
    while True:
        yield RisingEdge(dut.clk)
        filter = filter*0.95 + int(dut.io_serialout)*0.05
        dut.io_debug_filtred <= int(filter*1023)

@cocotb.test()
def test1(dut):
    cocotb.fork(ClockDomainAsyncReset(dut.clk, dut.reset, 1000))
    cocotb.fork(simulationSpeedPrinter(dut.clk))
#    cocotb.fork(SimulationTimeout(1000 * 20e3))
    cocotb.fork(debugFilter(dut))

    dut.io_parallelIn <= 0x10
    for i in xrange(200):
        yield RisingEdge(dut.clk)

    dut.io_parallelIn <= 0x70
    for i in xrange(1000):
        yield RisingEdge(dut.clk)

    dut.io_parallelIn <= 0x80
    for i in xrange(1000):
        yield RisingEdge(dut.clk)

    dut.io_parallelIn <= 0x90
    for i in xrange(1000):
        yield RisingEdge(dut.clk)


    dut.io_parallelIn <= 0xF0
    for i in xrange(200):
        yield RisingEdge(dut.clk)