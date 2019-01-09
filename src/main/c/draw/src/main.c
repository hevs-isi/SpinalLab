#include <stdint.h>

#define CORE_HZ 12000000

#define GPIO_A    ((volatile uint32_t*)(0xF0000000))
#define UART      ((volatile uint32_t*)(0xF0010000))
#define DRAW      ((volatile uint32_t*)(0xF0030000))


void main() {
	DRAW[0] = 2;

	for(int i = 0; i < 16; i++){
		DRAW[2] = (i >= 7) ? 0x4000 : 0xC000;
		DRAW[3] = (i << 11);
	}

	DRAW[1] = 0x0008; //0x4000 for simulation, 0x0008 IRL
	DRAW[0] = 1;
}

void irqCallback(){

}



