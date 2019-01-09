#include <stdint.h>

#define CORE_HZ 66000000/4

#define GPIO_A    ((volatile uint32_t*)(0xF0000000))
#define UART      ((volatile uint32_t*)(0xF0010000))

void main() {
	GPIO_A[1] = 0x000000FF;
	int counter = 'a';
	while(1){
		//uint32_t value = ~(GPIO_A[0] >> 8);
		uint32_t value = GPIO_A[0] + 1;
		GPIO_A[0] = value;
		int32_t readed = ((int*) 0xF0010000)[0];
		if((readed & 0x10000) != 0)
			((int*) 0xF0010000)[0] = readed & 0xFF;
		//while(((int*) 0xF0010000)[1] >> 16 == 0);
		//((int*) 0xF0010000)[0] = counter++;
		//if(counter == 'z' + 1) counter = 'a';
	}
}

void irqCallback(){

}



