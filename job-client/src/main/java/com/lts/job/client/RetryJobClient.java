package com.lts.job.client;

import com.lts.job.client.domain.JobClientApplication;
import com.lts.job.client.domain.JobClientNode;
import com.lts.job.client.domain.Response;
import com.lts.job.client.domain.ResponseCode;
import com.lts.job.core.domain.Job;
import com.lts.job.core.exception.JobSubmitException;
import com.lts.job.core.support.RetryScheduler;

import java.util.Arrays;
import java.util.List;

/**
 * @author Robert HG (254963746@qq.com) on 8/14/14.
 *         重试 客户端, 如果 没有可用的JobTracker, 那么存文件, 定时重试
 */
public class RetryJobClient extends JobClient<JobClientNode, JobClientApplication> {

    private RetryScheduler retryScheduler;

    @Override
    protected void innerStart() {

        retryScheduler = new RetryScheduler<Job>(application, 30) {
            @Override
            protected boolean isRemotingEnable() {
                return isServerEnable();
            }

            @Override
            protected boolean retry(List<Job> jobs) {
                try {
                    return superSubmitJob(jobs).isSuccess();
                } catch (Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                }
                return false;
            }
        };
        super.innerStart();
        retryScheduler.start();
    }

    @Override
    protected void innerStop() {
        super.innerStop();
        retryScheduler.stop();
    }

    @Override
    public Response submitJob(Job job) throws JobSubmitException {
        return submitJob(Arrays.asList(job));
    }

    @Override
    public Response submitJob(List<Job> jobs) throws JobSubmitException {
        Response response = superSubmitJob(jobs);

        if (!response.isSuccess()) {
            try {
                for (Job job : response.getFailedJobs()) {
                    retryScheduler.inSchedule(job.getTaskId(), job);
                }
                response.setSuccess(true);
                response.setCode(ResponseCode.FAILED_AND_SAVE_FILE);
                response.setMsg(response.getMsg() + ", but save local fail store and send later !");
            } catch (Exception e) {
                response.setSuccess(false);
                response.setMsg(e.getMessage());
            }
        }

        return response;
    }

    private Response superSubmitJob(List<Job> jobs) throws JobSubmitException {
        return super.submitJob(jobs);
    }
}
