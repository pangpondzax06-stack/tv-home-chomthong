package tv.home.airweather;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The hourly refresh, through JobScheduler: no service of ours keeps running, and the system holds the job back
 * while the TV sleeps. When the TV wakes, the launcher's read of {@link NowProvider} finds the snapshot stale and
 * asks for {@link #now} instead of waiting for the next hour.
 */
public final class RefreshJob extends JobService {
    private static final int HOURLY = 1;
    private static final int ONCE = 2;
    private static final long HOUR_MS = 60 * 60_000L;
    private static final long FLEX_MS = 10 * 60_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    /** The hourly job, unless it is already scheduled. Persisted, so it comes back after a reboot. */
    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(HOURLY) != null) return;
        js.schedule(new JobInfo.Builder(HOURLY, new ComponentName(c, RefreshJob.class))
                .setPeriodic(HOUR_MS, FLEX_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build());
    }

    /** One refresh as soon as there is a network. Scheduling it again replaces a pending one. */
    static void now(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(ONCE, new ComponentName(c, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        IO.execute(() -> {
            boolean failed = false;
            try {
                Store.refresh(this);
            } catch (Exception e) {
                failed = true;
            }
            // A failed one-off retries with the system's backoff; the hourly one simply waits for its next hour.
            jobFinished(params, failed && params.getJobId() == ONCE);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
