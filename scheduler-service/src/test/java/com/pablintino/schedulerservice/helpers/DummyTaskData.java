package com.pablintino.schedulerservice.helpers;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode
public class DummyTaskData {
  private Integer testInt;
  private Float testFloat;
  private Date testDate;
  private List<String> testList;
  private Map<String, Integer> testMap;
}
