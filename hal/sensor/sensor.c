/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <errno.h>
#include <string.h>

#include <cutils/log.h>
#include <hardware/hardware.h>
#include <hardware/sensors.h>

#include <netinet/in.h>    // for sockaddr_in
#include <sys/types.h>    // for socket
#include <sys/socket.h>    // for socket
#include <stdio.h>        // for printf
#include <stdlib.h>        // for exit
#include <string.h>        // for bzero
#include <math.h>


#define NET_PORT 6666
#define LENGTH_OF_LISTEN_QUEUE 20
#define BUFFER_SIZE 1024
#define FILE_NAME_MAX_SIZE 512
int server_socket;
typedef unsigned char byte;

int fourBytesToInt(byte b1, byte b2, byte b3, byte b4)
{
     return ( b1 << 24 ) + ( b2 << 16 ) + ( b3 << 8 ) + b4;
}

float intBitsToFloat(int bits)
{
   int s = (bits >> 31) == 0 ? 1: -1;
   int e = (bits >> 23) & 0xff;
   int m = (e == 0) ? (bits & 0x7fffff) << 1: (bits & 0x7fffff ) | 0x800000;
   return s * m * ( float ) pow( 2, e - 150 );
}

void open_socket( void)
{
	struct sockaddr_in  server_addr;
	bzero(&server_addr, sizeof(server_addr));
	 server_addr.sin_family = AF_INET; 
	 server_addr.sin_addr.s_addr = htons(INADDR_ANY);
	 server_addr.sin_port = htons(NET_PORT);  
	
	 server_socket = socket(AF_INET,SOCK_STREAM,0);

	if (server_socket < 0) {
		printf("create socketfd feild");
		exit(1);

	}

	if( bind(server_socket, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0)
	{
		printf("Server Bind Port : %d Failed!",NET_PORT);
		exit(1);
	}
	
	if ( listen(server_socket, LENGTH_OF_LISTEN_QUEUE) )
	{
		printf("Server Listen Failed!");
		exit(1);
	}
	printf("open_socket success\n");

}

void recv_tcp_to_sensors_port(sensors_event_t* data, int count)
{
	struct sockaddr_in client_addr;
	socklen_t length = sizeof(client_addr);                                
	int new_server_socket = accept(server_socket, (struct sockaddr*)&client_addr, &length);
    	while(1)
	{
		if ( new_server_socket < 0)
		{
			printf("Server Accept Failed!\n");
			break;
		}
		 byte buffer[BUFFER_SIZE];
		 bzero(buffer, BUFFER_SIZE);
		 length = recv(new_server_socket, buffer, BUFFER_SIZE,0);
		if (length < 0)
		{
			printf("Server Recieve Data Failed!\n");
			break;
		}
		int i =0;
		for (; i < length; i++)
		{
		    printf("buffer[%d] = %x ", i, buffer[i]);
 		}
		float x = intBitsToFloat( fourBytesToInt( buffer[0], buffer[1], buffer[2], buffer[3] )); 
		float y = intBitsToFloat( fourBytesToInt( buffer[4], buffer[5], buffer[6], buffer[7] ));
		float z = intBitsToFloat( fourBytesToInt( buffer[8], buffer[9], buffer[10], buffer[11] ));
		printf("\n x = %f,  y = %f, z = %f \n", x, y, z);
	}
	close(new_server_socket);
}


static const struct sensor_t sSensorList[] = {  
        {    "ST 3-axis Gyroscope sensor",       
             "STMicroelectronics",  
              1, 10,  
              SENSOR_TYPE_GYROSCOPE, 4.0f*9.81f, 
		     (4.0f*9.81f)/256.0f, 0.2f, 0, { } },
} ;


static int poll__activate(struct sensors_poll_device_t *dev,  
        int handle, int enabled) {  
    //sensors_poll_context_t *ctx = (sensors_poll_context_t *)dev;  
   // return ctx->activate(handle, enabled);  
   return 0;
}  
  
  
static int poll__setDelay(struct sensors_poll_device_t *dev,  
        int handle, int64_t ns) {  
   // sensors_poll_context_t *ctx = (sensors_poll_context_t *)dev;  
  //  return ctx->setDelay(handle, ns); 
	return 0;
}  
  
static int  poll__poll(struct sensors_poll_device_t *dev,  
        sensors_event_t* data, int count) {  
  //while(1)
  //sleep(1000);
  
   printf("count = %d\n", count); 
   data->version = 1;
   data->sensor = 2;
   data->type = SENSOR_TYPE_GYROSCOPE;
   data->reserved0 = 0;
   data->timestamp = 0;
   memset(data->reserved1, 0, sizeof(data->reserved1));
   recv_tcp_to_sensors_port(data, count);
   close(server_socket);
   return 0;
}    

/*
 * NCI HAL method implementations. These must be overriden
 */

static int sensors_get_sensors_list(struct sensors_module_t* module,  
        struct sensor_t const** list)  
{  
    *list = sSensorList;  
    return 1;  
}  
/*
 * Generic device handling below - can generally be left unchanged.
 */
/* Close an opened nfc device instance */
static int poll_close(hw_device_t *dev) {
	struct sensors_poll_device_t * sen = (struct sensors_poll_device_t *)dev;
    free(sen);
    return 0;
}

static int open_sensors(const hw_module_t* module, const char* name,
        hw_device_t** device) {
    if (1) {
        struct sensors_poll_device_t *dev = calloc(1, sizeof(struct sensors_poll_device_t));

        dev->common.tag = HARDWARE_DEVICE_TAG;
        dev->common.version = 0; // [31:16] major, [15:0] minor
        dev->common.module = (struct hw_module_t*) module;
        dev->common.close = poll_close;
        dev->activate  = poll__activate;  
        dev->setDelay = poll__setDelay;  
        dev->poll     = poll__poll;  
        // NCI HAL method pointers

        *device = (hw_device_t*) dev;
	
   	open_socket();
        return 0;
    } else {
        return -EINVAL;
    }
}


static struct hw_module_methods_t sensors_module_methods = {
    .open = open_sensors,
};

struct sensors_module_t HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .version_major = 1, 
        .version_minor = 0, 
        .id = SENSORS_HARDWARE_MODULE_ID,
        .name = "MMA8451Q & AK8973A & gyro Sensors Module", 
        .author = "The Android Project",  
        .methods = &sensors_module_methods,
    },
	.get_sensors_list = sensors_get_sensors_list 
};
