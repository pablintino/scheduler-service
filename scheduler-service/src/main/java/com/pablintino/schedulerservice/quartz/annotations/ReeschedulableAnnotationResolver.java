package com.pablintino.schedulerservice.quartz.annotations;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ReeschedulableAnnotationResolver implements IReeschedulableAnnotationResolver{

    private final ClassPathScanningCandidateComponentProvider candidateComponentProvider;

    private final Set<Class> types;

    public ReeschedulableAnnotationResolver() {
        Set<Class> tempTypes = new HashSet<>();
        candidateComponentProvider = new ClassPathScanningCandidateComponentProvider(false);
        candidateComponentProvider.addIncludeFilter(new AnnotationTypeFilter(Reeschedulable.class));
        String basePackage = String.join(".", Arrays.copyOfRange(this.getClass().getCanonicalName().split("\\."), 0, 2));
        for (String className : candidateComponentProvider.findCandidateComponents(basePackage).stream().map(BeanDefinition::getBeanClassName).collect(Collectors.toList())) {
            try {
                tempTypes.add(Class.forName(className));
            } catch (ClassNotFoundException ex) {
                log.error("Cannot load annotated class " + className);
            }
        }
        types = Collections.unmodifiableSet(tempTypes);
    }

    @Override
    public Set<Class> getAnnotatedTypes() {
        return types;
    }
}
