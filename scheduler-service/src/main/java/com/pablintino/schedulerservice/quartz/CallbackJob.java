package com.pablintino.schedulerservice.quartz;

import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.ICallbackService;
import com.pablintino.schedulerservice.services.IJobParamsEncoder;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class CallbackJob implements Job {

    private ICallbackService callbackService;
    private IJobParamsEncoder jobParamsEncoder;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Job " + context.getJobDetail() + " starts its execution");
        try{
            JobDataMap jobDataMap = context.getMergedJobDataMap();
            SchedulerJobData jobData = jobParamsEncoder.extractDecodeJobParameters(jobDataMap);
            callbackService.executeCallback(jobData, jobDataMap);
        }catch (SchedulerValidationException ex){
            log.error("Validation exception while running JOB" + context.getJobDetail(), ex);
            throw new JobExecutionException(ex, false);
        }
        log.debug("Job " + context.getJobDetail() + " finished its execution");
    }

    @Autowired
    public void setJobParamsEncoder(IJobParamsEncoder jobParamsEncoder) {
        this.jobParamsEncoder = jobParamsEncoder;
    }

    @Autowired
    public void setCallbackService(ICallbackService callbackService) {
        this.callbackService = callbackService;
    }
}
