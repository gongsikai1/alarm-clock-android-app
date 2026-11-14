package com.doubao.audioalarm;

import android.app.job.JobParameters;
import android.app.job.JobService;

import com.doubao.audioalarm.util.Keeper;

/** JobScheduler 兜底：进程被杀后由系统定期拉起 */
public class KeeperJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        try {
            if (Keeper.isEnabled(this)) {
                Keeper.start(this);
                Keeper.selfHeal(this);
            }
        } catch (Exception ignored) {
        }
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
