import cocotb
from cocotb.decorators import coroutine
from cocotblib.Apb3 import Apb3
from cocotblib.TriState import TriState
from cocotblib.misc import ClockDomainAsyncReset, simulationSpeedPrinter, SimulationTimeout
from cocotb.triggers import Timer, RisingEdge


@cocotb.test()
def test1(dut):
    cocotb.fork(ClockDomainAsyncReset(dut.clk, dut.reset, 1000))
    cocotb.fork(simulationSpeedPrinter(dut.clk))
#    cocotb.fork(SimulationTimeout(1000 * 20e3))

    dut.io_step <= 1

    for i in xrange(8000):
        yield RisingEdge(dut.clk)