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

//#define SENSORS_GYROSCOPE_HANDLE        (ID_GY)
#define NET_PORT 5885
#define LENGTH_OF_LISTEN_QUEUE 20
#define BUFFER_SIZE 1024
#define FILE_NAME_MAX_SIZE 512
int server_socket;
int new_server_socket;
struct sockaddr_in client_addr;
 socklen_t length ;



void open_socket( void)
{
	struct sockaddr_in  server_addr;
	bzero(&server_addr,sizeof(server_addr));
	 server_addr.sin_family = AF_INET; 
	 server_addr.sin_addr.s_addr = htons(INADDR_ANY);
	 server_addr.sin_port = htons(NET_PORT);  
	
	 server_socket = socket(PF_INET,SOCK_STREAM,0);
     
	if (server_socket > 0) {
		ALOGD("create socketfd %d ========================\n",server_socket);
	
	}

	if( bind(server_socket,(struct sockaddr*)&server_addr,sizeof(server_addr))==0)
	{
		ALOGD("Server Bind Port : %d OK==========\n",NET_PORT);
		
	}
	
	if ( listen(server_socket, LENGTH_OF_LISTEN_QUEUE)==0 )
	{
		ALOGD("Server Listen OK=====\n");
		
	}
	

   //length = sizeof(client_addr);
   //new_server_socket = accept(server_socket,(struct sockaddr*)&client_addr,&length);	

}

static unsigned int  InCom_num = 0 ; //收到数据 不是一个完整sensors_event_t  个数
static char tbuffer[sizeof(sensors_event_t)];

int recv_tcp_to_sensors( sensors_event_t* data, int count)
{
/*
	InCom_num = Num - Com_Length_buffer 剩余不完整sensors_event_t数据长度
	Com_buffer_Length_addr = buffer + Com_Length_buffer 剩余数据在内存中起始地址
	InCom_buffer_addr = buffer + InCom_num; 剩余数据copy到buffer内存中结束地址
	
*/
	 
	char *buffer =(char *)data;
	  
	
	int buffersize = sizeof(sensors_event_t)*count;
	
    
    int Num = recv(new_server_socket,buffer + InCom_num,buffersize - InCom_num,0); // copy数据从上次剩余copy后地址开始
	// Com_Length_buffer = 0;
	ALOGD("Num data is uuuu======== %d \n",Num);

    if (Num <= 0)
     {
		InCom_num = 0;
		ALOGD("Server Recieve Data 0!\n");
		return -1;
      }
	
	  memcpy(buffer,tbuffer,InCom_num);
	ALOGD("InCom_numr data is ======== %d \n",InCom_num); 
	 
	  Num = Num + InCom_num;
	  int Complete_num = Num/(sizeof(sensors_event_t));
	  InCom_num = Num%(sizeof(sensors_event_t));
      ALOGD("Com_Length_buffer data is ======== %d \n",InCom_num); 
	  
	int Com_Length_buffer = Complete_num*(sizeof(sensors_event_t));
	
	ALOGD("Com_Length_bufferdata is ======== %d \n",Com_Length_buffer); 
	
	if(InCom_num!=0)
	{
		memcpy(tbuffer,buffer + Com_Length_buffer,InCom_num); //将剩余数据copy到buffer开始地址
	}	
	//bzero(buffer, BUFFER_SIZE);
	
	  	ALOGD("Complete_num data is ======== %d \n",Complete_num); 
	return   Complete_num;
	 /* data->acceleration.x = buffer[0];
	  data->acceleration.y = buffer[1];;
	  data->acceleration.z = buffer[2]; 
	  data->version = sizeof(sensors_event_t);
	  data->sensor = 0;
	  data->type = SENSOR_TYPE_GYROSCOPE;  
	  data->acceleration.status = SENSOR_STATUS_ACCURACY_HIGH;*/  
}


static const struct sensor_t sSensorList[] = {  
        {    "ST 3-axis Gyroscope sensor",       
             "STMicroelectronics",  
              1, 1,  
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
  
static int  poll_poll(struct sensors_poll_device_t *dev,  
        sensors_event_t* data, int count) { 
		
		int n = 0;	
	 do{
		if ( new_server_socket < 0) 
        {
			new_server_socket = accept(server_socket,(struct sockaddr*)&client_addr,&length);
			if ( new_server_socket < 0)
			{   
				ALOGD("Server Accept Failed!\n");
				continue;
			}
     	}
		    
			
		n = recv_tcp_to_sensors(data,count);
		if(n <= 0)
		{
			close(new_server_socket);
			new_server_socket = -1;
			continue;
		}
			
		
		
		
	 }while(n <=0);
		
		
    
	return n;
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
	close(new_server_socket);
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
        dev->poll     = poll_poll;  
        // NCI HAL method pointers

        *device = (hw_device_t*) dev;
		open_socket( );

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
