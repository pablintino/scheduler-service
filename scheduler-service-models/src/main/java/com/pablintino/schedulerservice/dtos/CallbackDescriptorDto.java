package com.pablintino.schedulerservice.dtos;

import lombok.Data;

@Data
public class CallbackDescriptorDto {
    private CallbackMethodTypeDto type;
    private String endpoint;
}
