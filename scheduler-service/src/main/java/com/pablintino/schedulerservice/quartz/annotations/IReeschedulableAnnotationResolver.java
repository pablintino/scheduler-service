package com.pablintino.schedulerservice.quartz.annotations;

import java.util.Set;

public interface IReeschedulableAnnotationResolver {

  Set<Class> getAnnotatedTypes();
}
