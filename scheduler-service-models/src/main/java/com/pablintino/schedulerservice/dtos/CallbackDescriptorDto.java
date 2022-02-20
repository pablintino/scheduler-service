package com.pablintino.schedulerservice.dtos;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class CallbackDescriptorDto {
  @NotNull private CallbackMethodTypeDto type;

  private String endpoint;
}
