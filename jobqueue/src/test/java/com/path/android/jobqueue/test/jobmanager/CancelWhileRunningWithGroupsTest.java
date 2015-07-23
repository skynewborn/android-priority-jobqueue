package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.CancelResult;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class CancelWhileRunningWithGroupsTest extends JobManagerTestBase {
    public static CountDownLatch[] endLatches = new CountDownLatch[]{new CountDownLatch(2), new CountDownLatch(2)};
    public static CountDownLatch[] startLatches = new CountDownLatch[]{new CountDownLatch(2), new CountDownLatch(2)};
    @Test
    public void testCancelBeforeRunning() throws InterruptedException {
        JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application).minConsumerCount(5));
        jobManager.addJob(new DummyJobWithLatches(0, new Params(1).addTags("dummyTag").groupBy("group1")));
        jobManager.addJob(new DummyJobWithLatches(0, new Params(1).addTags("dummyTag").groupBy("group2").persist()));
        assertThat("both jobs should start", startLatches[0].await(2, TimeUnit.SECONDS), is(true));
        final CancelResult[] cancelResults = new CancelResult[1];
        final CountDownLatch resultLatch = new CountDownLatch(1);
        startLatches[0].await(2, TimeUnit.SECONDS);
        jobManager.cancelJobsInBackground(new CancelResult.AsyncCancelCallback() {
            @Override
            public void onCancelled(CancelResult cancelResult) {
                cancelResults[0] = cancelResult;
                resultLatch.countDown();
            }
        }, TagConstraint.ANY, "dummyTag");
        // give time to cancel request to be processed
        Thread.sleep(1000);
        endLatches[0].countDown();
        endLatches[0].countDown();

        assertThat("result should come after jobs end", resultLatch.await(2, TimeUnit.SECONDS), is(true));
        assertThat("no jobs should be canceled", cancelResults[0].getCancelledJobs().size(), is(0));
        assertThat("both jobs should fail to cancel", cancelResults[0].getFailedToCancel().size(), is(2));

        jobManager.addJob(new DummyJobWithLatches(1, new Params(1).addTags("dummyTag").groupBy("group1")));
        jobManager.addJob(new DummyJobWithLatches(1, new Params(1).addTags("dummyTag").groupBy("group2").persist()));
        assertThat("new jobs with canceled groups should start", startLatches[1].await(10, TimeUnit.SECONDS), is(true));
        endLatches[1].countDown();
        endLatches[1].countDown();
    }

    public static class DummyJobWithLatches extends DummyJob {
        final int latchIndex;
        public DummyJobWithLatches(int latchIndex, Params params) {
            super(params);
            this.latchIndex =latchIndex;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            startLatches[latchIndex].countDown();
            endLatches[latchIndex].await(10, TimeUnit.SECONDS);
        }
    }
}
