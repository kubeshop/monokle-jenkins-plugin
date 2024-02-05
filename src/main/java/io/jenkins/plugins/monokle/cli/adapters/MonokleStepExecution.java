package io.jenkins.plugins.monokle.cli.adapters;

import hudson.EnvVars;
import hudson.model.TaskListener;
import io.jenkins.plugins.monokle.cli.setup.MonokleCLI;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

public class MonokleStepExecution extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;

    protected MonokleStepExecution(StepContext context) {
        super(context);
    }

    @Override
    protected Void run() throws Exception {
        var envVars = getContext().get(EnvVars.class);
        var logger = getContext().get(TaskListener.class).getLogger();

        MonokleCLI monokleCLI = new MonokleCLI(logger, envVars);
        var success = monokleCLI.setup();

        if (success) {
            getContext().onSuccess(null);

        } else {
            getContext().onFailure(new Exception("Monokle CLI setup failed"));
            return null;
        }

        return null;
    }
}
