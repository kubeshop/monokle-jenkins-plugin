package io.jenkins.plugins.monokle.cli.adapters;

import hudson.Extension;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

public class MonokleStep extends Step {

    @DataBoundConstructor
    public MonokleStep() {
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new MonokleStepExecution(context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "setupMonokleCLI";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Monokle CLI Setup Step";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }
    }
}
